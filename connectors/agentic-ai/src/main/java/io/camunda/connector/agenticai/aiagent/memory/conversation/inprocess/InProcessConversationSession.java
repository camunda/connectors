/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess;

import static io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationUtil.loadConversationContext;

import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationLoadResult;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRequest;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import java.util.UUID;

public class InProcessConversationSession implements ConversationSession {

  private InProcessConversationContext previousConversationContext;

  @Override
  public ConversationLoadResult loadMessages(AgentContext agentContext) {
    previousConversationContext =
        loadConversationContext(agentContext, InProcessConversationContext.class);
    if (previousConversationContext == null) {
      return ConversationLoadResult.empty();
    }

    return ConversationLoadResult.of(previousConversationContext.messages());
  }

  @Override
  public ConversationContext storeMessages(
      AgentContext agentContext, ConversationStoreRequest request) {
    final var conversationContextBuilder =
        previousConversationContext != null
            ? previousConversationContext.with()
            : InProcessConversationContext.builder().conversationId(UUID.randomUUID().toString());

    return conversationContextBuilder.messages(request.messages()).build();
  }
}
