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

public class ProcessVariableMemoryStore implements MemoryStore {
  @Override
  public void loadIntoMemory(AgentContext agentContext, ConversationMemory memory) {
    if (!(agentContext.memory() instanceof ProcessVariableMemoryData(List<Message> messages))) {
      throw new IllegalStateException(
          "Unsupported memory data: %s"
              .formatted(agentContext.memory().getClass().getSimpleName()));
    }

    memory.addMessages(messages);
  }

  @Override
  public AgentContext store(AgentContext agentContext, ConversationMemory memory) {
    return agentContext.withMemory(new ProcessVariableMemoryData(memory.messages()));
  }
}
