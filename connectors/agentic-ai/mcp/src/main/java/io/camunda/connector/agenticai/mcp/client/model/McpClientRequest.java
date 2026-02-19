/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.lang.Nullable;

public record McpClientRequest(@Valid @NotNull McpClientRequestData data) {
  public record McpClientRequestData(
      @Valid @NotNull ClientConfiguration client,
      @Valid @NotNull McpConnectorModeConfiguration connectorMode) {

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
    public static McpClientRequestData create(
        @JsonProperty("client") @Valid @NotNull ClientConfiguration client,
        @JsonProperty("connectorMode") @Valid @NotNull McpConnectorModeConfiguration connectorMode,
        @JsonProperty("tools") @Valid @Nullable McpClientToolsFilterConfiguration tools) {

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

      return new McpClientRequestData(client, targetConnectorMode);
    }

    public record ClientConfiguration(
        @TemplateProperty(
                group = "client",
                label = "Client ID",
                description =
                    "The MCP client ID. This needs to be configured on your connector runtime.",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
                constraints = @PropertyConstraints(notEmpty = true))
            @NotBlank
            String clientId) {}
  }
}
