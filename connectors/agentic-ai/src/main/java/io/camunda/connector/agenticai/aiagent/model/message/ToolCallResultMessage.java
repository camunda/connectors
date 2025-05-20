/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.connector.agenticai.aiagent.model.message.tools.ToolCallResult;
import io.camunda.connector.agenticai.model.AgenticAiRecordBuilder;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@AgenticAiRecordBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolCallResultMessage(
    List<ToolCallResult> results,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata)
    implements ToolCallResultMessageBuilder.With, Message {

  public ToolCallResultMessage {
    results = Objects.requireNonNullElseGet(results, List::of);
    metadata = Objects.requireNonNullElseGet(metadata, Map::of);
  }

  @Override
  public MessageRole role() {
    return MessageRole.TOOL_CALL_RESULT;
  }

  public static ToolCallResultMessageBuilder builder() {
    return ToolCallResultMessageBuilder.builder();
  }
}
