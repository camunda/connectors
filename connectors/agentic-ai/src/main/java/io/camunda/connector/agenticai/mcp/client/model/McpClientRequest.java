/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import io.camunda.connector.agenticai.mcp.client.model.McpClientRequest.McpClientRequestData.AnnotationsConfiguration;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record McpClientRequest(
    @Valid @NotNull AnnotationsConfiguration annotations,
    @Valid @NotNull McpClientRequestData data) {
  public record McpClientRequestData(
      @Valid @NotNull ClientConfiguration client,
      @Valid @NotNull OperationConfiguration operation) {

    public record AnnotationsConfiguration(
        @TemplateProperty(
                label = "Defines the element as MCP client",
                type = TemplateProperty.PropertyType.Hidden,
                feel = Property.FeelMode.disabled,
                defaultValue = "true")
            String mcpClient,
        @TemplateProperty(
                label = "MCP client integration version",
                type = TemplateProperty.PropertyType.Hidden,
                feel = Property.FeelMode.disabled,
                defaultValue = "0")
            String mcpClientSupportVersion) {}

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

    public record OperationConfiguration(
        @FEEL
            @TemplateProperty(
                group = "operation",
                label = "Method",
                description = "The MCP method to be called, e.g. <code>tools/list</code>",
                defaultValue = "tools/list",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                constraints = @PropertyConstraints(notEmpty = true))
            @NotBlank
            String method,
        @FEEL
            @TemplateProperty(
                group = "operation",
                label = "Parameters",
                feel = Property.FeelMode.required,
                optional = true)
            Map<String, Object> parameters) {}
  }
}
