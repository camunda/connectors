/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.document;

import static io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationUtil.loadConversationContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.CamundaDocumentMemoryStorageConfiguration;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.document.store.CamundaDocumentStore;
import io.camunda.connector.api.document.DocumentCreationRequest;
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

public class CamundaDocumentConversationSession implements ConversationSession {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaDocumentConversationSession.class);

  private static final int DEFAULT_PREVIOUS_DOCUMENTS_RETENTION_SIZE = 2;

  private final CamundaDocumentMemoryStorageConfiguration config;
  private final DocumentFactory documentFactory;
  private final CamundaDocumentStore documentStore;
  private final CamundaDocumentConversationSerializer conversationSerializer;
  private final AgentExecutionContext executionContext;
  private final int previousDocumentsRetentionSize;

  private CamundaDocumentConversationContext previousConversationContext;

  public CamundaDocumentConversationSession(
      CamundaDocumentMemoryStorageConfiguration config,
      DocumentFactory documentFactory,
      CamundaDocumentStore documentStore,
      CamundaDocumentConversationSerializer conversationSerializer,
      AgentExecutionContext executionContext) {
    this(
        config,
        documentFactory,
        documentStore,
        conversationSerializer,
        executionContext,
        DEFAULT_PREVIOUS_DOCUMENTS_RETENTION_SIZE);
  }

  public CamundaDocumentConversationSession(
      CamundaDocumentMemoryStorageConfiguration config,
      DocumentFactory documentFactory,
      CamundaDocumentStore documentStore,
      CamundaDocumentConversationSerializer conversationSerializer,
      AgentExecutionContext executionContext,
      int previousDocumentsRetentionSize) {
    this.config = config;
    this.documentFactory = documentFactory;
    this.documentStore = documentStore;
    this.conversationSerializer = conversationSerializer;
    this.executionContext = executionContext;
    this.previousDocumentsRetentionSize = previousDocumentsRetentionSize;
  }

  @Override
  public void loadIntoRuntimeMemory(AgentContext agentContext, RuntimeMemory memory) {
    previousConversationContext =
        loadConversationContext(agentContext, CamundaDocumentConversationContext.class);
    if (previousConversationContext == null) {
      return;
    }

    try {
      final var content =
          conversationSerializer.readDocumentContent(previousConversationContext.document());
      memory.addMessages(content.messages());
    } catch (IOException e) {
      throw new RuntimeException("Failed to load conversation from documentReference", e);
    }
  }

  @Override
  public AgentContext storeFromRuntimeMemory(AgentContext agentContext, RuntimeMemory memory) {
    final var conversationContextBuilder =
        previousConversationContext != null
            ? previousConversationContext.with()
            : CamundaDocumentConversationContext.builder()
                .conversationId(UUID.randomUUID().toString());

    final var updatedDocument =
        createUpdatedDocument(memory, conversationContextBuilder.conversationId());
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

  private Document createUpdatedDocument(RuntimeMemory memory, String conversationId) {
    final var content =
        new CamundaDocumentConversationContext.DocumentContent(memory.allMessages());

    String serialized;
    try {
      serialized = conversationSerializer.writeDocumentContent(content);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize conversation", e);
    }

    final var properties = new LinkedHashMap<String, Object>();
    Optional.ofNullable(config.customProperties()).ifPresent(properties::putAll);
    properties.put("conversationId", conversationId);

    final var jobContext = executionContext.jobContext();
    final var documentCreationRequestBuilder =
        DocumentCreationRequest.from(
                new ByteArrayInputStream(serialized.getBytes(StandardCharsets.UTF_8)))
            .processDefinitionId(jobContext.bpmnProcessId())
            .processInstanceKey(jobContext.processInstanceKey())
            .contentType("application/json")
            .fileName("%s_conversation.json".formatted(jobContext.elementId()))
            .customProperties(properties);

    Optional.ofNullable(config.timeToLive()).ifPresent(documentCreationRequestBuilder::timeToLive);

    return documentFactory.create(documentCreationRequestBuilder.build());
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
          instanceof DocumentReference.CamundaDocumentReference camundaDocumentReference) {
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
