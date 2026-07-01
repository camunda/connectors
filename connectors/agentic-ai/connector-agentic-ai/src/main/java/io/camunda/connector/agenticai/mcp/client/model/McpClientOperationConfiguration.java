/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record McpClientOperationConfiguration(
    @FEEL
        @TemplateProperty(
            group = "operation",
            label = "Method",
            description = "The MCP method to be called, e.g. <code>tools/list</code>.",
            tooltip =
                "The method to be called on the MCP server. See the <a href=\"https://modelcontextprotocol.io/specification/2024-11-05/server\">MCP specification</a> for a list of available methods.<br><br>Currently supported:<br><code>tools/list</code>, <code>tools/call</code>",
            defaultValue = "=toolCall.method",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotBlank
        String method,
    @FEEL
        @TemplateProperty(
            group = "operation",
            label = "Parameters",
            description = "The parameters to be passed to the MCP method.",
            tooltip =
                "The parameter structure depends on the method being called. See the <a href=\"https://modelcontextprotocol.io/specification/2024-11-05/server/tools#calling-tools\">MCP specification</a> for an example of the parameters for the <code>tools/call</code> method.",
            defaultValue = "=toolCall.params",
            feel = FeelMode.required,
            optional = true)
        Map<String, Object> params) {}
