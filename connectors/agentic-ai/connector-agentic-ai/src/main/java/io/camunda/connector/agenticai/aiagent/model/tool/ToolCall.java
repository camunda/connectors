/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.common.AgenticAiRecord;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@AgenticAiRecord
@JsonDeserialize(builder = ToolCall.ToolCallJacksonProxyBuilder.class)
public record ToolCall(
    String id,
    String name,
    Map<String, Object> arguments,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> metadata)
    implements ToolCallBuilder.With {

  public ToolCall(String id, String name, Map<String, Object> arguments) {
    this(id, name, arguments, null);
  }

  /**
   * The call with the given arguments. Keeps a {@code null} value in the arguments, which a model
   * can send explicitly and the immutable copy of the generated wither rejects.
   */
  @Override
  public ToolCall withArguments(Map<String, Object> arguments) {
    return new ToolCall(
        id, name, Collections.unmodifiableMap(new LinkedHashMap<>(arguments)), metadata);
  }

  public static ToolCallBuilder builder() {
    return new ToolCallJacksonProxyBuilder();
  }

  /**
   * The builder of {@link ToolCall#builder()} and of a call that is read from JSON. Keeps a {@code
   * null} value in the arguments, which a model can send explicitly and the immutable copy of the
   * generated builder rejects. Arguments that are not set or are {@code null} build as an empty
   * map.
   */
  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolCallJacksonProxyBuilder extends ToolCallBuilder {
    private @Nullable Map<String, Object> arguments;

    @Override
    public ToolCallBuilder arguments(@Nullable Map<String, Object> arguments) {
      this.arguments = arguments;
      return this;
    }

    @Override
    public Map<String, Object> arguments() {
      return arguments == null ? Map.of() : arguments;
    }

    @Override
    public ToolCall build() {
      return super.build().withArguments(arguments());
    }
  }
}
