/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory;

import static io.camunda.connector.agenticai.util.JacksonExceptionMessageExtractor.humanReadableJsonProcessingExceptionMessage;
import static io.camunda.connector.agenticai.util.ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AgentContextChatMemoryStore implements ChatMemoryStore {

  private static final String DEFAULT_MEMORY_ID = "default";

  private final ChatMemoryStore internalMemoryStore;
  private final ObjectMapper objectMapper;
  private final Set<Object> memoryIds = ConcurrentHashMap.newKeySet();

  public AgentContextChatMemoryStore(ObjectMapper objectMapper) {
    this(new InMemoryChatMemoryStore(), objectMapper);
  }

  public AgentContextChatMemoryStore(
      ChatMemoryStore internalMemoryStore, ObjectMapper objectMapper) {
    this.internalMemoryStore = internalMemoryStore;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<ChatMessage> getMessages(Object memoryId) {
    return internalMemoryStore.getMessages(memoryId);
  }

  @Override
  public void updateMessages(Object memoryId, List<ChatMessage> messages) {
    memoryIds.add(memoryId);
    internalMemoryStore.updateMessages(memoryId, messages);
  }

  @Override
  public void deleteMessages(Object memoryId) {
    memoryIds.remove(memoryId);
    internalMemoryStore.deleteMessages(memoryId);
  }

  public void loadFromAgentContext(AgentContext agentContext) {
    loadFromAgentContext(agentContext, DEFAULT_MEMORY_ID);
  }

  public void loadFromAgentContext(AgentContext agentContext, Object memoryId) {
    final var messages = agentContext.memory().stream().map(this::deserializeChatMessage).toList();

    this.updateMessages(memoryId, messages);
  }

  public AgentContext storeToAgentContext(AgentContext agentContext) {
    return storeToAgentContext(agentContext, DEFAULT_MEMORY_ID);
  }

  public AgentContext storeToAgentContext(AgentContext agentContext, Object memoryId) {
    final var messages =
        this.getMessages(memoryId).stream().map(this::serializeChatMessage).toList();

    return agentContext.withMemory(messages);
  }

  private Map<String, Object> serializeChatMessage(ChatMessage chatMessage) {
    // step 1: convert to JSON string from LangChain4J internal structure
    String json = ChatMessageSerializer.messageToJson(chatMessage);

    // step 2: convert JSON string to map for better variable inspection
    try {
      return objectMapper.readValue(json, STRING_OBJECT_MAP_TYPE_REFERENCE);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "Failed to convert memory entry: " + humanReadableJsonProcessingExceptionMessage(e));
    }
  }

  private ChatMessage deserializeChatMessage(Map<String, Object> input) {
    // step 1: convert map to JSON string
    String json;
    try {
      json = objectMapper.writeValueAsString(input);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "Failed to read memory entry: " + humanReadableJsonProcessingExceptionMessage(e));
    }

    // step 2: convert JSON string to LangChain4J internal structure
    return ChatMessageDeserializer.messageFromJson(json);
  }
}
