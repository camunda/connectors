/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.processdefinition;

import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.RetriesProperties;
import io.camunda.connector.agenticai.common.util.retry.CamundaApiRetry;
import io.camunda.connector.agenticai.common.util.retry.CamundaApiRetry.FailureReason;
import io.camunda.connector.agenticai.common.util.retry.CamundaApiRetry.Sleeper;
import io.camunda.connector.agenticai.common.util.retry.ErrorClassifier;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.runtime.tenant.PhysicalTenantClientSelector;
import org.jspecify.annotations.Nullable;

public class ProcessDefinitionClient {
  private static final String ERROR_CODE_AD_HOC_SUB_PROCESS_XML_FETCH_ERROR =
      "AD_HOC_SUB_PROCESS_XML_FETCH_ERROR";

  private final PhysicalTenantClientSelector clientSelector;
  private final RetriesProperties retriesProperties;

  public ProcessDefinitionClient(
      PhysicalTenantClientSelector clientSelector, RetriesProperties retriesProperties) {
    this.clientSelector = clientSelector;
    this.retriesProperties = retriesProperties;
  }

  /**
   * Reads the definition XML from the cluster serving {@code physicalTenantId}. Definition keys are
   * only unique within one cluster, so reading through any other one would resolve a different
   * definition, or none at all.
   */
  public String getProcessDefinitionXml(
      @Nullable String physicalTenantId, Long processDefinitionKey) {
    // resolved outside the retry: an unroutable physical tenant is a configuration error, and this
    // classifier retries every exception, so leaving it inside would burn the whole backoff on a
    // mapping no attempt can change
    final var camundaClient = clientSelector.forPhysicalTenant(physicalTenantId);
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
