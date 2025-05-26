/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.tools.ToolCallRequest;
import io.camunda.connector.agenticai.model.AgenticAiRecordBuilder;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

@AgenticAiRecordBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = AssistantMessage.AssistantMessageJacksonProxyBuilder.class)
public record AssistantMessage(
    @Nullable StopReason stopReason,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Content> content,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ToolCallRequest> toolCallRequests,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata)
    implements AssistantMessageBuilder.With, Message, ContentMessage {

  @Override
  public MessageRole role() {
    return MessageRole.ASSISTANT;
  }

  public static AssistantMessageBuilder builder() {
    return AssistantMessageBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class AssistantMessageJacksonProxyBuilder extends AssistantMessageBuilder {}

  public enum StopReason {
    STOP,
    LENGTH,
    TOOL_EXECUTION,
    CONTENT_FILTER,
    OTHER
  }
}
