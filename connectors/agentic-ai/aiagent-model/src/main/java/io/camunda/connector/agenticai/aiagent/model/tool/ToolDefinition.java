/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.common.annotation.AgenticAiRecord;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A tool definition, being a single tool available within the ad-hoc sub-process.
 *
 * <p>A parameter that carries a document has the string schema with the format {@code
 * camunda-document-id} (see {@link DocumentParameterSchema}). The stored schema keeps that format
 * and does not hold the description hint the model gets for such a parameter; the model providers
 * get the schema with the hint and without the format, which {@link
 * DocumentParameterSchema#toProviderToolDefinitions} derives where the conversation snapshot is
 * built.
 */
@AgenticAiRecord
@JsonDeserialize(builder = ToolDefinition.ToolDefinitionJacksonProxyBuilder.class)
public record ToolDefinition(
    String name, @Nullable String description, Map<String, Object> inputSchema)
    implements ToolDefinitionBuilder.With {

  public static ToolDefinitionBuilder builder() {
    return ToolDefinitionBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolDefinitionJacksonProxyBuilder extends ToolDefinitionBuilder {}
}
