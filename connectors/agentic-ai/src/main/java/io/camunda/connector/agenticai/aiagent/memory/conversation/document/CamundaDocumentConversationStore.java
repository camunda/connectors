/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.camunda.connector.agenticai.aiagent.memory.conversation.BaseConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationContext.DocumentContent;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.CamundaDocumentMemoryStorageConfiguration;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.document.Document;
import io.camunda.document.reference.DocumentReference.CamundaDocumentReference;
import io.camunda.document.store.CamundaDocumentStore;
import io.camunda.document.store.DocumentCreationRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaDocumentConversationStore
    extends BaseConversationStore<CamundaDocumentConversationContext> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaDocumentConversationStore.class);
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
  public void loadIntoRuntimeMemory(
      OutboundConnectorContext context, AgentContext agentContext, RuntimeMemory memory) {
    final var previousConversationContext = loadPreviousConversationContext(agentContext);
    if (previousConversationContext == null) {
      return;
    }

    try {
      final var content =
          objectMapper.readValue(
              previousConversationContext.document().asInputStream(), DocumentContent.class);
      memory.addMessages(content.messages());
    } catch (IOException e) {
      throw new RuntimeException("Failed to load conversation from documentReference", e);
    }
  }

  @Override
  public AgentContext storeFromRuntimeMemory(
      OutboundConnectorContext context, AgentContext agentContext, RuntimeMemory memory) {
    final var previousConversationContext = loadPreviousConversationContext(agentContext);
    final var conversationContextBuilder =
        previousConversationContext != null
            ? previousConversationContext.with()
            : CamundaDocumentConversationContext.builder()
                .conversationId(UUID.randomUUID().toString());

    final var updatedDocument =
        createUpdatedDocument(context, memory, conversationContextBuilder.conversationId());
    conversationContextBuilder.document(updatedDocument);

    // after write succeeded, try to purge previous documents, but keep at least the last
    // two documents in case of errors in order to allow recovering the agent state
    if (previousConversationContext != null) {
      List<Document> previousDocuments =
          new ArrayList<>(previousConversationContext.previousDocuments());
      previousDocuments.add(previousConversationContext.document());

      conversationContextBuilder.previousDocuments(purgePreviousDocuments(previousDocuments));
    }

    return agentContext.withConversation(conversationContextBuilder.build());
  }

  private Document createUpdatedDocument(
      OutboundConnectorContext context, RuntimeMemory memory, String conversationId) {
    final var content = new DocumentContent(memory.allMessages());

    String serialized;
    try {
      serialized = objectWriter.writeValueAsString(content);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize conversation", e);
    }

    final var properties = new LinkedHashMap<String, Object>();
    Optional.ofNullable(config.customProperties()).ifPresent(properties::putAll);
    properties.put("conversationId", conversationId);

    final var documentCreationRequestBuilder =
        DocumentCreationRequest.from(
                new ByteArrayInputStream(serialized.getBytes(StandardCharsets.UTF_8)))
            .processDefinitionId(context.getJobContext().getBpmnProcessId())
            .processInstanceKey(context.getJobContext().getProcessInstanceKey())
            .contentType("application/json")
            .fileName("%s_conversation.json".formatted(context.getJobContext().getElementId()))
            .customProperties(properties);

    Optional.ofNullable(config.timeToLive()).ifPresent(documentCreationRequestBuilder::timeToLive);

    return context.create(documentCreationRequestBuilder.build());
  }

  private List<Document> purgePreviousDocuments(List<Document> previousDocuments) {
    if (previousDocuments.size() <= previousDocumentsRetentionSize) {
      return previousDocuments;
    }

    final var updatedPreviousDocuments = new ArrayList<>(previousDocuments);
    final var removalCandidates =
        new ArrayList<>(
            updatedPreviousDocuments.subList(
                0, updatedPreviousDocuments.size() - previousDocumentsRetentionSize));

    for (Document removalCandidate : removalCandidates) {
      if (removalCandidate.reference()
          instanceof CamundaDocumentReference camundaDocumentReference) {
        try {
          documentStore.deleteDocument(camundaDocumentReference);
          updatedPreviousDocuments.remove(removalCandidate);
        } catch (Exception e) {
          LOGGER.warn("Failed to delete previous document: {}", camundaDocumentReference, e);
        }
      } else {
        LOGGER.warn(
            "Unsupported document reference type: {}. Expected CamundaDocumentReference.",
            removalCandidate.reference().getClass().getName());
      }
    }

    return updatedPreviousDocuments;
  }
}
