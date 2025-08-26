/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.tool;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Tool call process variable representation which moves arguments to the upper level and id/name to
 * a "_meta" object.
 *
 * <p>This is needed to allow accessing tool call arguments from the root of the object in
 * fromAi(toolCall.myParameter) vs. fromAi(toolCall.arguments.myParameter).
 */
public record ToolCallProcessVariable(
    @JsonProperty("_meta") ToolCallMetadata metadata,
    @JsonAnySetter @JsonAnyGetter Map<String, Object> arguments) {

  public ToolCallProcessVariable(String id, String name, Map<String, Object> arguments) {
    this(new ToolCallMetadata(id, name), arguments);
  }

  public static ToolCallProcessVariable from(ToolCall toolCall) {
    return new ToolCallProcessVariable(toolCall.id(), toolCall.name(), toolCall.arguments());
  }

  public record ToolCallMetadata(String id, String name) {}
}
