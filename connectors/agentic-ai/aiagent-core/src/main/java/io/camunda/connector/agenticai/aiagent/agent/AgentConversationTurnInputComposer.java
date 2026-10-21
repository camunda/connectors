/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.conversation.AgentInput;
import io.camunda.connector.agenticai.aiagent.conversation.PreviousConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;

/** Composes the next conversation turn input from raw history and the current invocation state. */
public interface AgentConversationTurnInputComposer {
  CompositionResult compose(
      AgentConfiguration configuration,
      AgentContext agentContext,
      PreviousConversation previousConversation,
      AgentInput agentInput);
}
