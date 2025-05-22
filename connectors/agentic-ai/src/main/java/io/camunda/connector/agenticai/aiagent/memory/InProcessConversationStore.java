/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.model.message.Message;
import java.util.List;

public class InProcessConversationStore implements ConversationStore<InProcessConversationRecord> {

  @Override
  public Class<InProcessConversationRecord> conversationRecordClass() {
    return InProcessConversationRecord.class;
  }

  @Override
  public void loadFromContext(AgentContext agentContext, ConversationMemory memory) {
    if (agentContext.conversation() == null) {
      return;
    }

    if (!(agentContext.conversation()
        instanceof InProcessConversationRecord(List<Message> messages))) {
      throw new IllegalStateException(
          "Unsupported conversation record: %s"
              .formatted(agentContext.conversation().getClass().getSimpleName()));
    }

    memory.addMessages(messages);
  }

  @Override
  public ConversationRecord store(AgentContext agentContext, ConversationMemory memory) {
    return new InProcessConversationRecord(memory.allMessages());
  }
}
