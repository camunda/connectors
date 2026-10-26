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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.api.outbound.JobContext;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs one managed-script job: resolves the linked script and sends it, with its input, to the
 * executor of its language. Returns the job outcome instead of completing or failing the job, which
 * the connector runtime does.
 *
 * <p>No job provisions anything: the executors are created when the runtime starts. A job that
 * arrives before they are ready waits for a bounded time, then fails without consuming a retry.
 */
final class ManagedScriptJobHandler {

  static final String LANGUAGE_HEADER = "language";
  static final String RUNTIME_HEADER = "runtime";

  /** The runtime headers a task may set: the runtime of the executor of the language. */
  private static final Map<ScriptLanguage, Set<String>> EXECUTOR_RUNTIMES =
      Map.of(
          ScriptLanguage.PYTHON, Set.of("python3", "python3.13"),
          ScriptLanguage.JAVASCRIPT, Set.of("nodejs", "nodejs22", "nodejs22.x"));

  private static final Logger LOG = LoggerFactory.getLogger(ManagedScriptJobHandler.class);
  private static final int MAX_ERROR_MESSAGE_LENGTH = 1_024;

  /** Outcome of a managed-script job. */
  sealed interface Outcome {

    record Completed(Map<String, Object> variables) implements Outcome {}

    /**
     * @param retries the retries the job keeps; {@code null} lets the runtime consume one
     * @param backoff {@link Duration#ZERO} for no backoff
     */
    record Failed(String code, String message, Integer retries, Duration backoff)
        implements Outcome {}
  }

  /** A resolved script: its language and its source. */
  private record Script(ScriptLanguage language, String source) {}

  private final ManagedCodeProvider provider;
  private final ExecutorReadiness executors;
  private final ScriptResources resources;
  private final ObjectMapper objectMapper;
  private final Duration provisioningTimeout;
  private final Duration executionTimeout;
  private final Duration retryBackoff;

  /**
   * @param provisioningTimeout how long a job waits for the executors before it fails without
   *     consuming a retry
   */
  ManagedScriptJobHandler(
      ManagedCodeProvider provider,
      ExecutorReadiness executors,
      ScriptResources resources,
      ObjectMapper objectMapper,
      Duration provisioningTimeout,
      Duration executionTimeout,
      Duration retryBackoff) {
    this.provider = provider;
    this.executors = executors;
    this.resources = resources;
    this.objectMapper = objectMapper;
    this.provisioningTimeout = provisioningTimeout;
    this.executionTimeout = executionTimeout;
    this.retryBackoff = retryBackoff;
  }

  /**
   * @param physicalTenantId never {@code null}; part of the artifact key
   * @param input the value of the script input variable, passed to the script as its variables
   */
  Outcome handle(
      CamundaClient camundaClient,
      String physicalTenantId,
      JobContext job,
      Map<String, Object> input) {
    // One budget per job for fetching the script and waiting for the executors, including after a
    // missing executor, so that the job timeout (provisioning timeout + execution timeout) covers
    // the whole attempt.
    final long provisioningDeadline = System.nanoTime() + provisioningTimeout.toNanos();
    try {
      final var script = resolveScript(camundaClient, physicalTenantId, job);
      return execute(job, input, script, provisioningDeadline);
    } catch (InvalidJobException e) {
      return failed("INVALID_JOB", e.getMessage(), 0, Duration.ZERO);
    } catch (ProvisioningTimeoutException e) {
      LOG.info(
          "Managed-script job {} waited {} for the executors; failing it without consuming a retry",
          job.getJobKey(),
          provisioningTimeout);
      return failed("PROVISIONING_PENDING", e.getMessage(), job.getRetries(), retryBackoff);
    } catch (ProvisioningException e) {
      final var message =
          "The executors are not available (%s): %s".formatted(e.code(), e.getMessage());
      if (e.retryable()) {
        return failed(e.code(), message, null, retryBackoff);
      }
      return failed(e.code(), message, 0, Duration.ZERO);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return failed(
          "INTERRUPTED", "Managed-script worker was interrupted", job.getRetries(), retryBackoff);
    } catch (RuntimeException e) {
      LOG.warn(
          "Managed-script job {} failed: {}", job.getJobKey(), e.getClass().getSimpleName(), e);
      return failed(
          "PLATFORM_ERROR", "Managed-script worker failed: " + safeMessage(e), null, retryBackoff);
    }
  }

  private Script resolveScript(CamundaClient camundaClient, String physicalTenantId, JobContext job)
      throws InterruptedException {
    final var headers = job.getCustomHeaders();
    final var links = LinkedResources.parse(headers, objectMapper);
    if (links.dependenciesResourceKey().isPresent()) {
      throw new InvalidJobException(
          "UNSUPPORTED_DEPENDENCIES: managed scripts do not support dependencies yet");
    }
    final var script = resources.get(camundaClient, physicalTenantId, links.scriptResourceKey());
    final ScriptLanguage language;
    try {
      // The element template sets no headers: the language follows the file extension.
      final var runtime = optionalHeader(headers, RUNTIME_HEADER);
      language =
          ScriptLanguage.from(optionalHeader(headers, LANGUAGE_HEADER), runtime, script.name());
      if (runtime != null
          && !EXECUTOR_RUNTIMES.get(language).contains(runtime.toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException(
            "Runtime '%s' cannot be chosen per script: every %s script runs on the executor of"
                    .formatted(runtime, language.language())
                + " its language, "
                + EXECUTOR_RUNTIMES.get(language));
      }
    } catch (IllegalArgumentException e) {
      throw new InvalidJobException(e.getMessage());
    }
    return new Script(language, new String(script.content(), StandardCharsets.UTF_8));
  }

  private Outcome execute(
      JobContext job, Map<String, Object> input, Script script, long provisioningDeadline)
      throws InterruptedException {
    final var request =
        new ExecutionRequest(
            Long.toString(job.getJobKey()),
            script.source(),
            input == null ? Map.of() : input,
            executionContext(job),
            executionTimeout.toMillis());
    awaitExecutors(executors.start(), provisioningDeadline);
    var response = invoke(script.language(), request);
    if (response.isDeploymentMissing()) {
      // The executor this instance considered ready is gone; create it again, once.
      LOG.info(
          "Managed-script executor of job {} is missing; creating the executors again",
          job.getJobKey());
      awaitExecutors(executors.restart(), provisioningDeadline);
      response = invoke(script.language(), request);
      if (response.isDeploymentMissing()) {
        return failed(
            ExecutionResponse.DEPLOYMENT_MISSING,
            "DEPLOYMENT_MISSING: the executor disappeared again after it was created",
            null,
            retryBackoff);
      }
    }
    return outcome(job, response);
  }

  private void awaitExecutors(CompletableFuture<Void> ready, long deadlineNanos)
      throws InterruptedException {
    try {
      // Bounded wait on the shared future: a timeout must not complete or cancel it.
      ready.get(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      // A failure that stays retryable, such as a lasting denial, must end in an incident.
      final var lasting = executors.lastingFailure(provisioningTimeout.multipliedBy(2));
      if (lasting.isPresent()) {
        throw lasting.get();
      }
      throw new ProvisioningTimeoutException(
          "The managed-code executors are still being created after %s; the job will be retried"
              .formatted(provisioningTimeout));
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ProvisioningException provisioningException) {
        // A permanent failure; a job after the backoff tries again.
        throw provisioningException;
      }
      throw new ProvisioningException(
          "PLATFORM_UNAVAILABLE", "Creating the executors failed unexpectedly", true, e.getCause());
    }
  }

  private ExecutionResponse invoke(ScriptLanguage language, ExecutionRequest request) {
    final var response = provider.execute(language, request);
    if (response == null || response.outcome() == null) {
      return ExecutionResponse.failed("INVALID_RESULT", "Provider returned no outcome", false);
    }
    return response;
  }

  private Outcome outcome(JobContext job, ExecutionResponse response) {
    if (ExecutionResponse.COMPLETED.equals(response.outcome())) {
      LOG.debug("Completed managed-script job {}", job.getJobKey());
      return new Outcome.Completed(response.variables() == null ? Map.of() : response.variables());
    }
    if (!ExecutionResponse.FAILED.equals(response.outcome()) || response.error() == null) {
      return failed(
          "INVALID_RESULT",
          "INVALID_RESULT: unsupported outcome '%s'".formatted(response.outcome()),
          0,
          Duration.ZERO);
    }
    final var error = response.error();
    final var code = error.code() == null ? "SCRIPT_ERROR" : error.code();
    final var message =
        "%s: %s"
            .formatted(code, error.message() == null ? "Managed script failed" : error.message());
    if (error.retryable()) {
      return failed(code, message, null, retryBackoff);
    }
    return failed(code, message, 0, Duration.ZERO);
  }

  private static Outcome.Failed failed(
      String code, String message, Integer retries, Duration backoff) {
    return new Outcome.Failed(
        code, bounded(message), retries == null ? null : Math.max(0, retries), backoff);
  }

  private static Map<String, Object> executionContext(JobContext job) {
    final Map<String, Object> context = new LinkedHashMap<>();
    context.put("tenantId", job.getTenantId());
    context.put("processDefinitionId", job.getBpmnProcessId());
    context.put("processDefinitionVersion", job.getProcessDefinitionVersion());
    context.put("processInstanceKey", Long.toString(job.getProcessInstanceKey()));
    context.put("elementId", job.getElementId());
    context.put("elementInstanceKey", Long.toString(job.getElementInstanceKey()));
    context.put("jobKey", Long.toString(job.getJobKey()));
    return context;
  }

  private static String optionalHeader(Map<String, String> headers, String name) {
    final var value = headers.get(name);
    return value == null || value.isBlank() ? null : value;
  }

  private static String safeMessage(Exception error) {
    final var message = error.getMessage();
    return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
  }

  private static String bounded(String value) {
    return value.length() <= MAX_ERROR_MESSAGE_LENGTH
        ? value
        : value.substring(0, MAX_ERROR_MESSAGE_LENGTH);
  }

  private static final class ProvisioningTimeoutException extends RuntimeException {
    private ProvisioningTimeoutException(String message) {
      super(message);
    }
  }
}
