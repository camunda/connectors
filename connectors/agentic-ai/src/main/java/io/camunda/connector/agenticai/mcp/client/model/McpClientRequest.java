/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

public record McpClientRequest(@Valid @NotNull McpClientRequestData data) {
  public record McpClientRequestData(
      @Valid @NotNull ClientConfiguration client,
      @Valid @Nullable ToolsConfiguration tools,
      @Valid @NotNull OperationConfiguration operation) {

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

    public record ToolsConfiguration(
        @FEEL
            @TemplateProperty(
                group = "tools",
                label = "Included Tools",
                description =
                    "Allow-listed tools that can be used by the MCP client. By default, all tools are allowed.",
                type = TemplateProperty.PropertyType.Text,
                feel = Property.FeelMode.required,
                optional = true)
            List<String> included,
        @FEEL
            @TemplateProperty(
                group = "tools",
                label = "Excluded Tools",
                description =
                    "Tools that are not allowed to be used by the MCP client. Will override any included tools.",
                type = TemplateProperty.PropertyType.Text,
                feel = Property.FeelMode.required,
                optional = true)
            List<String> excluded) {}

    public record OperationConfiguration(
        @FEEL
            @TemplateProperty(
                group = "operation",
                label = "Method",
                description = "The MCP method to be called, e.g. <code>tools/list</code>",
                defaultValue = "=toolCall.method",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                constraints = @PropertyConstraints(notEmpty = true))
            @NotBlank
            String method,
        @FEEL
            @TemplateProperty(
                group = "operation",
                label = "Parameters",
                description = "The parameters to be passed to the MCP method.",
                defaultValue = "=toolCall.params",
                feel = Property.FeelMode.required,
                optional = true)
            Map<String, Object> params) {}
  }
}
