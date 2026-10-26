/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.managedcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.runtime.managedcode.ManagedScriptJobHandler.Outcome;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ManagedScriptJobHandlerTest {

  private static final Duration BACKOFF = Duration.ofSeconds(7);
  private static final Duration EXECUTION_TIMEOUT = Duration.ofSeconds(30);
  private static final Map<String, Object> INPUT = Map.of("a", 2, "b", 3);
  private static final String SCRIPT =
      """
      export async function execute(variables) {
        return { sum: variables.a + variables.b };
      }
      """;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final CamundaClient camundaClient = mock(CamundaClient.class);
  private final Map<Long, ScriptResources.ScriptResource> resources = new HashMap<>();
  private final AtomicInteger fetches = new AtomicInteger();
  private final ControlledProvider provider = new ControlledProvider();
  private final ExecutorReadiness executors =
      new ExecutorReadiness(provider, Duration.ofMillis(10));

  {
    resources.put(1L, resource("sum.js", SCRIPT));
    resources.put(2L, resource("package.json", "{}"));
  }

  @AfterEach
  void closeExecutors() {
    executors.close();
  }

  @Test
  void sendsTheScriptWithItsInputAndCompletesWithItsVariables() {
    provider.respond(ExecutionResponse.completed(Map.of("sum", 5)));

    final var outcome = handle(handler(Duration.ofSeconds(5)), job(1L, 3, scriptLink(1L)));

    assertThat(outcome).isEqualTo(new Outcome.Completed(Map.of("sum", 5)));
    final var request = provider.requests.getFirst();
    assertThat(request.contractVersion()).isEqualTo("1");
    assertThat(request.executionId()).isEqualTo("1");
    assertThat(request.script()).isEqualTo(SCRIPT);
    assertThat(request.timeoutMs()).isEqualTo(EXECUTION_TIMEOUT.toMillis());
    assertThat(request.variables()).isEqualTo(INPUT);
    assertThat(request.context())
        .containsEntry("tenantId", "tenant-a")
        .containsEntry("jobKey", "1")
        .containsEntry("processDefinitionId", "process");
    assertThat(request.payload())
        .containsEntry("script", SCRIPT)
        .containsEntry("timeoutMs", EXECUTION_TIMEOUT.toMillis());
  }

  @Test
  void derivesLanguageFromTheFileExtensionWithoutHeaders() {
    final var job = job(1L, 3, scriptLink(1L));
    when(job.getCustomHeaders()).thenReturn(Map.of("linkedResources", scriptLink(1L)));

    final var outcome = handle(handler(Duration.ofSeconds(5)), job);

    assertThat(outcome).isInstanceOf(Outcome.Completed.class);
    assertThat(provider.languages).containsExactly(ScriptLanguage.JAVASCRIPT);
  }

  @Test
  void createsTheExecutorsOnceForEveryScriptAndJob() {
    resources.put(3L, resource("other.js", "export function execute() { return {}; }"));
    final var handler = handler(Duration.ofSeconds(5));

    handle(handler, job(1L, 3, scriptLink(1L)));
    handle(handler, job(2L, 3, scriptLink(1L)));
    handle(handler, job(3L, 3, scriptLink(3L)));

    assertThat(provider.ensures).hasValue(1);
    assertThat(provider.requests).hasSize(3);
    assertThat(fetches).hasValue(2);
  }

  @Test
  void concurrentJobsWaitForTheSameExecutors() throws Exception {
    final var ready = new CompletableFuture<Void>();
    provider.readyWhen(ready);
    final var handler = handler(Duration.ofSeconds(10));
    final Future<Outcome> first;
    final Future<Outcome> second;
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      first = pool.submit(() -> handle(handler, job(1L, 3, scriptLink(1L))));
      second = pool.submit(() -> handle(handler, job(2L, 3, scriptLink(1L))));
      Thread.sleep(200);
      assertThat(provider.requests).isEmpty();
      ready.complete(null);
      pool.shutdown();
      assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    assertThat(provider.ensures).hasValue(1);
    assertThat(first.get()).isInstanceOf(Outcome.Completed.class);
    assertThat(second.get()).isInstanceOf(Outcome.Completed.class);
  }

  @Test
  void executorsNotReadyInTimeFailWithoutConsumingRetriesAndCreationContinues() {
    final var ready = new CompletableFuture<Void>();
    provider.readyWhen(ready);
    final var handler = handler(Duration.ofMillis(100));

    final var timedOut = failed(handle(handler, job(1L, 3, scriptLink(1L))));

    assertThat(timedOut.code()).isEqualTo("PROVISIONING_PENDING");
    assertThat(timedOut.retries()).isEqualTo(3);
    assertThat(timedOut.backoff()).isEqualTo(BACKOFF);
    assertThat(timedOut.message()).contains("still being created");

    ready.complete(null);
    assertThat(handle(handler(Duration.ofSeconds(5)), job(1L, 3, scriptLink(1L))))
        .isInstanceOf(Outcome.Completed.class);
    assertThat(provider.ensures).hasValue(1);
  }

  @Test
  void permanentExecutorFailureFailsWithoutRetriesAndAJobAfterTheBackoffTriesAgain()
      throws InterruptedException {
    provider.failEnsure(new ProvisioningException("PROVIDER_REJECTED", "denied", false));
    final var handler = handler(Duration.ofSeconds(5));

    final var failure = failed(handle(handler, job(1L, 3, scriptLink(1L))));

    assertThat(failure.retries()).isZero();
    assertThat(failure.backoff()).isZero();
    assertThat(failure.code()).isEqualTo("PROVIDER_REJECTED");
    assertThat(failure.message()).contains("PROVIDER_REJECTED").contains("denied");

    provider.failEnsure(null);
    Thread.sleep(50);
    assertThat(handle(handler, job(2L, 3, scriptLink(1L)))).isInstanceOf(Outcome.Completed.class);
    assertThat(provider.ensures).hasValue(2);
  }

  @Test
  void transientExecutorFailureIsRetriedInTheBackground() throws InterruptedException {
    provider.failEnsure(new ProvisioningException("PLATFORM_THROTTLED", "quota", true));
    executors.start();
    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (provider.ensures.get() < 2 && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    assertThat(provider.ensures.get()).isGreaterThanOrEqualTo(2);

    provider.failEnsure(null);

    assertThat(handle(handler(Duration.ofSeconds(5)), job(1L, 3, scriptLink(1L))))
        .isInstanceOf(Outcome.Completed.class);
  }

  @Test
  void aRetryableFailureThatLastsConsumesRetriesSoThatTheJobEndsInAnIncident()
      throws InterruptedException {
    provider.failEnsure(new ProvisioningException("PERMISSION_DENIED", "denied", true));
    final var handler = handler(Duration.ofMillis(50));

    final var early = failed(handle(handler, job(1L, 3, scriptLink(1L))));
    Thread.sleep(150);
    final var lasting = failed(handle(handler, job(2L, 3, scriptLink(1L))));

    assertThat(early.code()).isEqualTo("PROVISIONING_PENDING");
    assertThat(early.retries()).isEqualTo(3);
    assertThat(lasting.code()).isEqualTo("PERMISSION_DENIED");
    assertThat(lasting.retries()).isNull();
    assertThat(lasting.backoff()).isEqualTo(BACKOFF);
  }

  @Test
  void mapsRetryableAndNonRetryableExecutionFailures() {
    provider.respond(
        ExecutionResponse.failed("PLATFORM_UNAVAILABLE", "busy", true),
        ExecutionResponse.failed("SCRIPT_ERROR", "x".repeat(2_000), false));
    final var handler = handler(Duration.ofSeconds(5));

    final var retryable = failed(handle(handler, job(1L, 3, scriptLink(1L))));
    final var permanent = failed(handle(handler, job(2L, 3, scriptLink(1L))));

    assertThat(retryable.retries()).isNull();
    assertThat(retryable.backoff()).isEqualTo(BACKOFF);
    assertThat(retryable.message()).isEqualTo("PLATFORM_UNAVAILABLE: busy");
    assertThat(permanent.retries()).isZero();
    assertThat(permanent.code()).isEqualTo("SCRIPT_ERROR");
    assertThat(permanent.message()).startsWith("SCRIPT_ERROR: ").hasSize(1_024);
  }

  @Test
  void createsTheExecutorsAgainOnceWhenOneIsMissing() {
    provider.respond(
        ExecutionResponse.failed(ExecutionResponse.DEPLOYMENT_MISSING, "gone", false),
        ExecutionResponse.completed(Map.of("sum", 5)));

    final var outcome = handle(handler(Duration.ofSeconds(5)), job(1L, 3, scriptLink(1L)));

    assertThat(provider.ensures).hasValue(2);
    assertThat(provider.requests).hasSize(2);
    assertThat(outcome).isEqualTo(new Outcome.Completed(Map.of("sum", 5)));
  }

  @Test
  void failsWithOneRetryLessWhenTheExecutorIsMissingAgain() {
    final var missing =
        ExecutionResponse.failed(ExecutionResponse.DEPLOYMENT_MISSING, "gone", false);
    provider.respond(missing, missing);

    final var failure = failed(handle(handler(Duration.ofSeconds(5)), job(1L, 3, scriptLink(1L))));

    assertThat(provider.ensures).hasValue(2);
    assertThat(failure.retries()).isNull();
    assertThat(failure.message()).startsWith("DEPLOYMENT_MISSING");
  }

  @Test
  void rejectsMissingOrInvalidLinkedResourcesWithoutRetries() {
    final var handler = handler(Duration.ofSeconds(5));

    final var missing = failed(handle(handler, job(1L, 3, null)));
    final var invalid = failed(handle(handler, job(2L, 3, "not json")));
    final var noScript =
        failed(handle(handler, job(3L, 3, "[{\"resourceKey\":\"1\",\"linkName\":\"other\"}]")));

    assertThat(missing.retries()).isZero();
    assertThat(missing.message()).contains("Missing 'linkedResources' header");
    assertThat(invalid.retries()).isZero();
    assertThat(invalid.message()).contains("not a valid JSON array");
    assertThat(noScript.retries()).isZero();
    assertThat(noScript.message()).contains("no 'script' link");
    assertThat(provider.requests).isEmpty();
  }

  @Test
  void rejectsLanguageThatDoesNotMatchTheScriptResource() {
    final var job = job(1L, 3, scriptLink(1L));
    when(job.getCustomHeaders())
        .thenReturn(
            Map.of("linkedResources", scriptLink(1L), "language", "python", "runtime", "python3"));

    final var failure = failed(handle(handler(Duration.ofSeconds(5)), job));

    assertThat(failure.retries()).isZero();
    assertThat(failure.message()).contains("must use the .py extension");
  }

  @Test
  void rejectsARuntimeOtherThanTheExecutorsOne() {
    final var job = job(1L, 3, scriptLink(1L));
    when(job.getCustomHeaders())
        .thenReturn(Map.of("linkedResources", scriptLink(1L), "runtime", "nodejs20"));

    final var failure = failed(handle(handler(Duration.ofSeconds(5)), job));

    assertThat(failure.retries()).isZero();
    assertThat(failure.message()).contains("cannot be chosen per script");
    assertThat(provider.requests).isEmpty();
  }

  @Test
  void rejectsLinkedDependenciesWithoutRetries() {
    final var links =
        "[{\"resourceKey\":\"1\",\"resourceType\":\"ManagedScript\",\"linkName\":\"script\"},"
            + "{\"resourceKey\":\"2\",\"resourceType\":\"ManagedScript\",\"linkName\":\"dependencies\"}]";

    final var failure = failed(handle(handler(Duration.ofSeconds(5)), job(1L, 3, links)));

    assertThat(failure.retries()).isZero();
    assertThat(failure.message()).contains("UNSUPPORTED_DEPENDENCIES");
    assertThat(provider.requests).isEmpty();
  }

  @Test
  void resourceFetchFailureConsumesOneRetry() {
    resources.clear();

    final var failure = failed(handle(handler(Duration.ofSeconds(5)), job(1L, 3, scriptLink(1L))));

    assertThat(failure.retries()).isNull();
    assertThat(failure.backoff()).isEqualTo(BACKOFF);
  }

  @Test
  void waitsForResourceToBecomeVisibleWithoutConsumingARetry() {
    provider.respond(ExecutionResponse.completed(Map.of("sum", 5)));
    final var script = resources.remove(1L);
    final var scriptResources =
        new ScriptResources(
            16,
            (client, key) -> {
              // Not visible on the first two reads, as when the exporter lags behind the deployment
              if (fetches.incrementAndGet() <= 2) {
                throw new ScriptResources.ResourceNotVisibleException(key, null);
              }
              return script;
            },
            Duration.ofSeconds(1),
            Duration.ofMillis(10));

    final var outcome =
        handle(
            new ManagedScriptJobHandler(
                provider,
                executors,
                scriptResources,
                objectMapper,
                Duration.ofSeconds(5),
                EXECUTION_TIMEOUT,
                BACKOFF),
            job(1L, 3, scriptLink(1L)));

    assertThat(fetches).hasValue(3);
    assertThat(outcome).isEqualTo(new Outcome.Completed(Map.of("sum", 5)));
  }

  private Outcome handle(ManagedScriptJobHandler handler, JobContext job) {
    return handler.handle(camundaClient, "default", job, INPUT);
  }

  private static Outcome.Failed failed(Outcome outcome) {
    assertThat(outcome).isInstanceOf(Outcome.Failed.class);
    return (Outcome.Failed) outcome;
  }

  private ManagedScriptJobHandler handler(Duration provisioningTimeout) {
    return new ManagedScriptJobHandler(
        provider,
        executors,
        scriptResources(),
        objectMapper,
        provisioningTimeout,
        EXECUTION_TIMEOUT,
        BACKOFF);
  }

  private ScriptResources scriptResources() {
    return new ScriptResources(
        16,
        (client, key) -> {
          fetches.incrementAndGet();
          final var resource = resources.get(key);
          if (resource == null) {
            throw new ScriptResources.ResourceNotVisibleException(key, null);
          }
          return resource;
        },
        Duration.ofMillis(200),
        Duration.ofMillis(10));
  }

  private static ScriptResources.ScriptResource resource(String name, String content) {
    return new ScriptResources.ScriptResource(name, content.getBytes(StandardCharsets.UTF_8));
  }

  private static String scriptLink(long resourceKey) {
    return "[{\"resourceKey\":\"%d\",\"resourceType\":\"ManagedScript\",\"linkName\":\"script\"}]"
        .formatted(resourceKey);
  }

  private static JobContext job(long key, int retries, String linkedResources) {
    final var job = mock(JobContext.class);
    final Map<String, String> headers = new HashMap<>();
    if (linkedResources != null) {
      headers.put("linkedResources", linkedResources);
    }
    headers.put("language", "javascript");
    headers.put("runtime", "nodejs22");
    when(job.getJobKey()).thenReturn(key);
    when(job.getRetries()).thenReturn(retries);
    when(job.getCustomHeaders()).thenReturn(headers);
    when(job.getTenantId()).thenReturn("tenant-a");
    when(job.getBpmnProcessId()).thenReturn("process");
    return job;
  }
}
