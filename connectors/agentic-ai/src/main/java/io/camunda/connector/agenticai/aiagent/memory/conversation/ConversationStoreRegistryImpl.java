/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.InProcessMemoryStorageConfiguration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConversationStoreRegistryImpl implements ConversationStoreRegistry {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConversationStoreRegistryImpl.class);

  private final Map<String, ConversationStore> conversationStores = new HashMap<>();

  public ConversationStoreRegistryImpl() {}

  public ConversationStoreRegistryImpl(List<ConversationStore> conversationStores) {
    conversationStores.forEach(this::registerConversationStore);
  }

  public void registerConversationStore(final ConversationStore conversationStore) {
    final var type = conversationStore.type();
    if (conversationStores.containsKey(type)) {
      throw new IllegalArgumentException(
          "Conversation store with type '%s' is already registered.".formatted(type));
    }

    LOGGER.debug("Registering conversation store of type '{}'", type);

    conversationStores.put(type, conversationStore);
  }

  @Override
  public ConversationStore getConversationStore(
      final AgentExecutionContext executionContext, final AgentContext agentContext) {
    final var storageConfig =
        Optional.ofNullable(executionContext.memory())
            .map(AgentRequest.AgentRequestData.MemoryConfiguration::storage)
            .orElseGet(InProcessMemoryStorageConfiguration::new);

    final var conversationStore = conversationStores.get(storageConfig.storeType());
    if (conversationStore == null) {
      throw new IllegalStateException(
          "No conversation store registered for storage configuration type: %s"
              .formatted(storageConfig.storeType()));
    }

    return conversationStore;
  }
}
