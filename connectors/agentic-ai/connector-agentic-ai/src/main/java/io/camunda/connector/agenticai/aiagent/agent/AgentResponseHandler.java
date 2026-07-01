/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;

/**
 * Handles the creation of an agent response based on the configuration and the returned assistant
 * message.
 *
 * <p>Depending on the configuration, this handler takes care of parsing the response text into the
 * desired format (such as JSON).
 */
public interface AgentResponseHandler {
  AgentResponse createResponse(AgentConversation conversation);
}
