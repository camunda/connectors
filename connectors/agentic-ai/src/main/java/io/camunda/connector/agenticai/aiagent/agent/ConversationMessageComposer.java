/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;

/**
 * Composes this turn's messages into the conversation. Returns a new {@link AgentConversation} with
 * the system message upserted and the user/tool/event messages for this turn appended and frozen as
 * {@code addedMessages}.
 */
public interface ConversationMessageComposer {
  AgentConversation compose(AgentExecutionContext executionContext, AgentConversation conversation);
}
