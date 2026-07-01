/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.outbound;

import io.a2a.spec.AgentCard;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aStandaloneOperationConfiguration.SendMessageOperationConfiguration;

public interface A2aMessageSender {
  A2aSendMessageResult sendMessage(
      AgentCard agentCard, SendMessageOperationConfiguration sendMessageOperation);
}
