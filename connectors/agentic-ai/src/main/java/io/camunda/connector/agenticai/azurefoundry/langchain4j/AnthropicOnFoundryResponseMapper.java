/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry.langchain4j;

import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.StopReason;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps an Anthropic SDK {@link Message} to a langchain4j {@link ChatResponse}.
 *
 * <p>Stateless — safe for concurrent use.
 */
class AnthropicOnFoundryResponseMapper {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  ChatResponse toChatResponse(Message message) {
    String text = null;
    List<ToolExecutionRequest> toolRequests = new ArrayList<>();

    for (ContentBlock block : message.content()) {
      if (block.isText()) {
        String blockText = block.asText().text();
        text = (text == null) ? blockText : text + blockText;
      } else if (block.isToolUse()) {
        toolRequests.add(buildToolExecutionRequest(block));
      }
      // Other block types (thinking, server tools, etc.) are skipped
    }

    AiMessage aiMessage =
        toolRequests.isEmpty()
            ? new AiMessage(text != null ? text : "")
            : new AiMessage(text, toolRequests);

    FinishReason finishReason = mapStopReason(message.stopReason().orElse(null));
    TokenUsage tokenUsage = mapTokenUsage(message.usage());

    return ChatResponse.builder()
        .aiMessage(aiMessage)
        .finishReason(finishReason)
        .tokenUsage(tokenUsage)
        .id(message.id())
        .modelName(message.model().toString())
        .build();
  }

  private ToolExecutionRequest buildToolExecutionRequest(ContentBlock block) {
    var toolUse = block.asToolUse();
    // _input() returns JsonValue; convert it to a JSON string for langchain4j
    String argumentsJson;
    try {
      argumentsJson = OBJECT_MAPPER.writeValueAsString(toolUse._input().convert(Object.class));
    } catch (JsonProcessingException e) {
      argumentsJson = "{}";
    }
    return ToolExecutionRequest.builder()
        .id(toolUse.id())
        .name(toolUse.name())
        .arguments(argumentsJson)
        .build();
  }

  private FinishReason mapStopReason(StopReason stopReason) {
    if (stopReason == null) {
      return FinishReason.OTHER;
    }
    if (stopReason.equals(StopReason.END_TURN) || stopReason.equals(StopReason.STOP_SEQUENCE)) {
      return FinishReason.STOP;
    }
    if (stopReason.equals(StopReason.TOOL_USE)) {
      return FinishReason.TOOL_EXECUTION;
    }
    if (stopReason.equals(StopReason.MAX_TOKENS)) {
      return FinishReason.LENGTH;
    }
    return FinishReason.OTHER;
  }

  private TokenUsage mapTokenUsage(com.anthropic.models.messages.Usage usage) {
    int input = (int) usage.inputTokens();
    int output = (int) usage.outputTokens();
    return new TokenUsage(input, output, input + output);
  }
}
