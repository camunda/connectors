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
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A tool definition, being a single tool offered to the model.
 *
 * <p>{@code kind} is the kind of tool offered; the agent instance history reads it. It is written
 * to JSON only for a {@link ToolKind#RUNTIME} definition; a missing value reads as {@link
 * ToolKind#PROCESS}.
 *
 * <p>A parameter that carries a document has the schema {@link DocumentParameter#documentSchema}.
 */
@AgenticAiRecord
@JsonDeserialize(builder = ToolDefinition.ToolDefinitionJacksonProxyBuilder.class)
public record ToolDefinition(
    String name,
    @Nullable String description,
    Map<String, Object> inputSchema,
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ProcessKindFilter.class)
        @RecordBuilder.Initializer("DEFAULT_KIND")
        ToolKind kind)
    implements ToolDefinitionBuilder.With {

  public static final ToolKind DEFAULT_KIND = ToolKind.PROCESS;

  public ToolDefinition(
      String name, @Nullable String description, Map<String, Object> inputSchema) {
    this(name, description, inputSchema, DEFAULT_KIND);
  }

  public static ToolDefinition runtime(
      String name, @Nullable String description, Map<String, Object> inputSchema) {
    return new ToolDefinition(name, description, inputSchema, ToolKind.RUNTIME);
  }

  /** The parameters of the input schema that carry a document, in schema order. */
  @JsonIgnore
  public List<DocumentParameter> documentParameters() {
    return DocumentParameter.findAll(inputSchema);
  }

  public static ToolDefinitionBuilder builder() {
    return ToolDefinitionBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolDefinitionJacksonProxyBuilder extends ToolDefinitionBuilder {}
}
