/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.util.List;

/**
 * Handles the creation of an agent response based on the configuration and the returned assistant
 * message.
 *
 * <p>Depending on the configuration, this handler takes care of parsing the response text into the
 * desired format (such as JSON).
 */
public interface AgentResponseHandler {

  /**
   * Creates the response for the last assistant message of the conversation; all its tool calls go
   * to the process.
   */
  AgentResponse createResponse(AgentConversation conversation);

  /**
   * Creates the response for the last assistant message of the conversation.
   *
   * @param processToolCalls the tool calls of the assistant message that go to the process; the
   *     other calls of the message are answered by the agent itself
   */
  AgentResponse createResponse(AgentConversation conversation, List<ToolCall> processToolCalls);
}
