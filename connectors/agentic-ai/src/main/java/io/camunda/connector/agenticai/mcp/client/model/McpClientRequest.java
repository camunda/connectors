/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.lang.Nullable;

public record McpClientRequest(@Valid @NotNull McpClientRequestData data) {
  public record McpClientRequestData(
      @Valid @NotNull ClientConfiguration client,
      @Valid @NotNull McpConnectorModeConfiguration connectorMode,
      @Valid @Nullable McpClientToolsConfiguration tools) {

    public record ClientConfiguration(
        @TemplateProperty(
                group = "client",
                label = "Client ID",
                description =
                    "The MCP client ID. This needs to be configured on your connector runtime.",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                constraints = @PropertyConstraints(notEmpty = true))
            @NotBlank
            String clientId) {}
  }
}
