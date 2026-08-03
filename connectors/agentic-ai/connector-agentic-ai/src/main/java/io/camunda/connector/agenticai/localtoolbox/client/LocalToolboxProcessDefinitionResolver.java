/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.localtoolbox.client;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.RetriesProperties;
import io.camunda.connector.agenticai.common.util.retry.CamundaApiRetry;
import io.camunda.connector.agenticai.common.util.retry.CamundaApiRetry.FailureReason;
import io.camunda.connector.agenticai.common.util.retry.CamundaApiRetry.Sleeper;
import io.camunda.connector.agenticai.common.util.retry.ErrorClassifier;
import io.camunda.connector.agenticai.localtoolbox.LocalToolboxErrorCodes;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a BPMN process id (+ optional version) to the numeric process definition key that {@link
 * io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver}
 * operates on. Version is optional: when omitted, the latest deployed version is used.
 */
public class LocalToolboxProcessDefinitionResolver {

  private final CamundaClient camundaClient;
  private final RetriesProperties retriesProperties;

  public LocalToolboxProcessDefinitionResolver(
      CamundaClient camundaClient, RetriesProperties retriesProperties) {
    this.camundaClient = camundaClient;
    this.retriesProperties = retriesProperties;
  }

  public Long resolveProcessDefinitionKey(String processId, @Nullable Integer version) {
    final List<ProcessDefinition> processDefinitions =
        CamundaApiRetry.execute(
            () ->
                camundaClient
                    .newProcessDefinitionSearchRequest()
                    .filter(filter -> filter.processDefinitionId(processId))
                    .sort(sort -> sort.version().asc())
                    .send()
                    .join()
                    .items(),
            ErrorClassifier.onAllExceptions(),
            retriesProperties.maxRetries(),
            retriesProperties.initialRetryDelay(),
            (cause, attempt, reason) -> buildFetchException(processId, cause, attempt, reason),
            Sleeper.threadSleep());

    if (processDefinitions.isEmpty()) {
      throw notFoundException(processId, version);
    }

    if (version == null) {
      // sorted ascending by version -> the last item is the latest deployed version
      return processDefinitions.getLast().getProcessDefinitionKey();
    }

    return processDefinitions.stream()
        .filter(processDefinition -> processDefinition.getVersion() == version)
        .findFirst()
        .map(ProcessDefinition::getProcessDefinitionKey)
        .orElseThrow(() -> notFoundException(processId, version));
  }

  private ConnectorException notFoundException(String processId, @Nullable Integer version) {
    return new ConnectorException(
        LocalToolboxErrorCodes.ERROR_CODE_PROCESS_DEFINITION_NOT_FOUND,
        version == null
            ? "No deployed process definition found with process id '%s'.".formatted(processId)
            : "No deployed process definition found with process id '%s' and version %d."
                .formatted(processId, version));
  }

  private ConnectorException buildFetchException(
      String processId, Throwable cause, int attempt, FailureReason reason) {
    final String message =
        switch (reason) {
          case INTERRUPTED ->
              "Interrupted while retrying to look up process definitions with process id '%s'."
                  .formatted(processId);
          case RETRIES_EXHAUSTED, PERMANENT_ERROR ->
              "Failed to look up process definitions with process id '%s' after %d attempt(s): %s"
                  .formatted(processId, attempt, cause.getMessage());
        };
    return new ConnectorException(
        LocalToolboxErrorCodes.ERROR_CODE_PROCESS_DEFINITION_NOT_FOUND, message, cause);
  }
}
