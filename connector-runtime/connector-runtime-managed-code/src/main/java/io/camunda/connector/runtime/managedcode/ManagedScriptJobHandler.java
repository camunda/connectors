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
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
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
 * Handles one managed-script job: resolves the linked script, provisions its artifact on first use,
 * invokes it through the provider and translates the outcome into a job command.
 *
 * <p>Script content and variables are never logged.
 */
final class ManagedScriptJobHandler {

  static final String LANGUAGE_HEADER = "language";
  static final String RUNTIME_HEADER = "runtime";

  private static final Logger LOG = LoggerFactory.getLogger(ManagedScriptJobHandler.class);
  private static final int MAX_ERROR_MESSAGE_LENGTH = 1_024;

  private final ManagedCodeProvider provider;
  private final DeploymentRegistry registry;
  private final ScriptResources resources;
  private final ObjectMapper objectMapper;
  private final Duration provisioningTimeout;
  private final Duration retryBackoff;

  ManagedScriptJobHandler(
      ManagedCodeProvider provider,
      DeploymentRegistry registry,
      ScriptResources resources,
      ObjectMapper objectMapper,
      Duration provisioningTimeout,
      Duration retryBackoff) {
    this.provider = provider;
    this.registry = registry;
    this.resources = resources;
    this.objectMapper = objectMapper;
    this.provisioningTimeout = provisioningTimeout;
    this.retryBackoff = retryBackoff;
  }

  void handle(
      CamundaClient camundaClient, String physicalTenantId, JobClient jobClient, ActivatedJob job) {
    try {
      final var artifact = resolveArtifact(camundaClient, physicalTenantId, job);
      execute(jobClient, job, artifact);
    } catch (InvalidJobException e) {
      fail(jobClient, job, 0, e.getMessage(), Duration.ZERO);
    } catch (ProvisioningTimeoutException e) {
      LOG.info(
          "Managed-script job {} waited {} for provisioning; failing it without consuming a retry",
          job.getKey(),
          provisioningTimeout);
      fail(jobClient, job, job.getRetries(), e.getMessage(), retryBackoff);
    } catch (ProvisioningException e) {
      final var message = "Provisioning failed (%s): %s".formatted(e.code(), e.getMessage());
      if (e.retryable()) {
        fail(jobClient, job, job.getRetries() - 1, message, retryBackoff);
      } else {
        fail(jobClient, job, 0, message, Duration.ZERO);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      fail(jobClient, job, job.getRetries(), "Managed-script worker was interrupted", retryBackoff);
    } catch (RuntimeException e) {
      LOG.warn("Managed-script job {} failed: {}", job.getKey(), e.getClass().getSimpleName(), e);
      fail(
          jobClient,
          job,
          job.getRetries() - 1,
          "Managed-script worker failed: " + safeMessage(e),
          retryBackoff);
    }
  }

  private ArtifactSpec resolveArtifact(
      CamundaClient camundaClient, String physicalTenantId, ActivatedJob job)
      throws InterruptedException {
    final var headers = job.getCustomHeaders();
    final var links = LinkedResources.parse(headers, objectMapper);
    final var language = requiredHeader(headers, LANGUAGE_HEADER);
    final var runtime = requiredHeader(headers, RUNTIME_HEADER);
    final var script = resources.get(camundaClient, physicalTenantId, links.scriptResourceKey());
    final ScriptLanguage scriptLanguage;
    try {
      scriptLanguage = ScriptLanguage.from(language, runtime, script.name());
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
        scriptLanguage,
        runtime,
        script.name(),
        script.content(),
        dependencies);
  }

  private void execute(JobClient jobClient, ActivatedJob job, ArtifactSpec artifact)
      throws InterruptedException {
    final var request =
        new ExecutionRequest(
            Long.toString(job.getKey()), job.getVariablesAsMap(), executionContext(job));
    // One provisioning budget per job, so that re-provisioning stays within the job timeout.
    final long provisioningDeadline = System.nanoTime() + provisioningTimeout.toNanos();
    var deployment = awaitDeployment(artifact, provisioningDeadline);
    var response = invoke(artifact, deployment, request);
    if (response.isDeploymentMissing()) {
      // The provider lost a deployment this instance considered ready; provision it again once.
      LOG.info(
          "Provider deployment '{}' of managed-script job {} is missing; provisioning it again",
          deployment.deploymentId(),
          job.getKey());
      registry.evict(artifact.key(), deployment);
      deployment = awaitDeployment(artifact, provisioningDeadline);
      response = invoke(artifact, deployment, request);
      if (response.isDeploymentMissing()) {
        registry.evict(artifact.key(), deployment);
        fail(
            jobClient,
            job,
            job.getRetries() - 1,
            "DEPLOYMENT_MISSING: provider deployment disappeared again after re-provisioning",
            retryBackoff);
        return;
      }
    }
    complete(jobClient, job, response);
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

  private void complete(JobClient jobClient, ActivatedJob job, ExecutionResponse response) {
    if (ExecutionResponse.COMPLETED.equals(response.outcome())) {
      final Map<String, Object> variables =
          response.variables() == null ? Map.of() : response.variables();
      jobClient.newCompleteCommand(job).variables(variables).execute();
      LOG.debug("Completed managed-script job {}", job.getKey());
      return;
    }
    if (!ExecutionResponse.FAILED.equals(response.outcome()) || response.error() == null) {
      fail(
          jobClient,
          job,
          0,
          "INVALID_RESULT: unsupported outcome '%s'".formatted(response.outcome()),
          Duration.ZERO);
      return;
    }
    final var error = response.error();
    final var message =
        "%s: %s"
            .formatted(
                error.code() == null ? "SCRIPT_ERROR" : error.code(),
                error.message() == null ? "Managed script failed" : error.message());
    if (error.retryable()) {
      fail(jobClient, job, job.getRetries() - 1, message, retryBackoff);
    } else {
      fail(jobClient, job, 0, message, Duration.ZERO);
    }
  }

  private static void fail(
      JobClient jobClient,
      ActivatedJob job,
      int retries,
      String errorMessage,
      Duration retryBackoff) {
    final var command =
        jobClient
            .newFailCommand(job)
            .retries(Math.max(0, retries))
            .errorMessage(bounded(errorMessage));
    if (!retryBackoff.isZero()) {
      command.retryBackoff(retryBackoff);
    }
    command.execute();
  }

  private static Map<String, Object> executionContext(ActivatedJob job) {
    final Map<String, Object> context = new LinkedHashMap<>();
    context.put("tenantId", job.getTenantId());
    context.put("processDefinitionId", job.getBpmnProcessId());
    context.put("processDefinitionVersion", job.getProcessDefinitionVersion());
    context.put("processInstanceKey", Long.toString(job.getProcessInstanceKey()));
    context.put("elementId", job.getElementId());
    context.put("elementInstanceKey", Long.toString(job.getElementInstanceKey()));
    context.put("jobKey", Long.toString(job.getKey()));
    return context;
  }

  private static String requiredHeader(Map<String, String> headers, String name) {
    final var value = headers.get(name);
    if (value == null || value.isBlank()) {
      throw new InvalidJobException("Missing '%s' task header".formatted(name));
    }
    return value;
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
