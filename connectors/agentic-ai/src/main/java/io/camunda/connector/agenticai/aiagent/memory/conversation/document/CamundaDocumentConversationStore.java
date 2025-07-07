/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.camunda.connector.agenticai.aiagent.memory.conversation.BaseConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreSession;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.CamundaDocumentMemoryStorageConfiguration;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.document.store.CamundaDocumentStore;

public class CamundaDocumentConversationStore
    extends BaseConversationStore<CamundaDocumentConversationContext> {
  private static final int DEFAULT_PREVIOUS_DOCUMENTS_RETENTION_SIZE = 2;

  private final CamundaDocumentMemoryStorageConfiguration config;
  private final CamundaDocumentStore documentStore;
  private final ObjectMapper objectMapper;
  private final ObjectWriter objectWriter;
  private final int previousDocumentsRetentionSize;

  public CamundaDocumentConversationStore(
      CamundaDocumentMemoryStorageConfiguration config,
      CamundaDocumentStore documentStore,
      ObjectMapper objectMapper) {
    this(config, documentStore, objectMapper, DEFAULT_PREVIOUS_DOCUMENTS_RETENTION_SIZE);
  }

  public CamundaDocumentConversationStore(
      CamundaDocumentMemoryStorageConfiguration config,
      CamundaDocumentStore documentStore,
      ObjectMapper objectMapper,
      int previousDocumentsRetentionSize) {
    this.config = config;
    this.documentStore = documentStore;
    this.objectMapper = objectMapper;
    this.objectWriter = objectMapper.writerWithDefaultPrettyPrinter();
    this.previousDocumentsRetentionSize = previousDocumentsRetentionSize;
  }

  @Override
  public Class<CamundaDocumentConversationContext> conversationContextClass() {
    return CamundaDocumentConversationContext.class;
  }

  @Override
  protected ConversationStoreSession<CamundaDocumentConversationContext> createSession(
      OutboundConnectorContext context,
      AgentContext agentContext,
      CamundaDocumentConversationContext previousConversationContext) {
    return new CamundaDocumentConversationStoreSession(
        config,
        context,
        documentStore,
        objectMapper,
        objectWriter,
        context.getJobContext(),
        previousDocumentsRetentionSize,
        previousConversationContext);
  }
}
