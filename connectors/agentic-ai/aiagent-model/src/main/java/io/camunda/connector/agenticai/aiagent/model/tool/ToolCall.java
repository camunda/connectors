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
import io.camunda.connector.agenticai.common.annotation.AgenticAiRecord;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A call of a tool by the model.
 *
 * <p>{@code kind} is the class of tool the call targets. It is written to JSON only for a {@link
 * ToolKind#RUNTIME} call; a missing value reads as {@link ToolKind#PROCESS}.
 */
@AgenticAiRecord
@JsonDeserialize(builder = ToolCall.ToolCallJacksonProxyBuilder.class)
public record ToolCall(
    String id,
    String name,
    Map<String, Object> arguments,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> metadata,
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ProcessKindFilter.class)
        @RecordBuilder.Initializer("DEFAULT_KIND")
        ToolKind kind)
    implements ToolCallBuilder.With {

  public static final ToolKind DEFAULT_KIND = ToolKind.PROCESS;

  public ToolCall(String id, String name, Map<String, Object> arguments) {
    this(id, name, arguments, null, DEFAULT_KIND);
  }

  public ToolCall(
      String id,
      String name,
      Map<String, Object> arguments,
      @Nullable Map<String, Object> metadata) {
    this(id, name, arguments, metadata, DEFAULT_KIND);
  }

  public static ToolCallBuilder builder() {
    return ToolCallBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolCallJacksonProxyBuilder extends ToolCallBuilder {}

  /** Jackson value filter: a {@link ToolKind#PROCESS} kind is left out of the JSON. */
  static final class ProcessKindFilter {

    @Override
    public boolean equals(Object other) {
      return other == null || DEFAULT_KIND.equals(other);
    }

    @Override
    public int hashCode() {
      return DEFAULT_KIND.hashCode();
    }
  }
}
