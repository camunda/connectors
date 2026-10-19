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
import java.time.Clock;
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
  private final DeploymentRegistry registry =
      new DeploymentRegistry(provider, Executors.newFixedThreadPool(2), Clock.systemUTC());

  {
    resources.put(1L, resource("sum.js", SCRIPT));
    resources.put(2L, resource("package.json", "{}"));
  }

  @AfterEach
  void closeRegistry() {
    registry.close();
  }

  @Test
  void completesWithScriptVariables() {
    provider.respond(ExecutionResponse.completed(Map.of("sum", 5)));

    final var outcome = handle(handler(Duration.ofSeconds(5)), job(1L, 3, scriptLink(1L)));

    assertThat(outcome).isEqualTo(new Outcome.Completed(Map.of("sum", 5)));
    final var request = provider.requests.getFirst();
    assertThat(request.contractVersion()).isEqualTo("1");
    assertThat(request.executionId()).isEqualTo("1");
    assertThat(request.variables()).isEqualTo(INPUT);
    assertThat(request.context())
        .containsEntry("tenantId", "tenant-a")
        .containsEntry("jobKey", "1")
        .containsEntry("processDefinitionId", "process");
    assertThat(registry.find(provider.provisioned.getFirst().key()))
        .hasValueSatisfying(
            entry -> {
              assertThat(entry.status()).isEqualTo(DeploymentRegistry.Status.READY);
              assertThat(entry.lastUsedAt()).isNotNull();
            });
  }

  @Test
  void derivesLanguageFromTheFileExtensionWithoutHeaders() {
    final var job = job(1L, 3, scriptLink(1L));
    when(job.getCustomHeaders()).thenReturn(Map.of("linkedResources", scriptLink(1L)));

    final var outcome = handle(handler(Duration.ofSeconds(5)), job);

    assertThat(outcome).isInstanceOf(Outcome.Completed.class);
    assertThat(provider.provisioned.getFirst().language()).isEqualTo(ScriptLanguage.JAVASCRIPT);
    assertThat(provider.provisioned.getFirst().runtime()).isEqualTo("nodejs");
  }

  @Test
  void prefixesTheDeploymentName() {
    final var handler =
        new ManagedScriptJobHandler(
            provider,
            registry,
            scriptResources(),
            objectMapper,
            Duration.ofSeconds(5),
            BACKOFF,
            "camunda-ms-c1-");

    handle(handler, job(1L, 3, scriptLink(1L)));

    assertThat(provider.provisioned.getFirst().deploymentName()).startsWith("camunda-ms-c1-");
  }

  @Test
  void reusesDeploymentAndCachedContentForTheSameArtifact() {
    final var handler = handler(Duration.ofSeconds(5));

    handle(handler, job(1L, 3, scriptLink(1L)));
    handle(handler, job(2L, 3, scriptLink(1L)));

    assertThat(provider.provisionings).hasValue(1);
    assertThat(fetches).hasValue(1);
    assertThat(provider.requests).hasSize(2);
  }

  @Test
  void concurrentJobsShareOneProvisioning() throws Exception {
    final var ready = new CompletableFuture<ProviderDeployment>();
    provider.readyWhen(ready);
    final var handler = handler(Duration.ofSeconds(10));
    final Future<Outcome> first;
    final Future<Outcome> second;

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      first = executor.submit(() -> handle(handler, job(1L, 3, scriptLink(1L))));
      second = executor.submit(() -> handle(handler, job(2L, 3, scriptLink(1L))));
      Thread.sleep(200);
      ready.complete(null);
      executor.shutdown();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    assertThat(provider.provisionings).hasValue(1);
    assertThat(first.get()).isInstanceOf(Outcome.Completed.class);
    assertThat(second.get()).isInstanceOf(Outcome.Completed.class);
  }

  @Test
  void provisioningTimeoutFailsWithoutConsumingRetriesAndProvisioningContinues() {
    final var ready = new CompletableFuture<ProviderDeployment>();
    provider.readyWhen(ready);
    final var handler = handler(Duration.ofMillis(100));

    final var timedOut = failed(handle(handler, job(1L, 3, scriptLink(1L))));

    assertThat(timedOut.retries()).isEqualTo(3);
    assertThat(timedOut.backoff()).isEqualTo(BACKOFF);
    assertThat(timedOut.message()).contains("still being provisioned");

    ready.complete(null);
    assertThat(handle(handler, job(1L, 3, scriptLink(1L)))).isInstanceOf(Outcome.Completed.class);
    assertThat(provider.provisionings).hasValue(1);
  }

  @Test
  void permanentProvisioningFailureFailsWithoutRetries() {
    provider.failProvisioning(new ProvisioningException("BUILD_FAILED", "syntax error", false));

    final var failure = failed(handle(handler(Duration.ofSeconds(5)), job(1L, 3, scriptLink(1L))));

    assertThat(failure.retries()).isZero();
    assertThat(failure.backoff()).isZero();
    assertThat(failure.code()).isEqualTo("BUILD_FAILED");
    assertThat(failure.message()).contains("BUILD_FAILED").contains("syntax error");
  }

  @Test
  void transientProvisioningFailureConsumesOneRetryAndProvisionsAgainNextTime() {
    provider.failProvisioning(new ProvisioningException("PLATFORM_THROTTLED", "quota", true));
    final var handler = handler(Duration.ofSeconds(5));

    final var failure = failed(handle(handler, job(1L, 3, scriptLink(1L))));

    // null lets the runtime consume one retry
    assertThat(failure.retries()).isNull();
    assertThat(failure.backoff()).isEqualTo(BACKOFF);

    provider.failProvisioning(null);
    assertThat(handle(handler, job(1L, 2, scriptLink(1L)))).isInstanceOf(Outcome.Completed.class);
    assertThat(provider.provisionings).hasValue(2);
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
  void reprovisionsOnceWhenTheDeploymentIsMissing() {
    provider.respond(
        ExecutionResponse.failed(ExecutionResponse.DEPLOYMENT_MISSING, "gone", false),
        ExecutionResponse.completed(Map.of("sum", 5)));

    final var outcome = handle(handler(Duration.ofSeconds(5)), job(1L, 3, scriptLink(1L)));

    assertThat(provider.provisionings).hasValue(2);
    assertThat(provider.requests).hasSize(2);
    assertThat(outcome).isEqualTo(new Outcome.Completed(Map.of("sum", 5)));
  }

  @Test
  void reprovisioningThatOutlivesTheJobBudgetFailsWithoutConsumingRetries() {
    provider.respond(ExecutionResponse.failed(ExecutionResponse.DEPLOYMENT_MISSING, "gone", false));
    provider.readyInSequence(
        CompletableFuture.completedFuture(null), new CompletableFuture<ProviderDeployment>());

    final var failure = failed(handle(handler(Duration.ofMillis(200)), job(1L, 3, scriptLink(1L))));

    assertThat(provider.provisionings).hasValue(2);
    assertThat(failure.retries()).isEqualTo(3);
    assertThat(failure.backoff()).isEqualTo(BACKOFF);
    assertThat(failure.message()).contains("still being provisioned");
  }

  @Test
  void failsWithOneRetryLessWhenTheDeploymentIsMissingAgain() {
    final var missing =
        ExecutionResponse.failed(ExecutionResponse.DEPLOYMENT_MISSING, "gone", false);
    provider.respond(missing, missing);

    final var failure = failed(handle(handler(Duration.ofSeconds(5)), job(1L, 3, scriptLink(1L))));

    assertThat(provider.provisionings).hasValue(2);
    assertThat(failure.retries()).isNull();
    assertThat(failure.message()).startsWith("DEPLOYMENT_MISSING");
    assertThat(registry.find(provider.provisioned.getFirst().key())).isEmpty();
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
    assertThat(provider.provisionings).hasValue(0);
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
  void passesTheDependencyManifestToTheProvider() {
    final var links =
        "[{\"resourceKey\":\"1\",\"resourceType\":\"ManagedScript\",\"linkName\":\"script\"},"
            + "{\"resourceKey\":\"2\",\"resourceType\":\"ManagedScript\",\"linkName\":\"dependencies\"}]";

    final var outcome = handle(handler(Duration.ofSeconds(5)), job(1L, 3, links));

    assertThat(provider.provisioned.getFirst().dependencies())
        .hasValueSatisfying(
            manifest -> assertThat(new String(manifest, StandardCharsets.UTF_8)).isEqualTo("{}"));
    assertThat(outcome).isEqualTo(new Outcome.Completed(Map.of()));
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
                registry,
                scriptResources,
                objectMapper,
                Duration.ofSeconds(5),
                BACKOFF,
                ArtifactSpec.DEFAULT_DEPLOYMENT_NAME_PREFIX),
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
        registry,
        scriptResources(),
        objectMapper,
        provisioningTimeout,
        BACKOFF,
        ArtifactSpec.DEFAULT_DEPLOYMENT_NAME_PREFIX);
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
