/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.connector.agenticai.model.AgenticAiRecordBuilder;
import java.util.Map;
import java.util.Objects;

@AgenticAiRecordBuilder
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolCallResult(String id, String name, Map<String, Object> data)
    implements ToolCallResultBuilder.With {

  public ToolCallResult {
    data = Objects.requireNonNullElseGet(data, Map::of);
  }

  public static ToolCallResultBuilder builder() {
    return ToolCallResultBuilder.builder();
  }
}
