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
import io.camunda.connector.agenticai.common.AgenticAiRecord;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** A tool definition, being a single tool available within the ad-hoc sub-process. */
@AgenticAiRecord
@JsonDeserialize(builder = ToolDefinition.ToolDefinitionJacksonProxyBuilder.class)
public record ToolDefinition(
    String name,
    @Nullable String description,
    Map<String, Object> inputSchema,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata)
    implements ToolDefinitionBuilder.With {

  /** Metadata key marking a tool definition as a sandbox gateway tool. */
  public static final String METADATA_SANDBOX_TOOL = "sandbox";

  /** Metadata key carrying the target BPMN element id a sandbox tool call routes to. */
  public static final String METADATA_ELEMENT_ID = "elementId";

  public ToolDefinition {
    metadata = (metadata != null) ? metadata : Map.of();
  }

  @JsonIgnore
  public boolean isSandboxTool() {
    return Boolean.TRUE.equals(metadata().get(METADATA_SANDBOX_TOOL));
  }

  public static ToolDefinitionBuilder builder() {
    return ToolDefinitionBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolDefinitionJacksonProxyBuilder extends ToolDefinitionBuilder {}
}
