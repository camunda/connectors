/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record AgentResponse(
    AgentContext context, Map<String, Object> chatResponse, List<ToolCall> toolCalls) {
  public record ToolCall(
      @JsonProperty("_meta") ToolCallMetadata metadata,
      @JsonAnySetter @JsonAnyGetter Map<String, Object> arguments) {

    public ToolCall(String id, String name, Map<String, Object> arguments) {
      this(new ToolCallMetadata(id, name), arguments);
    }

    public record ToolCallMetadata(String id, String name) {}
  }
}
