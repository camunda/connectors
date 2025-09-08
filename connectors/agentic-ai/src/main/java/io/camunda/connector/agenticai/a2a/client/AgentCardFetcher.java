/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client;

import io.a2a.spec.AgentCard;
import io.camunda.connector.agenticai.a2a.client.model.A2aClientRequest.A2aClientRequestData.ConnectionConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aAgentCardResult;

public interface AgentCardFetcher {
  A2aAgentCardResult fetchAgentCard(ConnectionConfiguration connection);

  AgentCard fetchAgentCardRaw(ConnectionConfiguration connection);
}
