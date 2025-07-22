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
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.CamundaDocumentMemoryStorageConfiguration;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.document.store.CamundaDocumentStore;
import java.util.Optional;

public class CamundaDocumentConversationStore implements ConversationStore {

  public static final String TYPE = "camunda-document";

  private final DocumentFactory documentFactory;
  private final CamundaDocumentStore documentStore;
  private final CamundaDocumentConversationSerializer conversationSerializer;

  public CamundaDocumentConversationStore(
      DocumentFactory documentFactory,
      CamundaDocumentStore documentStore,
      ObjectMapper objectMapper) {
    this.documentFactory = documentFactory;
    this.documentStore = documentStore;
    this.conversationSerializer = new CamundaDocumentConversationSerializer(objectMapper);
  }

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public <T> T executeInSession(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      ConversationSessionHandler<T> sessionHandler) {
    final var config =
        Optional.ofNullable(executionContext.request().data())
            .map(AgentRequest.AgentRequestData::memory)
            .map(AgentRequest.AgentRequestData.MemoryConfiguration::storage)
            .orElse(null);

    if (!(config instanceof CamundaDocumentMemoryStorageConfiguration documentConfig)) {
      throw new IllegalStateException(
          "Expected memory storage configuration to be of type CamundaDocumentMemoryStorageConfiguration, but got: %s"
              .formatted(config != null ? config.getClass().getName() : "null"));
    }

    final var session =
        new CamundaDocumentConversationSession(
            documentConfig,
            documentFactory,
            documentStore,
            conversationSerializer,
            executionContext);

    return sessionHandler.handleSession(session);
  }
}
