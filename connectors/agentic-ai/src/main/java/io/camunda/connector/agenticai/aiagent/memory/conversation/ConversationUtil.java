/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import java.util.Optional;

public class ConversationUtil {

  public static <C extends ConversationContext> C loadConversationContext(
      AgentContext agentContext, Class<C> contextClass) {
    final var conversationContext =
        Optional.ofNullable(agentContext).map(AgentContext::conversation).orElse(null);
    if (conversationContext == null) {
      return null;
    }

    final var actualContextClass = conversationContext.getClass();
    if (!contextClass.isAssignableFrom(actualContextClass)) {
      throw new IllegalStateException(
          "Unsupported conversation context: %s".formatted(actualContextClass.getSimpleName()));
    }

    return contextClass.cast(conversationContext);
  }
}
