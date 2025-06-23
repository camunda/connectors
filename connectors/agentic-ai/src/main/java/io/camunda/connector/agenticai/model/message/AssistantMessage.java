/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.message;

import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import java.util.List;
import java.util.Map;

@AgenticAiRecord
@JsonDeserialize(builder = AssistantMessage.AssistantMessageJacksonProxyBuilder.class)
public record AssistantMessage(
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Content> content,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ToolCall> toolCalls,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata)
    implements AssistantMessageBuilder.With, Message, ContentMessage {

  public boolean hasToolCalls() {
    return toolCalls != null && !toolCalls.isEmpty();
  }

  public static AssistantMessage assistantMessage(String text) {
    return builder().content(List.of(textContent(text))).build();
  }

  public static AssistantMessage assistantMessage(String text, List<ToolCall> toolCalls) {
    return builder().content(List.of(textContent(text))).toolCalls(toolCalls).build();
  }

  public static AssistantMessage assistantMessage(List<Content> content) {
    return builder().content(content).build();
  }

  public static AssistantMessageBuilder builder() {
    return AssistantMessageBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class AssistantMessageJacksonProxyBuilder extends AssistantMessageBuilder {}
}
