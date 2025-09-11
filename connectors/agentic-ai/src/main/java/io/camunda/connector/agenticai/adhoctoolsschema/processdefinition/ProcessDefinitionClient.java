/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.processdefinition;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ProcessDefinitionConfiguration.RetriesConfiguration;
import io.camunda.connector.api.error.ConnectorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessDefinitionClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessDefinitionClient.class);

  private static final String ERROR_CODE_AD_HOC_SUB_PROCESS_XML_FETCH_ERROR =
      "AD_HOC_SUB_PROCESS_XML_FETCH_ERROR";

  private final CamundaClient camundaClient;
  private final RetriesConfiguration retriesConfiguration;

  public ProcessDefinitionClient(
      CamundaClient camundaClient, RetriesConfiguration retriesConfiguration) {
    this.camundaClient = camundaClient;
    this.retriesConfiguration = retriesConfiguration;
  }

  public String getProcessDefinitionXml(Long processDefinitionKey) {
    Exception lastException = null;

    for (int attempt = 1; attempt <= retriesConfiguration.maxRetries(); attempt++) {
      try {
        if (attempt > 1) {
          final var retryDelay =
              retriesConfiguration
                  .initialRetryDelay()
                  .multipliedBy(Math.round(Math.pow(2, attempt - 2)));

          LOGGER.warn(
              "Retrying to fetch process definition XML for process definition key {}. Attempt {}/{}. Waiting for {}.",
              processDefinitionKey,
              attempt,
              retriesConfiguration.maxRetries(),
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

        return camundaClient.newProcessDefinitionGetXmlRequest(processDefinitionKey).send().join();
      } catch (ClientHttpException e) {
        lastException = e;
        if (attempt == retriesConfiguration.maxRetries()) {
          LOGGER.error(
              "Failed to retrieve process definition XML for process definition key {} after {} attempts",
              processDefinitionKey,
              attempt,
              e);
        }
      }
    }

    throw new ConnectorException(
        ERROR_CODE_AD_HOC_SUB_PROCESS_XML_FETCH_ERROR,
        "Failed to retrieve process definition XML with key %s after %d attempts: %s"
            .formatted(
                processDefinitionKey,
                retriesConfiguration.maxRetries(),
                lastException.getMessage()),
        lastException);
  }
}
