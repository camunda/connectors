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
@JsonDeserialize(builder = ToolCallRequest.ToolCallRequestJacksonProxyBuilder.class)
public record ToolCallRequest(String id, String name, Map<String, Object> arguments)
    implements ToolCallRequestBuilder.With {

  public ToolCallRequest {
    arguments = Objects.requireNonNullElseGet(arguments, Map::of);
  }

  // Additional constructors with default values
  public ToolCallRequest(String id, String name) {
    this(id, name, Map.of());
  }

  public ToolCallRequest(String id) {
    this(id, "", Map.of());
  }

  public ToolCallRequest() {
    this("", "", Map.of());
  }

  public static ToolCallRequestBuilder builder() {
    return ToolCallRequestBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolCallRequestJacksonProxyBuilder {
    private final ToolCallRequestBuilder builder = ToolCallRequestBuilder.builder();

    public ToolCallRequestJacksonProxyBuilder id(String id) {
      builder.id(id);
      return this;
    }

    public ToolCallRequestJacksonProxyBuilder name(String name) {
      builder.name(name);
      return this;
    }

    public ToolCallRequestJacksonProxyBuilder arguments(Map<String, Object> arguments) {
      builder.arguments(arguments);
      return this;
    }

    public ToolCallRequest build() {
      return builder.build();
    }
  }
}
