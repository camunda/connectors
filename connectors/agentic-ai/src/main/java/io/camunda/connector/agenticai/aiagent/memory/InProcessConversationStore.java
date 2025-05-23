/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory;

import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.model.message.Message;
import java.util.List;

public class InProcessConversationStore implements ConversationStore<InProcessConversationContext> {

  @Override
  public Class<InProcessConversationContext> conversationContextClass() {
    return InProcessConversationContext.class;
  }

  @Override
  public void loadIntoRuntimeMemory(ConversationContext conversationContext, RuntimeMemory memory) {
    if (conversationContext == null) {
      return;
    }

    if (!(conversationContext instanceof InProcessConversationContext(List<Message> messages))) {
      throw new IllegalStateException(
          "Unsupported conversation context: %s"
              .formatted(conversationContext.getClass().getSimpleName()));
    }

    memory.addMessages(messages);
  }

  @Override
  public ConversationContext store(ConversationContext conversationContext, RuntimeMemory memory) {
    return new InProcessConversationContext(memory.allMessages());
  }
}
