/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * <p>{@code kind} is the kind of the tool the call names, {@link ToolKind#PROCESS} when no tool has
 * the name. It is written to JSON only for a {@link ToolKind#RUNTIME} call; a missing value reads
 * as {@link ToolKind#PROCESS}.
 *
 * <p>{@code rejection} is the error text that is the tool call result of a call that must not be
 * carried out, such as the call of a tool that does not exist. It is written to JSON only when set.
 * Both are set once, when the agent admits the call.
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
        ToolKind kind,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String rejection)
    implements ToolCallBuilder.With {

  public static final ToolKind DEFAULT_KIND = ToolKind.PROCESS;

  /** A call as the model sent it, before the agent admits it. */
  public ToolCall(String id, String name, Map<String, Object> arguments) {
    this(id, name, arguments, null, DEFAULT_KIND, null);
  }

  /** A call as the model sent it, before the agent admits it. */
  public ToolCall(
      String id,
      String name,
      Map<String, Object> arguments,
      @Nullable Map<String, Object> metadata) {
    this(id, name, arguments, metadata, DEFAULT_KIND, null);
  }

  /** Who produces the tool call result of a call. */
  public enum ResultSource {
    /** The process: the call activates a BPMN element. */
    PROCESS,
    /** A runtime tool, executed by the agent. */
    RUNTIME,
    /** The agent, with the rejection as the result. The call is never carried out. */
    REJECTED
  }

  /** A rejection takes precedence over the kind. */
  @JsonIgnore
  public ResultSource resultSource() {
    if (rejection != null) {
      return ResultSource.REJECTED;
    }
    return switch (kind) {
      case PROCESS -> ResultSource.PROCESS;
      case RUNTIME -> ResultSource.RUNTIME;
    };
  }

  /** Whether the agent produces the tool call result itself: the process never gets the call. */
  @JsonIgnore
  public boolean isLocal() {
    return resultSource() != ResultSource.PROCESS;
  }

  /**
   * The call with the given arguments. Unlike {@link #withArguments}, it keeps a {@code null} value
   * in the arguments, which a model can send explicitly and the immutable copy of the builder
   * rejects.
   */
  public ToolCall withResolvedArguments(Map<String, Object> resolvedArguments) {
    return new ToolCall(id, name, resolvedArguments, metadata, kind, rejection);
  }

  public static ToolCallBuilder builder() {
    return ToolCallBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolCallJacksonProxyBuilder extends ToolCallBuilder {}
}
