/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSessionHandler;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.CamundaDocumentMemoryStorageConfiguration;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.document.store.CamundaDocumentStore;

public class CamundaDocumentConversationStore implements ConversationStore {

  private final CamundaDocumentMemoryStorageConfiguration config;
  private final DocumentFactory documentFactory;
  private final CamundaDocumentStore documentStore;
  private final CamundaDocumentConversationSerializer conversationSerializer;

  public CamundaDocumentConversationStore(
      CamundaDocumentMemoryStorageConfiguration config,
      DocumentFactory documentFactory,
      CamundaDocumentStore documentStore,
      ObjectMapper objectMapper) {
    this.config = config;
    this.documentFactory = documentFactory;
    this.documentStore = documentStore;
    this.conversationSerializer = new CamundaDocumentConversationSerializer(objectMapper);
  }

  @Override
  public <T> T executeInSession(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      ConversationSessionHandler<T> sessionHandler) {
    final var session =
        new CamundaDocumentConversationSession(
            config, documentFactory, documentStore, conversationSerializer, executionContext);

    return sessionHandler.handleSession(session);
  }
}
