/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.common.AgenticAiRecord;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;
import java.util.Map;

@AgenticAiRecord
@JsonDeserialize(builder = ToolCallResultMessage.ToolCallResultMessageJacksonProxyBuilder.class)
public record ToolCallResultMessage(
    @RecordBuilder.Initializer(source = MessageUtil.class, value = "generateId") String id,
    List<ToolCallResultContent> results,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata)
    implements ToolCallResultMessageBuilder.With, Message {

  public static ToolCallResultMessageBuilder builder() {
    return ToolCallResultMessageBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolCallResultMessageJacksonProxyBuilder
      extends ToolCallResultMessageBuilder {}
}
