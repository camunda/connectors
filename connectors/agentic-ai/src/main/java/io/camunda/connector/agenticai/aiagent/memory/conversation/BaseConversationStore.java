/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.api.outbound.OutboundConnectorContext;

public abstract class BaseConversationStore<C extends ConversationContext>
    implements ConversationStore<C> {
  public abstract Class<C> conversationContextClass();

  @Override
  public AgentResponse executeInSession(
      OutboundConnectorContext context,
      AgentContext agentContext,
      ConversationStoreSessionHandler<C> handler) {
    final var previousConversationContext = loadPreviousConversationContext(agentContext);
    final var session = createSession(context, agentContext, previousConversationContext);
    return handler.apply(session);
  }

  protected C loadPreviousConversationContext(AgentContext agentContext) {
    final var conversationContext = agentContext.conversation();
    if (agentContext.conversation() == null) {
      return null;
    }

    final var expectedContextClass = conversationContextClass();
    final var actualContextClass = conversationContext.getClass();

    if (!expectedContextClass.isAssignableFrom(actualContextClass)) {
      throw new IllegalStateException(
          "Unsupported conversation context: %s".formatted(actualContextClass.getSimpleName()));
    }

    return expectedContextClass.cast(conversationContext);
  }

  protected abstract ConversationStoreSession<C> createSession(
      OutboundConnectorContext context, AgentContext agentContext, C previousConversationContext);
}
