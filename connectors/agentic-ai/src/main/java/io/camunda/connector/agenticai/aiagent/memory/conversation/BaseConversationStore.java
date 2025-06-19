/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;

public abstract class BaseConversationStore<C extends ConversationContext>
    implements ConversationStore {
  public abstract Class<C> conversationContextClass();

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
}
