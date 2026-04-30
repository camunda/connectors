/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.document;

import static io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationUtil.loadConversationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.CamundaDocumentMemoryStorageConfiguration;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaDocumentConversationStore implements ConversationStore {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaDocumentConversationStore.class);

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
  public ConversationSession createSession(
      AgentExecutionContext executionContext, AgentContext agentContext) {
    final var config =
        Optional.ofNullable(executionContext.memory())
            .map(MemoryConfiguration::storage)
            .orElse(null);

    if (!(config instanceof CamundaDocumentMemoryStorageConfiguration documentConfig)) {
      throw new IllegalStateException(
          "Expected memory storage configuration to be of type CamundaDocumentMemoryStorageConfiguration, but got: %s"
              .formatted(config != null ? config.getClass().getName() : "null"));
    }

    return new CamundaDocumentConversationSession(
        documentConfig, documentFactory, documentStore, conversationSerializer, executionContext);
  }

  @Override
  public void onJobCompletionFailed(
      AgentExecutionContext executionContext,
      AgentContext failedContext,
      JobCompletionFailure failure) {
    var ctx = loadConversationContext(failedContext, CamundaDocumentConversationContext.class);
    if (ctx == null) {
      return;
    }

    // ctx.document() is the document written by storeMessages during this job — it became
    // orphaned because Zeebe rejected the job completion, so no pointer will ever reference it
    var document = ctx.document();
    if (document.reference() instanceof CamundaDocumentReference camundaDocumentReference) {
      try {
        documentStore.deleteDocument(camundaDocumentReference);
      } catch (Exception e) {
        LOGGER.warn(
            "Failed to delete orphaned document after job completion failure: {}",
            camundaDocumentReference,
            e);
      }
    }
  }
}
