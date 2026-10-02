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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ManagedScriptJobHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ManagedScriptJobHandler.class);
  private static final int MAX_ERROR_MESSAGE_LENGTH = 1_024;

  private final ManagedScriptControlPlane controlPlane;
  private final LocalProcessScriptExecutor executor;
  private final ObjectMapper objectMapper;
  private final Duration readinessBackoff;

  ManagedScriptJobHandler(
      ManagedScriptControlPlane controlPlane,
      LocalProcessScriptExecutor executor,
      ObjectMapper objectMapper,
      Duration readinessBackoff) {
    this.controlPlane = controlPlane;
    this.executor = executor;
    this.objectMapper = objectMapper;
    this.readinessBackoff = readinessBackoff;
  }

  void handle(
      CamundaClient camundaClient, String physicalTenantId, JobClient jobClient, ActivatedJob job) {
    try {
      final var invocation =
          controlPlane.getInvocation(
              camundaClient, physicalTenantId, job.getProcessDefinitionKey(), job.getElementId());
      switch (invocation.status()) {
        case PENDING, BUILDING, DEPLOYING -> deferUntilReady(jobClient, job, invocation.status());
        case FAILED -> fail(jobClient, job, 0, "Managed-script deployment failed", Duration.ZERO);
        case READY -> execute(jobClient, job, invocation);
      }
    } catch (Exception e) {
      LOG.error("Failed to execute managed-script job '{}'", job.getKey(), e);
      fail(
          jobClient,
          job,
          Math.max(0, job.getRetries() - 1),
          "Managed-script worker failed: " + safeMessage(e),
          readinessBackoff);
    }
  }

  private void execute(JobClient jobClient, ActivatedJob job, ManagedScriptInvocation invocation)
      throws Exception {
    if (!"fake".equals(invocation.provider())) {
      fail(
          jobClient,
          job,
          0,
          "Local execution only supports definitions deployed by the fake provider",
          Duration.ZERO);
      return;
    }
    if (invocation.providerDeploymentId().isBlank()) {
      fail(
          jobClient,
          job,
          job.getRetries(),
          "Managed-script definition is READY without a provider deployment ID",
          readinessBackoff);
      return;
    }

    final var result =
        executor.execute(
            invocation,
            job.getVariablesAsMap(),
            executionContext(job),
            Long.toString(job.getKey()));
    if ("COMPLETED".equals(result.response().path("outcome").asText())) {
      final Map<String, Object> variables =
          objectMapper.convertValue(result.response().path("variables"), new TypeReference<>() {});
      jobClient.newCompleteCommand(job).variables(variables).execute();
      LOG.info(
          "Completed managed-script job '{}' locally in {} ms",
          job.getKey(),
          result.durationMillis());
      if (!result.logTail().isBlank()) {
        LOG.debug(
            "Managed-script job '{}' logs{}:\n{}",
            job.getKey(),
            result.logsTruncated() ? " (truncated)" : "",
            result.logTail());
      }
      return;
    }

    final var error = result.response().path("error");
    final var retryable = error.path("retryable").asBoolean(false);
    final var retries = retryable ? Math.max(0, job.getRetries() - 1) : 0;
    fail(
        jobClient,
        job,
        retries,
        "%s: %s"
            .formatted(
                error.path("code").asText("SCRIPT_ERROR"),
                error.path("message").asText("Managed script failed")),
        retryable ? readinessBackoff : Duration.ZERO);
  }

  private void deferUntilReady(
      JobClient jobClient, ActivatedJob job, ManagedScriptInvocation.Status status) {
    fail(
        jobClient,
        job,
        job.getRetries(),
        "Managed-script deployment is not ready: " + status,
        readinessBackoff);
  }

  private static void fail(
      JobClient jobClient,
      ActivatedJob job,
      int retries,
      String errorMessage,
      Duration retryBackoff) {
    final var command =
        jobClient.newFailCommand(job).retries(retries).errorMessage(bounded(errorMessage));
    if (!retryBackoff.isZero()) {
      command.retryBackoff(retryBackoff);
    }
    command.execute();
  }

  private static Map<String, Object> executionContext(ActivatedJob job) {
    final Map<String, Object> context = new LinkedHashMap<>();
    context.put("jobKey", job.getKey());
    context.put("processInstanceKey", job.getProcessInstanceKey());
    context.put("processDefinitionKey", job.getProcessDefinitionKey());
    context.put("bpmnProcessId", job.getBpmnProcessId());
    context.put("elementId", job.getElementId());
    context.put("elementInstanceKey", job.getElementInstanceKey());
    context.put("tenantId", job.getTenantId());
    return context;
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
}
