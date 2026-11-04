/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.camunda.client.CamundaClient;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.managedscript.ScriptResources.ResourceNotVisibleException;
import io.camunda.connector.managedscript.api.ErrorCode;
import io.camunda.connector.managedscript.api.ExecutionRequest;
import io.camunda.connector.managedscript.api.ExecutionResponse;
import io.camunda.connector.managedscript.api.ManagedCodeProvider;
import io.camunda.connector.managedscript.api.ProvisioningException;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * <p>A job waits for the executor of its language up to the provisioning timeout, which also covers
 * making it ready again once after it was reported missing. Logs never contain the script, its
 * input or its output, nor failure messages, which can quote them.
 */
final class ManagedScriptJobHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ManagedScriptJobHandler.class);

  /** Outcome of a managed-script job. */
  sealed interface Outcome {

    record Completed(Map<String, Object> variables) implements Outcome {}

    /**
     * @param retries the retries the job keeps; {@code null} lets the runtime consume one
     * @param backoff {@link Duration#ZERO} lets the task's {@code retryBackoff} header apply
     */
    record Failed(String code, String message, Integer retries, Duration backoff)
        implements Outcome {}
  }

  private final ManagedCodeProvider provider;
  private final ExecutorReadiness readiness;
  private final ScriptResources resources;
  private final ObjectReader linksReader;
  private final Duration provisioningTimeout;
  private final Duration executionTimeout;
  private final Duration retryBackoff;

  ManagedScriptJobHandler(
      ManagedCodeProvider provider,
      ExecutorReadiness readiness,
      ScriptResources resources,
      ObjectMapper objectMapper,
      Duration provisioningTimeout,
      Duration executionTimeout,
      Duration retryBackoff) {
    this.provider = provider;
    this.readiness = readiness;
    this.resources = resources;
    this.linksReader = LinkedResources.reader(objectMapper);
    this.provisioningTimeout = provisioningTimeout;
    this.executionTimeout = executionTimeout;
    this.retryBackoff = retryBackoff;
  }

  /**
   * @param physicalTenantId never {@code null}; resource keys are unique within one physical tenant
   * @param input the value of the script input variable, passed to the script as its variables
   */
  Outcome handle(
      CamundaClient camundaClient,
      String physicalTenantId,
      JobContext job,
      Map<String, Object> input) {
    final long start = System.nanoTime();
    Long resourceKey = null;
    ScriptLanguage language = null;
    Outcome outcome;
    try {
      final var links = LinkedResources.parse(job.getCustomHeaders(), linksReader);
      if (links.dependenciesResourceKey().isPresent()) {
        throw new InvalidJobException(
            ErrorCode.UNSUPPORTED_DEPENDENCIES, "Managed scripts do not support dependencies yet");
      }
      resourceKey = links.scriptResourceKey();
      final var script = resources.get(camundaClient, physicalTenantId, resourceKey);
      language = languageOf(script.name());
      outcome = execute(job, input, language, script.content());
    } catch (InvalidJobException e) {
      outcome = failed(job, e.code(), e.getMessage(), null, language);
    } catch (ResourceNotVisibleException e) {
      outcome = failed(job, ErrorCode.PLATFORM_UNAVAILABLE, e.getMessage(), null, language);
    } catch (ProvisioningException e) {
      // A denial of the invocation, while the grants of a new cluster propagate, is waited out.
      final var code =
          e.permissionDenied() && readiness.withinGrantPropagationWindow()
              ? ErrorCode.PROVISIONING_PENDING
              : e.code();
      outcome =
          failed(
              job,
              code,
              "The executor of %s is not ready: %s"
                  .formatted(language == null ? "the script" : language.language(), e.getMessage()),
              null,
              language);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      outcome =
          failed(
              job,
              ErrorCode.PLATFORM_INTERRUPTED,
              "The managed-script worker was interrupted",
              null,
              language);
    } catch (RuntimeException e) {
      // The exception message can quote the input, so only its type is logged.
      LOG.warn(
          "Managed-script job {} failed unexpectedly: {}", job.getJobKey(), e.getClass().getName());
      outcome =
          failed(
              job,
              ErrorCode.PLATFORM_ERROR,
              "The managed-script worker failed: " + e.getClass().getSimpleName(),
              null,
              language);
    }
    LOG.info(
        "Managed-script job {}: script resource {}, language {}, outcome {}, {} ms",
        job.getJobKey(),
        resourceKey,
        language == null ? null : language.language(),
        outcome instanceof Outcome.Failed failed ? failed.code() : ExecutionResponse.COMPLETED,
        Duration.ofNanos(System.nanoTime() - start).toMillis());
    return outcome;
  }

  private Outcome execute(
      JobContext job, Map<String, Object> input, ScriptLanguage language, String source)
      throws InterruptedException {
    final var request =
        new ExecutionRequest(
            Long.toString(job.getJobKey()),
            source,
            input == null ? Map.of() : input,
            executionContext(job),
            executionTimeout.toMillis());
    // One budget for the job, so that a missing executor does not double its wait.
    final long deadline = System.nanoTime() + provisioningTimeout.toNanos();
    var ready = readiness.start(language);
    awaitExecutor(language, ready, deadline);
    var response = provider.execute(language, request);
    if (response != null && response.isDeploymentMissing()) {
      // Made ready again once within the job; missing again, it consumes a retry.
      ready = readiness.restart(language, ready);
      awaitExecutor(language, ready, deadline);
      response = provider.execute(language, request);
    }
    return outcome(job, language, response);
  }

  private void awaitExecutor(
      ScriptLanguage language, CompletableFuture<Void> ready, long deadlineNanos)
      throws InterruptedException {
    try {
      // A bounded wait on the shared attempt: a timeout must not complete or cancel it.
      ready.get(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      // A retryable failure that lasts must end in an incident.
      final var lasting = readiness.lastingFailure(language, provisioningTimeout.multipliedBy(2));
      if (lasting.isPresent()) {
        throw lasting.get();
      }
      throw new ProvisioningException(
          ErrorCode.PROVISIONING_PENDING,
          "still being made ready after %s; the job will be retried"
              .formatted(provisioningTimeout));
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ProvisioningException failure) {
        throw failure;
      }
      throw new IllegalStateException(e.getCause());
    }
  }

  private Outcome outcome(JobContext job, ScriptLanguage language, ExecutionResponse response) {
    if (response == null || response.outcome() == null) {
      return failed(
          job, ErrorCode.INVALID_RESULT, "The provider returned no outcome", null, language);
    }
    if (!ExecutionRequest.CONTRACT_VERSION.equals(response.contractVersion())) {
      return failed(
          job,
          ErrorCode.INVALID_RESULT,
          "Unsupported contract version '%s'".formatted(response.contractVersion()),
          null,
          language);
    }
    if (ExecutionResponse.COMPLETED.equals(response.outcome())) {
      if (response.variables() == null) {
        return failed(
            job, ErrorCode.INVALID_RESULT, "The script returned no variables", null, language);
      }
      return new Outcome.Completed(response.variables());
    }
    final var error = response.error();
    if (!ExecutionResponse.FAILED.equals(response.outcome()) || error == null) {
      return failed(
          job,
          ErrorCode.INVALID_RESULT,
          "Unsupported outcome '%s'".formatted(response.outcome()),
          null,
          language);
    }
    final var code = ErrorCode.of(error.code());
    if (code.isEmpty()) {
      return failed(
          job,
          ErrorCode.INVALID_RESULT,
          "Unknown error code '%s': %s".formatted(error.code(), error.message()),
          null,
          language);
    }
    return failed(job, code.get(), error.message(), error.stackTrace(), language);
  }

  /** Fails the job with the retries that the error code calls for. */
  private Outcome.Failed failed(
      JobContext job, ErrorCode code, String message, String stackTrace, ScriptLanguage language) {
    final var text = IncidentMessage.format(code, message, stackTrace, language);
    return switch (code.retries()) {
      case NONE_LEFT -> new Outcome.Failed(code.name(), text, 0, Duration.ZERO);
      // The modeler sets the backoff next to the retries a job consumes.
      case CONSUME_ONE -> new Outcome.Failed(code.name(), text, null, Duration.ZERO);
      // A job that keeps its retries can fail without bound, so the operator paces it.
      case UNCHANGED -> new Outcome.Failed(code.name(), text, job.getRetries(), retryBackoff);
    };
  }

  /** The language follows the extension of the script's file name, and nothing else. */
  private static ScriptLanguage languageOf(String resourceName) {
    for (var candidate : ScriptLanguage.values()) {
      if (resourceName != null && resourceName.endsWith(candidate.extension())) {
        return candidate;
      }
    }
    throw new InvalidJobException(
        "Cannot derive the managed-script language from the script resource '%s': use a .py or .js file"
            .formatted(resourceName));
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
}
