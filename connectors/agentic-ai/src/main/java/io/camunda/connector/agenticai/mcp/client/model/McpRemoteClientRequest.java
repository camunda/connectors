/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record McpRemoteClientRequest(@Valid @NotNull McpRemoteClientRequestData data) {
  public record McpRemoteClientRequestData(
      @Valid @NotNull McpRemoteClientTransportConfiguration transport,
      @Valid McpRemoteClientOptionsConfiguration options,
      @Valid @NotNull McpConnectorModeConfiguration connectorMode,
      @FEEL
          @TemplateProperty(
              group = "meta",
              label = "Metadata (_meta)",
              description =
                  "Optional metadata passed with tool calls as the MCP _meta field. Supports FEEL expressions for dynamic values.",
              feel = FeelMode.required,
              optional = true)
          @Nullable Map<String, Object> meta) {

    /**
     * Creator method, especially for deserialization, for constructing McpClientRequestData from
     * deprecated structure, where filters are only applicable for MCP tools and not for other
     * connector modes, thus the filter being on this level instead of inside the connector mode.
     *
     * @deprecated This is only used to ensure element templates of version below 2 are still
     *     supported
     */
    @JsonCreator
    @Deprecated(forRemoval = true)
    public static McpRemoteClientRequestData create(
        @JsonProperty("transport") @Valid @NotNull McpRemoteClientTransportConfiguration transport,
        @JsonProperty("options") @Valid McpRemoteClientOptionsConfiguration options,
        @JsonProperty("connectorMode") @Valid @NotNull McpConnectorModeConfiguration connectorMode,
        @JsonProperty("tools") @Valid @Nullable McpClientToolsFilterConfiguration tools,
        @JsonProperty("meta") @Nullable Map<String, Object> meta) {
      var targetConnectorMode =
          tools == null
              ? connectorMode
              : switch (connectorMode) {
                case McpConnectorModeConfiguration.StandaloneModeConfiguration
                        standaloneModeConfiguration ->
                    new McpConnectorModeConfiguration.StandaloneModeConfiguration(
                        standaloneModeConfiguration.operation(),
                        new McpClientStandaloneFiltersConfiguration(tools, null, null));
                case McpConnectorModeConfiguration.ToolModeConfiguration toolModeConfiguration ->
                    new McpConnectorModeConfiguration.ToolModeConfiguration(
                        toolModeConfiguration.toolOperation(),
                        new McpClientToolModeFiltersConfiguration(tools));
              };

      return new McpRemoteClientRequestData(transport, options, targetConnectorMode, meta);
    }
  }
}
