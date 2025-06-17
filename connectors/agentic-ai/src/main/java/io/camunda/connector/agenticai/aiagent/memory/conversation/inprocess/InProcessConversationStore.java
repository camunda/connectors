/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess;

import io.camunda.connector.agenticai.aiagent.memory.conversation.BaseConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.util.UUID;

public class InProcessConversationStore
    extends BaseConversationStore<InProcessConversationContext> {

  @Override
  public Class<InProcessConversationContext> conversationContextClass() {
    return InProcessConversationContext.class;
  }

  @Override
  public void loadIntoRuntimeMemory(
      OutboundConnectorContext context, AgentContext agentContext, RuntimeMemory memory) {
    final var previousConversationContext = loadPreviousConversationContext(agentContext);
    if (previousConversationContext == null) {
      return;
    }

    memory.addMessages(previousConversationContext.messages());
  }

  @Override
  public AgentContext storeFromRuntimeMemory(
      OutboundConnectorContext context, AgentContext agentContext, RuntimeMemory memory) {
    final var previousConversationContext = loadPreviousConversationContext(agentContext);
    final var conversationContextBuilder =
        previousConversationContext != null
            ? previousConversationContext.with()
            : InProcessConversationContext.builder().id(UUID.randomUUID().toString());

    final var conversationContext =
        conversationContextBuilder.messages(memory.allMessages()).build();
    return agentContext.withConversation(conversationContext);
  }
}
