/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common;

import io.a2a.spec.AgentCard;
import io.camunda.connector.agenticai.a2a.client.common.model.A2aConnectionConfiguration;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aAgentCard;

public interface A2aAgentCardFetcher {
  A2aAgentCard fetchAgentCard(A2aConnectionConfiguration connection);

  AgentCard fetchAgentCardRaw(A2aConnectionConfiguration connection);
}
