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
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A tool definition, being a single tool available within the ad-hoc sub-process.
 *
 * <p>{@code documentParameters} lists the parameters of the input schema that carry a document
 * reference, as JSON pointers into the input schema (e.g. {@code /properties/doc} or {@code
 * /properties/docs/items}). It is agent-internal metadata and is never part of the schema sent to a
 * model provider.
 */
@AgenticAiRecord
@JsonDeserialize(builder = ToolDefinition.ToolDefinitionJacksonProxyBuilder.class)
public record ToolDefinition(
    String name,
    @Nullable String description,
    Map<String, Object> inputSchema,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> documentParameters)
    implements ToolDefinitionBuilder.With {

  public ToolDefinition {
    documentParameters = documentParameters == null ? List.of() : List.copyOf(documentParameters);
  }

  public static ToolDefinitionBuilder builder() {
    return ToolDefinitionBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolDefinitionJacksonProxyBuilder extends ToolDefinitionBuilder {}
}
