/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyListBuilder;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record McpClientToolsFilterConfiguration(
    @FEEL
        @TemplateProperty(
            group = "filters",
            label = "Included tools",
            description =
                "List of tools that can be used by the MCP client. By default, all tools are allowed.",
            type = TemplateProperty.PropertyType.Text,
            feel = FeelMode.required,
            optional = true)
        List<@NotBlank String> included,
    @FEEL
        @TemplateProperty(
            group = "filters",
            label = "Excluded tools",
            description =
                "List of tools that are not allowed to be used by the MCP client. Will override any included tools.",
            type = TemplateProperty.PropertyType.Text,
            feel = FeelMode.required,
            optional = true)
        List<@NotBlank String> excluded) {

  public AllowDenyList toAllowDenyList() {
    return AllowDenyListBuilder.builder().allowed(included).denied(excluded).build();
  }
}
