/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.processdefinition;

import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ToolsProperties.ProcessDefinitionProperties.RetriesProperties;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessDefinitionClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessDefinitionClient.class);
  private static final String ERROR_CODE_AD_HOC_SUB_PROCESS_XML_FETCH_ERROR =
      "AD_HOC_SUB_PROCESS_XML_FETCH_ERROR";

  private final CamundaClient camundaClient;
  private final RetriesProperties retriesProperties;

  public ProcessDefinitionClient(
      CamundaClient camundaClient, RetriesProperties retriesProperties) {
    this.camundaClient = camundaClient;
    this.retriesProperties = retriesProperties;
  }

  public String getProcessDefinitionXml(Long processDefinitionKey) {
    Exception lastException = null;

    int maxAttempts = 1 + retriesProperties.maxRetries();
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        if (attempt > 1) {
          waitBeforeRetry(attempt, processDefinitionKey);
        }

        return camundaClient.newProcessDefinitionGetXmlRequest(processDefinitionKey).send().join();
      } catch (Exception e) {
        lastException = e;
      }
    }

    final var errorMessage =
        "Failed to retrieve process definition XML with key %s after %d attempt(s)"
            .formatted(processDefinitionKey, maxAttempts);

    LOGGER.error(errorMessage, lastException);
    throw new ConnectorException(
        ERROR_CODE_AD_HOC_SUB_PROCESS_XML_FETCH_ERROR,
        "%s: %s".formatted(errorMessage, lastException.getMessage()),
        lastException);
  }

  private void waitBeforeRetry(int attempt, Long processDefinitionKey) {
    Duration retryDelay = exponentialBackoffRetryDelay(attempt);

    LOGGER.warn(
        "Retrying to fetch process definition XML for process definition key {}. Attempt {}/{}. Waiting for {}.",
        processDefinitionKey,
        attempt,
        1 + retriesProperties.maxRetries(),
        retryDelay);

    try {
      Thread.sleep(retryDelay);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ConnectorException(
          ERROR_CODE_AD_HOC_SUB_PROCESS_XML_FETCH_ERROR,
          "Interrupted while retrying to fetch process definition XML with key '%s'."
              .formatted(processDefinitionKey));
    }
  }

  private Duration exponentialBackoffRetryDelay(int attempt) {
    return retriesProperties
        .initialRetryDelay()
        .multipliedBy(Math.round(Math.pow(2, attempt - 2))); // 2^0 (x1), 2^1 (x2), 2^2 (x4), ...
  }
}
