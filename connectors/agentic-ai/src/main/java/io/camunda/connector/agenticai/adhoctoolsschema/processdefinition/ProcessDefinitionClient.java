/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.processdefinition;

import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ToolsProperties.ProcessDefinitionProperties.RetriesProperties;
import io.camunda.connector.agenticai.util.retry.CamundaApiRetry;
import io.camunda.connector.agenticai.util.retry.CamundaApiRetry.FailureReason;
import io.camunda.connector.agenticai.util.retry.CamundaApiRetry.Sleeper;
import io.camunda.connector.agenticai.util.retry.ErrorClassifier;
import io.camunda.connector.api.error.ConnectorException;

public class ProcessDefinitionClient {
  private static final String ERROR_CODE_AD_HOC_SUB_PROCESS_XML_FETCH_ERROR =
      "AD_HOC_SUB_PROCESS_XML_FETCH_ERROR";

  private final CamundaClient camundaClient;
  private final RetriesProperties retriesProperties;

  public ProcessDefinitionClient(CamundaClient camundaClient, RetriesProperties retriesProperties) {
    this.camundaClient = camundaClient;
    this.retriesProperties = retriesProperties;
  }

  public String getProcessDefinitionXml(Long processDefinitionKey) {
    return CamundaApiRetry.execute(
        () -> camundaClient.newProcessDefinitionGetXmlRequest(processDefinitionKey).send().join(),
        ErrorClassifier.onAllExceptions(),
        retriesProperties.maxRetries(),
        retriesProperties.initialRetryDelay(),
        (cause, attempt, reason) -> buildException(processDefinitionKey, cause, attempt, reason),
        Sleeper.threadSleep());
  }

  private ConnectorException buildException(
      Long processDefinitionKey, Throwable cause, int attempt, FailureReason reason) {
    final String message =
        switch (reason) {
          case INTERRUPTED ->
              "Interrupted while retrying to fetch process definition XML with key %s."
                  .formatted(processDefinitionKey);
          case RETRIES_EXHAUSTED, PERMANENT_ERROR ->
              "Failed to retrieve process definition XML with key %s after %d attempt(s): %s"
                  .formatted(processDefinitionKey, attempt, cause.getMessage());
        };
    return new ConnectorException(ERROR_CODE_AD_HOC_SUB_PROCESS_XML_FETCH_ERROR, message, cause);
  }
}
