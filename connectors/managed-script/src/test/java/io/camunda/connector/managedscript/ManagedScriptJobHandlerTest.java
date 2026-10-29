/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import io.camunda.client.CamundaClient;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.managedscript.ManagedScriptJobHandler.Outcome;
import io.camunda.connector.managedscript.ScriptResources.ResourceNotVisibleException;
import io.camunda.connector.managedscript.ScriptResources.ScriptResource;
import io.camunda.connector.managedscript.api.ErrorCode;
import io.camunda.connector.managedscript.api.ExecutionRequest;
import io.camunda.connector.managedscript.api.ExecutionResponse;
import io.camunda.connector.managedscript.api.ProvisioningException;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ManagedScriptJobHandlerTest {

  private static final long SCRIPT_KEY = 2251799813685300L;
  private static final Duration BACKOFF = Duration.ofSeconds(10);
  private static final String SCRIPT_HEADER =
      "[{\"resourceKey\":\"%d\",\"resourceType\":\"ManagedScript\",\"linkName\":\"script\"}]"
          .formatted(SCRIPT_KEY);

  private final CamundaClient client = mock(CamundaClient.class);
  private final FakeProvider provider = new FakeProvider();
  private final Map<Long, ScriptResource> stored = new HashMap<>();
  private final AtomicInteger fetches = new AtomicInteger();
  private final ManagedScriptJobHandler handler = handler(Duration.ofMillis(200));

  @Test
  void sendsTheScriptWithItsInputAndContextAndCompletesWithItsVariables() {
    store("sum.py", "def execute(variables, context):\n    return {}\n");
    provider.respond(ExecutionResponse.completed(Map.of("sum", 5)));

    final var outcome = handler.handle(client, "default", job(SCRIPT_HEADER), Map.of("a", 2));

    assertThat(outcome).isEqualTo(new Outcome.Completed(Map.of("sum", 5)));
    final var call = provider.calls.getFirst();
    assertThat(call.language()).isEqualTo(ScriptLanguage.PYTHON);
    assertThat(call.request().script()).startsWith("def execute");
    assertThat(call.request().variables()).isEqualTo(Map.of("a", 2));
    assertThat(call.request().executionId()).isEqualTo("42");
    assertThat(call.request().timeoutMs()).isEqualTo(30_000);
    assertThat(call.request().context())
        .containsEntry("tenantId", "<default>")
        .containsEntry("processDefinitionId", "order-process")
        .containsEntry("processDefinitionVersion", 3)
        .containsEntry("processInstanceKey", "100")
        .containsEntry("elementId", "compute-sum")
        .containsEntry("elementInstanceKey", "101")
        .containsEntry("jobKey", "42");
  }

  @Test
  void sendsAnEmptyInputWhenTheScriptInputIsAbsent() {
    store("sum.py", "x");

    handler.handle(client, "default", job(SCRIPT_HEADER), null);

    assertThat(provider.calls.getFirst().request().variables()).isEmpty();
  }

  @Test
  void takesTheLanguageFromTheFileExtensionOnly() {
    store("sum.js", "export function execute() { return {}; }");
    final var headers = new HashMap<>(Map.of("linkedResources", SCRIPT_HEADER));
    headers.put("language", "python");
    headers.put("runtime", "python3.13");

    final var outcome = handler.handle(client, "default", job(headers), Map.of());

    assertThat(outcome).isInstanceOf(Outcome.Completed.class);
    assertThat(provider.calls.getFirst().language()).isEqualTo(ScriptLanguage.JAVASCRIPT);
  }

  @Test
  void rejectsAnUnsupportedExtensionWithoutRetries() {
    store("sum.rb", "x");

    final var outcome = handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());

    assertFailed(outcome, ErrorCode.INVALID_JOB, 0);
    assertThat(provider.calls).isEmpty();
  }

  @Test
  void rejectsMissingOrInvalidLinkedResourcesWithoutRetries() {
    // Stored, so that a header read too leniently would reach the provider.
    store("sum.py", "x");
    for (var header :
        new String[] {
          "",
          "not json",
          "[]",
          "[{\"resourceKey\":\"abc\",\"linkName\":\"script\"}]",
          "[{\"resourceKey\":\"0\",\"linkName\":\"script\"}]",
          "[{\"resourceKey\":\"-1\",\"linkName\":\"script\"}]",
          "[{\"resourceKey\":\"1\",\"linkName\":\"script\"},{\"resourceKey\":\"2\",\"linkName\":\"script\"}]",
          "{\"resourceKey\":\"%d\",\"linkName\":\"script\"}".formatted(SCRIPT_KEY),
          SCRIPT_HEADER + " []"
        }) {
      final var outcome =
          handler.handle(client, "default", job(Map.of("linkedResources", header)), Map.of());

      assertFailed(outcome, ErrorCode.INVALID_JOB, 0);
    }
    assertFailed(
        handler.handle(client, "default", job(Map.of()), Map.of()), ErrorCode.INVALID_JOB, 0);
    assertThat(provider.calls).isEmpty();
  }

  @Test
  void rejectsLinkedDependenciesWithoutRetries() {
    store("sum.py", "x");
    final var header =
        "[{\"resourceKey\":\"%d\",\"linkName\":\"script\"},{\"resourceKey\":\"7\",\"linkName\":\"dependencies\"}]"
            .formatted(SCRIPT_KEY);

    final var outcome =
        handler.handle(client, "default", job(Map.of("linkedResources", header)), Map.of());

    assertFailed(outcome, ErrorCode.UNSUPPORTED_DEPENDENCIES, 0);
  }

  @Test
  void consumesARetryWhenTheScriptIsStillNotVisibleAfterPolling() {
    final var outcome = handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());

    final var failed = assertFailed(outcome, ErrorCode.PLATFORM_UNAVAILABLE, null);
    assertThat(failed.backoff()).isEqualTo(BACKOFF);
    assertThat(fetches.get()).isGreaterThan(1);
  }

  @Test
  void waitsForTheScriptToBecomeVisible() {
    final var handler = handler(Duration.ofSeconds(5));
    final var script = new ScriptResource("sum.py", "x".getBytes(StandardCharsets.UTF_8));
    new Thread(
            () -> {
              sleep(100);
              synchronized (stored) {
                stored.put(SCRIPT_KEY, script);
              }
            })
        .start();

    final var outcome = handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());

    assertThat(outcome).isInstanceOf(Outcome.Completed.class);
  }

  @Test
  void fetchesAScriptOncePerPhysicalTenantAndKey() {
    store("sum.py", "x");

    handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());
    handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());
    handler.handle(client, "other", job(SCRIPT_HEADER), Map.of());

    assertThat(fetches.get()).isEqualTo(2);
  }

  @Test
  void makesTheExecutorsReadyOnceForEveryJob() {
    store("sum.py", "x");

    handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());
    handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());

    assertThat(provider.ensureCalls).isEqualTo(1);
    assertThat(provider.calls).hasSize(2);
  }

  @Test
  void takesTheRetriesFromTheCodeNotFromTheRetryableFlag() {
    store("sum.py", "x");
    provider.respond(
        new ExecutionResponse(
            "1",
            "FAILED",
            null,
            new ExecutionResponse.Error("SCRIPT_ERROR", "boom", null, true, false)),
        new ExecutionResponse(
            "1",
            "FAILED",
            null,
            new ExecutionResponse.Error("PLATFORM_THROTTLED", "slow down", null, false, false)));

    final var script = handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());
    final var throttled = handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());

    assertFailed(script, ErrorCode.SCRIPT_ERROR, 0);
    assertFailed(throttled, ErrorCode.PLATFORM_THROTTLED, null);
  }

  @Test
  void treatsACodeOutsideTheClosedSetAsAnInvalidResult() {
    store("sum.py", "x");
    provider.respond(scriptFailure("BUILD_FAILED", "old code", null));

    final var outcome = handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());

    final var failed = assertFailed(outcome, ErrorCode.INVALID_RESULT, 0);
    assertThat(failed.message()).contains("BUILD_FAILED");
  }

  @Test
  void treatsAMissingOrUnknownOutcomeAsAnInvalidResult() {
    store("sum.py", "x");
    provider.respond(
        new ExecutionResponse("1", null, null, null),
        new ExecutionResponse("1", "RUNNING", null, null),
        new ExecutionResponse("1", "FAILED", null, null));

    for (int i = 0; i < 3; i++) {
      assertFailed(
          handler.handle(client, "default", job(SCRIPT_HEADER), Map.of()),
          ErrorCode.INVALID_RESULT,
          0);
    }
  }

  @Test
  void keepsTheRetriesOfAnInterruptedJob() {
    // The script is not visible yet, so the interrupted thread fails while polling for it.
    final var handler = handler(Duration.ofSeconds(5));
    Thread.currentThread().interrupt();
    try {
      final var outcome = handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());

      final var failed = assertFailed(outcome, ErrorCode.PLATFORM_INTERRUPTED, 3);
      assertThat(failed.backoff()).isEqualTo(BACKOFF);
    } finally {
      assertThat(Thread.interrupted()).isTrue();
    }
  }

  @Test
  void mapsAProvisioningFailureByItsCodeAndTriesAgainWithTheNextJob() {
    store("sum.py", "x");
    provider
        .failEnsure(new ProvisioningException(ErrorCode.PLATFORM_REJECTED, "role missing"))
        .failEnsure(new ProvisioningException(ErrorCode.PLATFORM_UNAVAILABLE, "service error"));

    final var rejected = handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());
    final var unavailable = handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());
    final var completed = handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());

    assertFailed(rejected, ErrorCode.PLATFORM_REJECTED, 0);
    assertFailed(unavailable, ErrorCode.PLATFORM_UNAVAILABLE, null);
    assertThat(completed).isInstanceOf(Outcome.Completed.class);
    assertThat(provider.ensureCalls).isEqualTo(3);
  }

  @Test
  void failsAScriptErrorWithoutRetriesWithItsStackTraceInTheIncident() {
    store("sum.py", "x");
    provider.respond(
        scriptFailure("SCRIPT_ERROR", "ZeroDivisionError: division by zero", "Traceback ..."));

    final var outcome = handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());

    final var failed = assertFailed(outcome, ErrorCode.SCRIPT_ERROR, 0);
    assertThat(failed.message())
        .isEqualTo("SCRIPT_ERROR: ZeroDivisionError: division by zero\nTraceback ...");
    assertThat(failed.backoff()).isEqualTo(Duration.ZERO);
  }

  @Test
  void consumesARetryOnAnUnexpectedException() {
    store("sum.py", "x");
    final var handler =
        new ManagedScriptJobHandler(
            new FakeProvider() {
              @Override
              public ExecutionResponse execute(ScriptLanguage language, ExecutionRequest request) {
                throw new IllegalStateException("unexpected");
              }
            },
            resources(Duration.ofMillis(200)),
            ConnectorsObjectMapperSupplier.getCopy(),
            Duration.ofSeconds(30),
            BACKOFF);

    final var outcome = handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());

    assertFailed(outcome, ErrorCode.PLATFORM_ERROR, null);
  }

  @Test
  void consumesARetryWhenTheExecutorIsMissingAndLetsTheNextJobMakeItReady() {
    store("sum.py", "x");
    provider.respond(
        ExecutionResponse.failed(ErrorCode.DEPLOYMENT_MISSING, "gone"),
        ExecutionResponse.completed(Map.of("sum", 5)));

    final var missing = handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());
    final var completed = handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());

    final var failed = assertFailed(missing, ErrorCode.DEPLOYMENT_MISSING, null);
    assertThat(failed.backoff()).isEqualTo(BACKOFF);
    assertThat(completed).isEqualTo(new Outcome.Completed(Map.of("sum", 5)));
    assertThat(provider.ensureCalls).isEqualTo(2);
  }

  @Test
  void treatsAnUnsupportedContractVersionAsAnInvalidResult() {
    store("sum.py", "x");
    provider.respond(new ExecutionResponse("2", "COMPLETED", Map.of("sum", 5), null));

    final var outcome = handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());

    assertFailed(outcome, ErrorCode.INVALID_RESULT, 0);
  }

  @Test
  void requiresTheVariablesOfACompletedScript() {
    store("sum.py", "x");
    provider.respond(
        new ExecutionResponse("1", "COMPLETED", null, null),
        new ExecutionResponse("1", "COMPLETED", Map.of(), null));

    final var missing = handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());
    final var empty = handler.handle(client, "default", job(SCRIPT_HEADER), Map.of());

    assertFailed(missing, ErrorCode.INVALID_RESULT, 0);
    assertThat(empty).isEqualTo(new Outcome.Completed(Map.of()));
  }

  private ManagedScriptJobHandler handler(Duration visibilityTimeout) {
    return new ManagedScriptJobHandler(
        provider,
        resources(visibilityTimeout),
        ConnectorsObjectMapperSupplier.getCopy(),
        Duration.ofSeconds(30),
        BACKOFF);
  }

  private ScriptResources resources(Duration visibilityTimeout) {
    return new ScriptResources(
        16,
        (camundaClient, resourceKey, timeout) -> {
          fetches.incrementAndGet();
          synchronized (stored) {
            final var resource = stored.get(resourceKey);
            if (resource == null) {
              throw new ResourceNotVisibleException(resourceKey, null);
            }
            return resource;
          }
        },
        visibilityTimeout,
        Duration.ofMillis(10));
  }

  private void store(String name, String source) {
    synchronized (stored) {
      stored.put(SCRIPT_KEY, new ScriptResource(name, source.getBytes(StandardCharsets.UTF_8)));
    }
  }

  private static JobContext job(String linkedResources) {
    return job(Map.of("linkedResources", linkedResources));
  }

  private static JobContext job(Map<String, String> headers) {
    final var job = mock(JobContext.class);
    lenient().when(job.getCustomHeaders()).thenReturn(headers);
    lenient().when(job.getJobKey()).thenReturn(42L);
    lenient().when(job.getRetries()).thenReturn(3);
    lenient().when(job.getTenantId()).thenReturn("<default>");
    lenient().when(job.getBpmnProcessId()).thenReturn("order-process");
    lenient().when(job.getProcessDefinitionVersion()).thenReturn(3);
    lenient().when(job.getProcessInstanceKey()).thenReturn(100L);
    lenient().when(job.getElementId()).thenReturn("compute-sum");
    lenient().when(job.getElementInstanceKey()).thenReturn(101L);
    return job;
  }

  private static ExecutionResponse scriptFailure(String code, String message, String stackTrace) {
    return new ExecutionResponse(
        "1", "FAILED", null, new ExecutionResponse.Error(code, message, stackTrace, false, false));
  }

  private static Outcome.Failed assertFailed(Outcome outcome, ErrorCode code, Integer retries) {
    assertThat(outcome).isInstanceOf(Outcome.Failed.class);
    final var failed = (Outcome.Failed) outcome;
    assertThat(failed.code()).isEqualTo(code.name());
    assertThat(failed.message()).startsWith(code.name() + ": ");
    assertThat(failed.retries()).isEqualTo(retries);
    return failed;
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
