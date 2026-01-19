/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import static io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.StandaloneModeConfiguration.STANDALONE_ID;
import static io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.ToolModeConfiguration.AI_AGENT_TOOL_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.filters.FilterOptions;
import io.camunda.connector.agenticai.mcp.client.filters.FilterOptionsBuilder;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.collections4.MapUtils;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = McpConnectorModeConfiguration.ToolModeConfiguration.class,
      name = AI_AGENT_TOOL_ID),
  @JsonSubTypes.Type(
      value = McpConnectorModeConfiguration.StandaloneModeConfiguration.class,
      name = STANDALONE_ID)
})
@TemplateDiscriminatorProperty(
    group = "connectorMode",
    label = "Connector mode",
    name = "type",
    description = "Select how this connector is used.",
    defaultValue = AI_AGENT_TOOL_ID)
public sealed interface McpConnectorModeConfiguration
    permits McpConnectorModeConfiguration.StandaloneModeConfiguration,
        McpConnectorModeConfiguration.ToolModeConfiguration {

  McpClientOperation toMcpClientOperation();

  Optional<FilterOptions> createFilterOptions();

  @TemplateSubType(id = AI_AGENT_TOOL_ID, label = "AI Agent tool")
  record ToolModeConfiguration(
      @Valid @NotNull McpClientOperationConfiguration toolOperation,
      @Valid McpClientToolModeFiltersConfiguration toolModeFilters)
      implements McpConnectorModeConfiguration {

    @TemplateProperty(ignore = true)
    public static final String AI_AGENT_TOOL_ID = "aiAgentTool";

    @Override
    public McpClientOperation toMcpClientOperation() {
      return MapUtils.isEmpty(toolOperation.params())
          ? McpClientOperation.of(toolOperation.method())
          : McpClientOperation.of(toolOperation.method(), toolOperation.params());
    }

    @Override
    public Optional<FilterOptions> createFilterOptions() {
      var result =
          toolModeFilters == null
              ? null
              : FilterOptionsBuilder.builder()
                  .toolFilters(
                      toolModeFilters.tools() != null
                          ? toolModeFilters.tools().toAllowDenyList()
                          : AllowDenyList.allowingEverything())
                  .build();

      return Optional.ofNullable(result);
    }
  }

  @TemplateSubType(id = STANDALONE_ID, label = "Standalone")
  record StandaloneModeConfiguration(
      @Valid @NotNull McpStandaloneOperationConfiguration operation,
      @Valid McpClientStandaloneFiltersConfiguration standaloneModeFilters)
      implements McpConnectorModeConfiguration {

    @TemplateProperty(ignore = true)
    public static final String STANDALONE_ID = "standalone";

    @Override
    public McpClientOperation toMcpClientOperation() {
      return McpClientOperation.of(operation.method(), operation.params().orElseGet(Map::of));
    }

    @Override
    public Optional<FilterOptions> createFilterOptions() {
      var result =
          standaloneModeFilters == null
              ? null
              : FilterOptionsBuilder.builder()
                  .toolFilters(
                      standaloneModeFilters.tools() != null
                          ? standaloneModeFilters.tools().toAllowDenyList()
                          : AllowDenyList.allowingEverything())
                  .resourceFilters(
                      standaloneModeFilters.resources() != null
                          ? standaloneModeFilters.resources().toAllowDenyList()
                          : AllowDenyList.allowingEverything())
                  .promptFilters(
                      standaloneModeFilters.prompts() != null
                          ? standaloneModeFilters.prompts().toAllowDenyList()
                          : AllowDenyList.allowingEverything())
                  .build();

      return Optional.ofNullable(result);
    }
  }
}
