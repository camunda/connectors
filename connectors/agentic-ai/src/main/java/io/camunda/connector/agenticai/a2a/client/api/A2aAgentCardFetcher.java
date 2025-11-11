/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.api;

import io.a2a.spec.AgentCard;
import io.camunda.connector.agenticai.a2a.client.model.A2aRequest.A2aRequestData.ConnectionConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aAgentCard;

public interface A2aAgentCardFetcher {
  A2aAgentCard fetchAgentCard(ConnectionConfiguration connection);

  AgentCard fetchAgentCardRaw(ConnectionConfiguration connection);
}
