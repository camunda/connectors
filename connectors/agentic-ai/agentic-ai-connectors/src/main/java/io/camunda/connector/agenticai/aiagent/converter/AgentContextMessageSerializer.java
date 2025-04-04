/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.converter;

import static io.camunda.connector.agenticai.mapping.JacksonExceptionMessageExtractor.humanReadableJsonProcessingExceptionMessage;
import static io.camunda.connector.agenticai.mapping.ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;

public class AgentContextMessageSerializer {

  private final ObjectMapper objectMapper;

  public AgentContextMessageSerializer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<ChatMessage> loadFromAgentContext(AgentContext agentContext) {
    return agentContext.history().stream().map(this::deserializeChatMessage).toList();
  }

  public List<Map<String, Object>> asAgentContextHistory(List<ChatMessage> chatMessages) {
    return chatMessages.stream().map(this::serializeChatMessage).toList();
  }

  private Map<String, Object> serializeChatMessage(ChatMessage chatMessage) {
    // step 1: convert to JSON string from LangChain4J internal structure
    String json = ChatMessageSerializer.messageToJson(chatMessage);

    // step 2: convert JSON string to map
    try {
      return objectMapper.readValue(json, STRING_OBJECT_MAP_TYPE_REFERENCE);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "Failed to convert history entry: " + humanReadableJsonProcessingExceptionMessage(e));
    }
  }

  private ChatMessage deserializeChatMessage(Map<String, Object> input) {
    // step 1: convert map to JSON string
    String json;
    try {
      json = objectMapper.writeValueAsString(input);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "Failed to read history entry: " + humanReadableJsonProcessingExceptionMessage(e));
    }

    // step 2: convert JSON string to LangChain4J internal structure
    return ChatMessageDeserializer.messageFromJson(json);
  }
}
