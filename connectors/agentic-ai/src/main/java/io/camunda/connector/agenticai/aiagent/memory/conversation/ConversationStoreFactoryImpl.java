/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.CamundaDocumentMemoryStorageConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.InProcessMemoryStorageConfiguration;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.document.store.CamundaDocumentStore;
import java.util.Optional;

public class ConversationStoreFactoryImpl implements ConversationStoreFactory {

  private final ObjectMapper objectMapper;
  private final DocumentFactory documentFactory;
  private final CamundaDocumentStore camundaDocumentStore;

  public ConversationStoreFactoryImpl(
      ObjectMapper objectMapper,
      DocumentFactory documentFactory,
      CamundaDocumentStore camundaDocumentStore) {
    this.objectMapper = objectMapper;
    this.documentFactory = documentFactory;
    this.camundaDocumentStore = camundaDocumentStore;
  }

  @Override
  public ConversationStore createConversationStore(
      final AgentExecutionContext executionContext, final AgentContext agentContext) {
    final var storageConfig =
        Optional.ofNullable(executionContext.request().data().memory())
            .map(AgentRequest.AgentRequestData.MemoryConfiguration::storage)
            .orElseGet(InProcessMemoryStorageConfiguration::new);

    return switch (storageConfig) {
      case InProcessMemoryStorageConfiguration ignored -> new InProcessConversationStore();
      case CamundaDocumentMemoryStorageConfiguration camundaDocumentConfig ->
          new CamundaDocumentConversationStore(
              camundaDocumentConfig, documentFactory, camundaDocumentStore, objectMapper);
    };
  }
}
