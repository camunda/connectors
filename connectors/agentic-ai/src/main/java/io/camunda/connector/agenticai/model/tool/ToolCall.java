/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.tool;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import java.util.Map;
import javax.annotation.Nullable;

@AgenticAiRecord
@JsonDeserialize(builder = ToolCall.ToolCallJacksonProxyBuilder.class)
public record ToolCall(@Nullable String id, String name, Map<String, Object> arguments)
    implements ToolCallBuilder.With {

  public static ToolCallBuilder builder() {
    return ToolCallBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolCallJacksonProxyBuilder extends ToolCallBuilder {}
}
