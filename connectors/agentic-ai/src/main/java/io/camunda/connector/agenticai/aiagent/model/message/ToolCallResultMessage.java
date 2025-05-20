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
import io.camunda.connector.agenticai.aiagent.model.message.tools.ToolCallResult;
import io.camunda.connector.agenticai.model.AgenticAiRecordBuilder;
import java.util.List;
import java.util.Map;

@AgenticAiRecordBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = ToolCallResultMessage.ToolCallResultMessageJacksonProxyBuilder.class)
public record ToolCallResultMessage(
    List<ToolCallResult> results,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata)
    implements ToolCallResultMessageBuilder.With, Message {

  @Override
  public MessageRole role() {
    return MessageRole.TOOL_CALL_RESULT;
  }

  public static ToolCallResultMessageBuilder builder() {
    return ToolCallResultMessageBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolCallResultMessageJacksonProxyBuilder {
    private final ToolCallResultMessageBuilder builder = ToolCallResultMessageBuilder.builder();

    public ToolCallResultMessageJacksonProxyBuilder results(List<ToolCallResult> results) {
      builder.results(results);
      return this;
    }

    public ToolCallResultMessageJacksonProxyBuilder metadata(Map<String, Object> metadata) {
      builder.metadata(metadata);
      return this;
    }

    public ToolCallResultMessage build() {
      return builder.build();
    }
  }
}
