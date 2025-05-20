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
import io.camunda.connector.agenticai.aiagent.model.message.content.ContentBlock;
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
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ContentBlock> content,
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
  public static class AssistantMessageJacksonProxyBuilder {
    private final AssistantMessageBuilder builder = AssistantMessageBuilder.builder();

    public AssistantMessageJacksonProxyBuilder stopReason(StopReason stopReason) {
      builder.stopReason(stopReason);
      return this;
    }

    public AssistantMessageJacksonProxyBuilder content(List<ContentBlock> content) {
      builder.content(content);
      return this;
    }

    public AssistantMessageJacksonProxyBuilder toolCallRequests(
        List<ToolCallRequest> toolCallRequests) {
      builder.toolCallRequests(toolCallRequests);
      return this;
    }

    public AssistantMessageJacksonProxyBuilder metadata(Map<String, Object> metadata) {
      builder.metadata(metadata);
      return this;
    }

    public AssistantMessage build() {
      return builder.build();
    }
  }

  public enum StopReason {
    STOP,
    LENGTH,
    TOOL_EXECUTION,
    CONTENT_FILTER,
    OTHER
  }
}
