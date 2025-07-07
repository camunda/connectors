/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess;

import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreSession;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import java.util.UUID;

public class InProcessConversationStoreSession
    implements ConversationStoreSession<InProcessConversationContext> {

  private final InProcessConversationContext previousConversationContext;

  public InProcessConversationStoreSession(
      InProcessConversationContext previousConversationContext) {
    this.previousConversationContext = previousConversationContext;
  }

  @Override
  public void loadIntoRuntimeMemory(RuntimeMemory memory) {
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
