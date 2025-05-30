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
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record McpClientOperationConfiguration(
    @FEEL
        @TemplateProperty(
            group = "operation",
            label = "Method",
            description = "The MCP method to be called, e.g. <code>tools/list</code>",
            defaultValue = "=toolCall.method",
            type = TemplateProperty.PropertyType.String,
            feel = Property.FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
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
