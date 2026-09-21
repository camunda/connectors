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
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStoreImpl;
import io.camunda.connector.runtime.tenant.PhysicalTenantClientSelector;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaDocumentConversationStore implements ConversationStore {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaDocumentConversationStore.class);

  public static final String TYPE = "camunda-document";

  /**
   * Cache key of the cluster configured without a physical tenant. Not a possible tenant ID of its
   * own: a blank physical tenant is normalized to {@code null} both on a job and on a client.
   */
  private static final String NO_PHYSICAL_TENANT = "";

  private final DocumentFactory singleTenantDocumentFactory;
  private final CamundaDocumentStore singleTenantDocumentStore;
  private final PhysicalTenantClientSelector clientSelector;
  private final CamundaDocumentConversationSerializer conversationSerializer;
  private final Map<String, TenantDocuments> documentsByPhysicalTenantId =
      new ConcurrentHashMap<>();

  public CamundaDocumentConversationStore(
      DocumentFactory documentFactory,
      CamundaDocumentStore documentStore,
      PhysicalTenantClientSelector clientSelector,
      ObjectMapper objectMapper) {
    this.singleTenantDocumentFactory = documentFactory;
    this.singleTenantDocumentStore = documentStore;
    this.clientSelector = clientSelector;
    this.conversationSerializer = new CamundaDocumentConversationSerializer(objectMapper);
  }

  private record TenantDocuments(DocumentFactory factory, CamundaDocumentStore store) {}

  /**
   * Conversation memory documents have to be written to, read from and deleted on the cluster the
   * job runs against, so they are resolved per physical tenant rather than through the single
   * document beans picked at startup.
   *
   * <p>With one tenant the injected beans are used as they are, so overriding {@code
   * documentFactory}/{@code documentStore} (an in-memory store in tests, for instance) keeps
   * working. Applying such an override to every tenant of a genuine multi-cluster runtime would put
   * every tenant's memory through the same store instead of its own.
   *
   * <p>A cluster configured without a physical tenant can be served alongside tenant-scoped ones,
   * and its jobs carry no physical tenant either. Those are cached under {@link
   * #NO_PHYSICAL_TENANT} because the map rejects a null key, while the store itself still receives
   * the real, nullable ID — it is what the store checks a document's own physical tenant against.
   */
  private TenantDocuments documentsFor(AgentExecutionContext executionContext) {
    if (clientSelector.servesSinglePhysicalTenant()) {
      return new TenantDocuments(singleTenantDocumentFactory, singleTenantDocumentStore);
    }
    final @Nullable String physicalTenantId = executionContext.jobContext().getPhysicalTenantId();
    final var client = clientSelector.forPhysicalTenant(physicalTenantId);
    return documentsByPhysicalTenantId.computeIfAbsent(
        physicalTenantId == null ? NO_PHYSICAL_TENANT : physicalTenantId,
        cacheKey -> {
          var store = new CamundaDocumentStoreImpl(client, physicalTenantId);
          return new TenantDocuments(new DocumentFactoryImpl(store), store);
        });
  }

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public ConversationSession createSession(
      AgentExecutionContext executionContext, AgentContext agentContext) {
    final var config =
        Optional.ofNullable(executionContext.configuration().memory())
            .map(MemoryConfiguration::storage)
            .orElse(null);

    if (!(config instanceof CamundaDocumentMemoryStorageConfiguration documentConfig)) {
      throw new IllegalStateException(
          "Expected memory storage configuration to be of type CamundaDocumentMemoryStorageConfiguration, but got: %s"
              .formatted(config != null ? config.getClass().getName() : "null"));
    }

    final var documents = documentsFor(executionContext);
    return new CamundaDocumentConversationSession(
        documentConfig,
        documents.factory(),
        documents.store(),
        conversationSerializer,
        executionContext);
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
        documentsFor(executionContext).store().deleteDocument(camundaDocumentReference);
      } catch (Exception e) {
        LOGGER.warn(
            "Failed to delete orphaned document after job completion failure: {}",
            camundaDocumentReference,
            e);
      }
    }
  }
}
