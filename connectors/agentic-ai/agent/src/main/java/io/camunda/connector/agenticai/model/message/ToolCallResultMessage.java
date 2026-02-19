/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;
import java.util.Map;

@AgenticAiRecord
@JsonDeserialize(builder = ToolCallResultMessage.ToolCallResultMessageJacksonProxyBuilder.class)
public record ToolCallResultMessage(
    List<ToolCallResult> results,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata)
    implements ToolCallResultMessageBuilder.With, Message {

  public static ToolCallResultMessageBuilder builder() {
    return ToolCallResultMessageBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolCallResultMessageJacksonProxyBuilder
      extends ToolCallResultMessageBuilder {}
}
