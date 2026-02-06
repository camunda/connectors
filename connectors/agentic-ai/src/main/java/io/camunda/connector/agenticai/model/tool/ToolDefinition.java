/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.tool;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.mcp.client.model.Annotations;
import io.camunda.connector.agenticai.mcp.client.model.Icon;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

/** A tool definition, being a single tool available within the ad-hoc sub-process. */
@AgenticAiRecord
@JsonDeserialize(builder = ToolDefinition.ToolDefinitionJacksonProxyBuilder.class)
public record ToolDefinition(
    String name,
    @Nullable String description,
    Map<String, Object> inputSchema,
    @Nullable String title,
    @Nullable List<Icon> icons,
    @Nullable Map<String, Object> outputSchema,
    @Nullable Annotations annotations)
    implements ToolDefinitionBuilder.With {

  public static ToolDefinitionBuilder builder() {
    return ToolDefinitionBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolDefinitionJacksonProxyBuilder extends ToolDefinitionBuilder {}
}
