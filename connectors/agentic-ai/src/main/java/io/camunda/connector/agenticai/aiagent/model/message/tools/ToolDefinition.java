/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecordBuilder;
import java.util.Map;
import java.util.Objects;

@AgenticAiRecordBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = ToolDefinition.ToolDefinitionJacksonProxyBuilder.class)
public record ToolDefinition(String name, String description, Map<String, Object> inputSchema)
    implements ToolDefinitionBuilder.With {

  public ToolDefinition {
    inputSchema = Objects.requireNonNullElseGet(inputSchema, Map::of);
  }

  // Additional constructors with default values
  public ToolDefinition(String name, String description) {
    this(name, description, Map.of());
  }

  public ToolDefinition(String name) {
    this(name, "", Map.of());
  }

  public ToolDefinition() {
    this("", "", Map.of());
  }

  public static ToolDefinitionBuilder builder() {
    return ToolDefinitionBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolDefinitionJacksonProxyBuilder {
    private final ToolDefinitionBuilder builder = ToolDefinitionBuilder.builder();

    public ToolDefinitionJacksonProxyBuilder name(String name) {
      builder.name(name);
      return this;
    }

    public ToolDefinitionJacksonProxyBuilder description(String description) {
      builder.description(description);
      return this;
    }

    public ToolDefinitionJacksonProxyBuilder inputSchema(Map<String, Object> inputSchema) {
      builder.inputSchema(inputSchema);
      return this;
    }

    public ToolDefinition build() {
      return builder.build();
    }
  }
}
