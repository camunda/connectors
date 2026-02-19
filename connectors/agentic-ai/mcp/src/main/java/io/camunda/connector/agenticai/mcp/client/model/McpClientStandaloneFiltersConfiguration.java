/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;

public record McpClientStandaloneFiltersConfiguration(
    @NestedProperties(
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "data.connectorMode.operation.type",
                    oneOf = {"tools/call", "tools/list"}))
        @Valid
        McpClientToolsFilterConfiguration tools,
    @NestedProperties(
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "data.connectorMode.operation.type",
                    oneOf = {"resources/read", "resources/list", "resources/templates/list"}))
        @Valid
        McpClientResourcesFilterConfiguration resources,
    @NestedProperties(
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "data.connectorMode.operation.type",
                    oneOf = {"prompts/get", "prompts/list"}))
        @Valid
        McpClientPromptsFilterConfiguration prompts) {}
