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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ManagedScriptJobHandlerTest {

  private static final Duration BACKOFF = Duration.ofSeconds(7);
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
    final var job = job(1L, 3, scriptLink(1L));
    final var jobClient = new RecordingJobClient();

    handler(Duration.ofSeconds(5)).handle(camundaClient, "default", jobClient.mock, job);

    verify(jobClient.complete).variables(Map.<String, Object>of("sum", 5));
    final var request = provider.requests.getFirst();
    assertThat(request.contractVersion()).isEqualTo("1");
    assertThat(request.executionId()).isEqualTo("1");
    assertThat(request.variables()).containsEntry("a", 2);
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
  void reusesDeploymentAndCachedContentForTheSameArtifact() {
    final var handler = handler(Duration.ofSeconds(5));

    handler.handle(
        camundaClient, "default", new RecordingJobClient().mock, job(1L, 3, scriptLink(1L)));
    handler.handle(
        camundaClient, "default", new RecordingJobClient().mock, job(2L, 3, scriptLink(1L)));

    assertThat(provider.provisionings).hasValue(1);
    assertThat(fetches).hasValue(1);
    assertThat(provider.requests).hasSize(2);
  }

  @Test
  void concurrentJobsShareOneProvisioning() throws Exception {
    final var ready = new CompletableFuture<ProviderDeployment>();
    provider.readyWhen(ready);
    final var handler = handler(Duration.ofSeconds(10));
    final var first = new RecordingJobClient();
    final var second = new RecordingJobClient();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      executor.submit(
          () -> handler.handle(camundaClient, "default", first.mock, job(1L, 3, scriptLink(1L))));
      executor.submit(
          () -> handler.handle(camundaClient, "default", second.mock, job(2L, 3, scriptLink(1L))));
      Thread.sleep(200);
      ready.complete(null);
      executor.shutdown();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    assertThat(provider.provisionings).hasValue(1);
    verify(first.complete).execute();
    verify(second.complete).execute();
  }

  @Test
  void provisioningTimeoutFailsWithoutConsumingRetriesAndProvisioningContinues() {
    final var ready = new CompletableFuture<ProviderDeployment>();
    provider.readyWhen(ready);
    final var handler = handler(Duration.ofMillis(100));
    final var timedOut = new RecordingJobClient();

    handler.handle(camundaClient, "default", timedOut.mock, job(1L, 3, scriptLink(1L)));

    verify(timedOut.fail).retries(3);
    verify(timedOut.failStep2).retryBackoff(BACKOFF);
    assertThat(timedOut.errorMessage()).contains("still being provisioned");

    ready.complete(null);
    final var retried = new RecordingJobClient();
    handler.handle(camundaClient, "default", retried.mock, job(1L, 3, scriptLink(1L)));

    verify(retried.complete).execute();
    assertThat(provider.provisionings).hasValue(1);
  }

  @Test
  void permanentProvisioningFailureFailsWithoutRetries() {
    provider.failProvisioning(new ProvisioningException("BUILD_FAILED", "syntax error", false));
    final var jobClient = new RecordingJobClient();

    handler(Duration.ofSeconds(5))
        .handle(camundaClient, "default", jobClient.mock, job(1L, 3, scriptLink(1L)));

    verify(jobClient.fail).retries(0);
    verify(jobClient.failStep2, never()).retryBackoff(any());
    assertThat(jobClient.errorMessage()).contains("BUILD_FAILED").contains("syntax error");
  }

  @Test
  void transientProvisioningFailureConsumesOneRetryAndProvisionsAgainNextTime() {
    provider.failProvisioning(new ProvisioningException("PLATFORM_THROTTLED", "quota", true));
    final var handler = handler(Duration.ofSeconds(5));
    final var jobClient = new RecordingJobClient();

    handler.handle(camundaClient, "default", jobClient.mock, job(1L, 3, scriptLink(1L)));

    verify(jobClient.fail).retries(2);
    verify(jobClient.failStep2).retryBackoff(BACKOFF);

    provider.failProvisioning(null);
    final var retried = new RecordingJobClient();
    handler.handle(camundaClient, "default", retried.mock, job(1L, 2, scriptLink(1L)));

    verify(retried.complete).execute();
    assertThat(provider.provisionings).hasValue(2);
  }

  @Test
  void mapsRetryableAndNonRetryableExecutionFailures() {
    provider.respond(
        ExecutionResponse.failed("PLATFORM_UNAVAILABLE", "busy", true),
        ExecutionResponse.failed("SCRIPT_ERROR", "x".repeat(2_000), false));
    final var handler = handler(Duration.ofSeconds(5));
    final var retryable = new RecordingJobClient();
    final var permanent = new RecordingJobClient();

    handler.handle(camundaClient, "default", retryable.mock, job(1L, 3, scriptLink(1L)));
    handler.handle(camundaClient, "default", permanent.mock, job(2L, 3, scriptLink(1L)));

    verify(retryable.fail).retries(2);
    verify(retryable.failStep2).retryBackoff(BACKOFF);
    assertThat(retryable.errorMessage()).isEqualTo("PLATFORM_UNAVAILABLE: busy");
    verify(permanent.fail).retries(0);
    assertThat(permanent.errorMessage()).startsWith("SCRIPT_ERROR: ").hasSize(1_024);
  }

  @Test
  void reprovisionsOnceWhenTheDeploymentIsMissing() {
    provider.respond(
        ExecutionResponse.failed(ExecutionResponse.DEPLOYMENT_MISSING, "gone", false),
        ExecutionResponse.completed(Map.of("sum", 5)));
    final var jobClient = new RecordingJobClient();

    handler(Duration.ofSeconds(5))
        .handle(camundaClient, "default", jobClient.mock, job(1L, 3, scriptLink(1L)));

    assertThat(provider.provisionings).hasValue(2);
    assertThat(provider.requests).hasSize(2);
    verify(jobClient.complete).variables(Map.<String, Object>of("sum", 5));
  }

  @Test
  void reprovisioningThatOutlivesTheJobBudgetFailsWithoutConsumingRetries() {
    provider.respond(ExecutionResponse.failed(ExecutionResponse.DEPLOYMENT_MISSING, "gone", false));
    provider.readyInSequence(
        CompletableFuture.completedFuture(null), new CompletableFuture<ProviderDeployment>());
    final var jobClient = new RecordingJobClient();

    handler(Duration.ofMillis(200))
        .handle(camundaClient, "default", jobClient.mock, job(1L, 3, scriptLink(1L)));

    assertThat(provider.provisionings).hasValue(2);
    verify(jobClient.fail).retries(3);
    verify(jobClient.failStep2).retryBackoff(BACKOFF);
    assertThat(jobClient.errorMessage()).contains("still being provisioned");
  }

  @Test
  void failsWithOneRetryLessWhenTheDeploymentIsMissingAgain() {
    final var missing =
        ExecutionResponse.failed(ExecutionResponse.DEPLOYMENT_MISSING, "gone", false);
    provider.respond(missing, missing);
    final var jobClient = new RecordingJobClient();

    handler(Duration.ofSeconds(5))
        .handle(camundaClient, "default", jobClient.mock, job(1L, 3, scriptLink(1L)));

    assertThat(provider.provisionings).hasValue(2);
    verify(jobClient.fail).retries(2);
    assertThat(jobClient.errorMessage()).startsWith("DEPLOYMENT_MISSING");
    assertThat(registry.find(provider.provisioned.getFirst().key())).isEmpty();
  }

  @Test
  void rejectsMissingOrInvalidLinkedResourcesWithoutRetries() {
    final var handler = handler(Duration.ofSeconds(5));
    final var missing = new RecordingJobClient();
    final var invalid = new RecordingJobClient();
    final var noScript = new RecordingJobClient();

    handler.handle(camundaClient, "default", missing.mock, job(1L, 3, null));
    handler.handle(camundaClient, "default", invalid.mock, job(2L, 3, "not json"));
    handler.handle(
        camundaClient,
        "default",
        noScript.mock,
        job(3L, 3, "[{\"resourceKey\":\"1\",\"linkName\":\"other\"}]"));

    verify(missing.fail).retries(0);
    assertThat(missing.errorMessage()).contains("Missing 'linkedResources' header");
    verify(invalid.fail).retries(0);
    assertThat(invalid.errorMessage()).contains("not a valid JSON array");
    verify(noScript.fail).retries(0);
    assertThat(noScript.errorMessage()).contains("no 'script' link");
    assertThat(provider.provisionings).hasValue(0);
  }

  @Test
  void rejectsLanguageThatDoesNotMatchTheScriptResource() {
    final var jobClient = new RecordingJobClient();
    final var job = job(1L, 3, scriptLink(1L));
    when(job.getCustomHeaders())
        .thenReturn(
            Map.of("linkedResources", scriptLink(1L), "language", "python", "runtime", "python3"));

    handler(Duration.ofSeconds(5)).handle(camundaClient, "default", jobClient.mock, job);

    verify(jobClient.fail).retries(0);
    assertThat(jobClient.errorMessage()).contains("must use the .py extension");
  }

  @Test
  void passesTheDependencyManifestToTheProvider() {
    final var jobClient = new RecordingJobClient();
    final var links =
        "[{\"resourceKey\":\"1\",\"resourceType\":\"ManagedScript\",\"linkName\":\"script\"},"
            + "{\"resourceKey\":\"2\",\"resourceType\":\"ManagedScript\",\"linkName\":\"dependencies\"}]";

    handler(Duration.ofSeconds(5))
        .handle(camundaClient, "default", jobClient.mock, job(1L, 3, links));

    assertThat(provider.provisioned.getFirst().dependencies())
        .hasValueSatisfying(
            manifest -> assertThat(new String(manifest, StandardCharsets.UTF_8)).isEqualTo("{}"));
    verify(jobClient.complete).variables(Map.<String, Object>of());
  }

  @Test
  void resourceFetchFailureConsumesOneRetry() {
    resources.clear();
    final var jobClient = new RecordingJobClient();

    handler(Duration.ofSeconds(5))
        .handle(camundaClient, "default", jobClient.mock, job(1L, 3, scriptLink(1L)));

    verify(jobClient.fail, timeout(1_000)).retries(2);
    verify(jobClient.failStep2).retryBackoff(BACKOFF);
  }

  @Test
  void waitsForResourceToBecomeVisibleWithoutConsumingARetry() {
    provider.respond(ExecutionResponse.completed(Map.of("sum", 5)));
    final var script = resources.remove(1L);
    final var jobClient = new RecordingJobClient();
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

    new ManagedScriptJobHandler(
            provider, registry, scriptResources, objectMapper, Duration.ofSeconds(5), BACKOFF)
        .handle(camundaClient, "default", jobClient.mock, job(1L, 3, scriptLink(1L)));

    assertThat(fetches).hasValue(3);
    verify(jobClient.complete).variables(Map.<String, Object>of("sum", 5));
    verify(jobClient.fail, never()).retries(anyInt());
  }

  private ManagedScriptJobHandler handler(Duration provisioningTimeout) {
    return new ManagedScriptJobHandler(
        provider, registry, scriptResources(), objectMapper, provisioningTimeout, BACKOFF);
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

  private static ActivatedJob job(long key, int retries, String linkedResources) {
    final var job = mock(ActivatedJob.class);
    final Map<String, String> headers = new HashMap<>();
    if (linkedResources != null) {
      headers.put("linkedResources", linkedResources);
    }
    headers.put("language", "javascript");
    headers.put("runtime", "nodejs22");
    when(job.getKey()).thenReturn(key);
    when(job.getRetries()).thenReturn(retries);
    when(job.getCustomHeaders()).thenReturn(headers);
    when(job.getTenantId()).thenReturn("tenant-a");
    when(job.getBpmnProcessId()).thenReturn("process");
    when(job.getVariablesAsMap()).thenReturn(Map.of("a", 2, "b", 3));
    return job;
  }

  private static final class RecordingJobClient {
    final JobClient mock = mock(JobClient.class);
    final FailJobCommandStep1 fail = mock(FailJobCommandStep1.class);
    final FailJobCommandStep2 failStep2 = mock(FailJobCommandStep2.class, RETURNS_SELF);
    final CompleteJobCommandStep1 complete = mock(CompleteJobCommandStep1.class, RETURNS_SELF);

    RecordingJobClient() {
      when(mock.newFailCommand(any(ActivatedJob.class))).thenReturn(fail);
      when(fail.retries(anyInt())).thenReturn(failStep2);
      when(mock.newCompleteCommand(any(ActivatedJob.class))).thenReturn(complete);
    }

    String errorMessage() {
      final var captor = ArgumentCaptor.forClass(String.class);
      verify(failStep2).errorMessage(captor.capture());
      return captor.getValue();
    }
  }
}
