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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs one managed-script job: resolves the linked script, provisions its artifact if needed and
 * invokes it. Returns the job outcome instead of completing or failing the job, which the connector
 * runtime does.
 */
final class ManagedScriptJobHandler {

  static final String LANGUAGE_HEADER = "language";
  static final String RUNTIME_HEADER = "runtime";

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

  private final ManagedCodeProvider provider;
  private final DeploymentRegistry registry;
  private final ScriptResources resources;
  private final ObjectMapper objectMapper;
  private final Duration provisioningTimeout;
  private final Duration retryBackoff;
  private final String deploymentNamePrefix;

  ManagedScriptJobHandler(
      ManagedCodeProvider provider,
      DeploymentRegistry registry,
      ScriptResources resources,
      ObjectMapper objectMapper,
      Duration provisioningTimeout,
      Duration retryBackoff,
      String deploymentNamePrefix) {
    this.provider = provider;
    this.registry = registry;
    this.resources = resources;
    this.objectMapper = objectMapper;
    this.provisioningTimeout = provisioningTimeout;
    this.retryBackoff = retryBackoff;
    this.deploymentNamePrefix = deploymentNamePrefix;
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
    // One budget per job for fetching the resources and provisioning, including re-provisioning,
    // so that the job timeout (provisioning timeout + execution timeout) covers the whole attempt.
    final long provisioningDeadline = System.nanoTime() + provisioningTimeout.toNanos();
    try {
      final var artifact = resolveArtifact(camundaClient, physicalTenantId, job);
      return execute(job, input, artifact, provisioningDeadline);
    } catch (InvalidJobException e) {
      return failed("INVALID_JOB", e.getMessage(), 0, Duration.ZERO);
    } catch (ProvisioningTimeoutException e) {
      LOG.info(
          "Managed-script job {} waited {} for provisioning; failing it without consuming a retry",
          job.getJobKey(),
          provisioningTimeout);
      return failed("PROVISIONING_PENDING", e.getMessage(), job.getRetries(), retryBackoff);
    } catch (ProvisioningException e) {
      final var message = "Provisioning failed (%s): %s".formatted(e.code(), e.getMessage());
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

  private ArtifactSpec resolveArtifact(
      CamundaClient camundaClient, String physicalTenantId, JobContext job)
      throws InterruptedException {
    final var headers = job.getCustomHeaders();
    final var links = LinkedResources.parse(headers, objectMapper);
    final var script = resources.get(camundaClient, physicalTenantId, links.scriptResourceKey());
    final ScriptLanguage scriptLanguage;
    final String runtime;
    try {
      // The element template sets no headers: language and runtime follow the file extension.
      scriptLanguage =
          ScriptLanguage.from(
              optionalHeader(headers, LANGUAGE_HEADER),
              optionalHeader(headers, RUNTIME_HEADER),
              script.name());
      runtime =
          Optional.ofNullable(optionalHeader(headers, RUNTIME_HEADER))
              .orElse(scriptLanguage.defaultRuntime());
    } catch (IllegalArgumentException e) {
      throw new InvalidJobException(e.getMessage());
    }
    final Optional<byte[]> dependencies;
    if (links.dependenciesResourceKey().isPresent()) {
      final long key = links.dependenciesResourceKey().get();
      dependencies = Optional.of(resources.get(camundaClient, physicalTenantId, key).content());
    } else {
      dependencies = Optional.empty();
    }
    return ArtifactSpec.create(
        physicalTenantId,
        job.getTenantId(),
        provider.name(),
        deploymentNamePrefix,
        scriptLanguage,
        runtime,
        script.name(),
        script.content(),
        dependencies);
  }

  private Outcome execute(
      JobContext job, Map<String, Object> input, ArtifactSpec artifact, long provisioningDeadline)
      throws InterruptedException {
    final var request =
        new ExecutionRequest(
            Long.toString(job.getJobKey()),
            input == null ? Map.of() : input,
            executionContext(job));
    var deployment = awaitDeployment(artifact, provisioningDeadline);
    var response = invoke(artifact, deployment, request);
    if (response.isDeploymentMissing()) {
      // The provider lost a deployment this instance considered ready; provision it again once.
      LOG.info(
          "Provider deployment '{}' of managed-script job {} is missing; provisioning it again",
          deployment.deploymentId(),
          job.getJobKey());
      registry.evict(artifact.key(), deployment);
      deployment = awaitDeployment(artifact, provisioningDeadline);
      response = invoke(artifact, deployment, request);
      if (response.isDeploymentMissing()) {
        registry.evict(artifact.key(), deployment);
        return failed(
            ExecutionResponse.DEPLOYMENT_MISSING,
            "DEPLOYMENT_MISSING: provider deployment disappeared again after re-provisioning",
            null,
            retryBackoff);
      }
    }
    return outcome(job, response);
  }

  private ProviderDeployment awaitDeployment(ArtifactSpec artifact, long deadlineNanos)
      throws InterruptedException {
    final var future = registry.ensureDeployment(artifact);
    try {
      // Bounded wait on the shared future: a timeout must not complete or cancel it.
      return future.get(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      throw new ProvisioningTimeoutException(
          "Managed-script artifact is still being provisioned after %s; the job will be retried"
              .formatted(provisioningTimeout));
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ProvisioningException provisioningException) {
        throw provisioningException;
      }
      throw new ProvisioningException(
          "PLATFORM_UNAVAILABLE", "Provisioning failed unexpectedly", true, e.getCause());
    }
  }

  private ExecutionResponse invoke(
      ArtifactSpec artifact, ProviderDeployment deployment, ExecutionRequest request) {
    registry.markUsed(artifact.key());
    final var response = provider.invoke(deployment, request);
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
