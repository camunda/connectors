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
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = ToolCallResult.ToolCallResultJacksonProxyBuilder.class)
public record ToolCallResult(String id, String name, Map<String, Object> data)
    implements ToolCallResultBuilder.With {

  public ToolCallResult {
    data = Objects.requireNonNullElseGet(data, Map::of);
  }

  // Additional constructors with default values
  public ToolCallResult(String id, String name) {
    this(id, name, Map.of());
  }

  public ToolCallResult(String id) {
    this(id, "", Map.of());
  }

  public ToolCallResult() {
    this("", "", Map.of());
  }

  public static ToolCallResultBuilder builder() {
    return ToolCallResultBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolCallResultJacksonProxyBuilder {
    private final ToolCallResultBuilder builder = ToolCallResultBuilder.builder();

    public ToolCallResultJacksonProxyBuilder id(String id) {
      builder.id(id);
      return this;
    }

    public ToolCallResultJacksonProxyBuilder name(String name) {
      builder.name(name);
      return this;
    }

    public ToolCallResultJacksonProxyBuilder data(Map<String, Object> data) {
      builder.data(data);
      return this;
    }

    public ToolCallResult build() {
      return builder.build();
    }
  }
}
