/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess;

import static io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationUtil.loadConversationContext;

import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import java.util.UUID;

public class InProcessConversationSession implements ConversationSession {

  private InProcessConversationContext previousConversationContext;

  @Override
  public void loadIntoRuntimeMemory(AgentContext agentContext, RuntimeMemory memory) {
    previousConversationContext =
        loadConversationContext(agentContext, InProcessConversationContext.class);
    if (previousConversationContext == null) {
      return;
    }

    memory.addMessages(previousConversationContext.messages());
  }

  @Override
  public AgentContext storeFromRuntimeMemory(AgentContext agentContext, RuntimeMemory memory) {
    final var conversationContextBuilder =
        previousConversationContext != null
            ? previousConversationContext.with()
            : InProcessConversationContext.builder().conversationId(UUID.randomUUID().toString());

    final var conversationContext =
        conversationContextBuilder.messages(memory.allMessages()).build();

    return agentContext.withConversation(conversationContext);
  }
}
