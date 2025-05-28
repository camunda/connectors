/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * A gateway tool definition, being an entrypoint for multiple tools. Tool discovery needs to be
 * done at runtime.
 *
 * <p>Example use case: MCP Client is a gateway to multiple tools exposed by the connected MCP
 * server
 */
@AgenticAiRecord
@JsonDeserialize(builder = GatewayToolDefinition.GatewayToolDefinitionJacksonProxyBuilder.class)
public record GatewayToolDefinition(
    String type,
    String name,
    @Nullable String description,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> properties)
    implements GatewayToolDefinitionBuilder.With {

  public static GatewayToolDefinitionBuilder builder() {
    return GatewayToolDefinitionBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class GatewayToolDefinitionJacksonProxyBuilder
      extends GatewayToolDefinitionBuilder {}
}
