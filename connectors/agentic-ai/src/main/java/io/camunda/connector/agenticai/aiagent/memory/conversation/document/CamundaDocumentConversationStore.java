/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSessionHandler;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.CamundaDocumentMemoryStorageConfiguration;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.document.store.CamundaDocumentStore;

public class CamundaDocumentConversationStore implements ConversationStore {

  private final CamundaDocumentMemoryStorageConfiguration config;
  private final CamundaDocumentStore documentStore;
  private final ObjectMapper objectMapper;
  private final ObjectWriter objectWriter;

  public CamundaDocumentConversationStore(
      CamundaDocumentMemoryStorageConfiguration config,
      CamundaDocumentStore documentStore,
      ObjectMapper objectMapper) {
    this.config = config;
    this.documentStore = documentStore;
    this.objectMapper = objectMapper;
    this.objectWriter = objectMapper.writerWithDefaultPrettyPrinter();
  }

  @Override
  public <T> T executeInSession(
      OutboundConnectorContext context,
      AgentRequest request,
      AgentContext agentContext,
      ConversationSessionHandler<T> sessionHandler) {
    return sessionHandler.handleSession(createSession(context));
  }

  private CamundaDocumentConversationSession createSession(OutboundConnectorContext context) {
    return new CamundaDocumentConversationSession(
        config, context, documentStore, objectMapper, objectWriter, context.getJobContext());
  }
}
