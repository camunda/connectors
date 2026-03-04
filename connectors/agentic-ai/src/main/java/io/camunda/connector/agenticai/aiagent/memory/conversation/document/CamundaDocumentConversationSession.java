/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.document;

import static io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationUtil.loadConversationContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationLoadResult;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.CamundaDocumentMemoryStorageConfiguration;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

public class CamundaDocumentConversationSession implements ConversationSession {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaDocumentConversationSession.class);

  private final CamundaDocumentMemoryStorageConfiguration config;
  private final DocumentFactory documentFactory;
  private final CamundaDocumentStore documentStore;
  private final CamundaDocumentConversationSerializer conversationSerializer;
  private final AgentExecutionContext executionContext;

  private long loadedVersion;
  private @Nullable String loadedConversationId;
  private @Nullable Document previousDocument;

  public CamundaDocumentConversationSession(
      CamundaDocumentMemoryStorageConfiguration config,
      DocumentFactory documentFactory,
      CamundaDocumentStore documentStore,
      CamundaDocumentConversationSerializer conversationSerializer,
      AgentExecutionContext executionContext) {
    this.config = config;
    this.documentFactory = documentFactory;
    this.documentStore = documentStore;
    this.conversationSerializer = conversationSerializer;
    this.executionContext = executionContext;
  }

  @Override
  public ConversationLoadResult loadMessages(AgentContext agentContext) {
    final var conversationContext =
        loadConversationContext(agentContext, ConversationContext.class);
    if (conversationContext == null) {
      return ConversationLoadResult.of(List.of());
    }

    loadedVersion = conversationContext.version();
    loadedConversationId = conversationContext.conversationId();

    if (conversationContext instanceof CamundaDocumentConversationContext documentContext) {
      previousDocument = documentContext.document();
      try {
        final var content = conversationSerializer.readDocumentContent(documentContext.document());
        return ConversationLoadResult.of(content.messages());
      } catch (IOException e) {
        throw new RuntimeException("Failed to load conversation from documentReference", e);
      }
    } else if (conversationContext instanceof InProcessConversationContext inProcessContext) {
      // Migration path: transparent in-process -> document migration
      return ConversationLoadResult.of(inProcessContext.messages());
    } else {
      throw new IllegalStateException(
          "Unsupported conversation context: %s"
              .formatted(conversationContext.getClass().getSimpleName()));
    }
  }

  @Override
  public AgentContext storeMessages(AgentContext agentContext, List<Message> messages) {
    final var conversationId =
        loadedConversationId != null ? loadedConversationId : UUID.randomUUID().toString();
    final long nextVersion = loadedVersion + 1;

    final var updatedDocument = createUpdatedDocument(messages, conversationId);

    final var conversationContext =
        CamundaDocumentConversationContext.builder()
            .conversationId(conversationId)
            .version(nextVersion)
            .document(updatedDocument)
            .build();

    return agentContext.withConversation(conversationContext);
  }

  @Override
  public void onJobCompleted(AgentContext agentContext) {
    if (previousDocument == null) {
      return;
    }

    if (previousDocument.reference()
        instanceof DocumentReference.CamundaDocumentReference camundaDocumentReference) {
      try {
        documentStore.deleteDocument(camundaDocumentReference);
      } catch (Exception e) {
        LOGGER.warn("Failed to delete previous document: {}", camundaDocumentReference, e);
      }
    } else {
      LOGGER.warn(
          "Unsupported document reference type: {}. Expected CamundaDocumentReference.",
          previousDocument.reference().getClass().getName());
    }
  }

  private Document createUpdatedDocument(List<Message> messages, String conversationId) {
    final var content = new CamundaDocumentConversationContext.DocumentContent(messages);

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
}
