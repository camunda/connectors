/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.conversation.AgentConversation;

/**
 * Handles the creation of an agent response based on the configuration and the returned assistant
 * message.
 *
 * <p>Depending on the configuration, this handler takes care of parsing the response text into the
 * desired format (such as JSON).
 */
public interface AgentResponseHandler {

  /**
   * Creates the response for the last assistant message of the conversation. Its tool calls are the
   * process tool calls of that message: the runtime tool calls are executed by the agent and are
   * not handed to the process.
   */
  AgentResponse createResponse(AgentConversation conversation);
}
