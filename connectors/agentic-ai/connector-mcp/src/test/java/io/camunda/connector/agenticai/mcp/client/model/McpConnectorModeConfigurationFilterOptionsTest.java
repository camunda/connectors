/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.StandaloneModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.ToolModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.ListToolsOperationConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpConnectorModeConfigurationFilterOptionsTest {

  private static final AllowDenyList EVERYTHING = AllowDenyList.allowingEverything();

  @Test
  void toolMode_mapsIncludedAndExcludedToolsToAllowedAndDenied() {
    final var filters =
        new McpClientToolModeFiltersConfiguration(
            new McpClientToolsFilterConfiguration(List.of("a", "b"), List.of("c")));

    final var options = toolMode(filters).createFilterOptions().orElseThrow();

    assertThat(options.toolFilters()).isEqualTo(new AllowDenyList(List.of("a", "b"), List.of("c")));
  }

  @Test
  void toolMode_allowsEverything_whenToolsFilterIsMissing() {
    final var filters = new McpClientToolModeFiltersConfiguration(null);

    final var options = toolMode(filters).createFilterOptions().orElseThrow();

    assertThat(options.toolFilters()).isEqualTo(EVERYTHING);
  }

  @Test
  void toolMode_hasNoFilterOptions_whenFiltersAreMissing() {
    assertThat(toolMode(null).createFilterOptions()).isEmpty();
  }

  @Test
  void standalone_mapsIncludedAndExcludedOfEveryPrimitiveToAllowedAndDenied() {
    final var filters =
        new McpClientStandaloneFiltersConfiguration(
            new McpClientToolsFilterConfiguration(List.of("t1"), List.of("t2")),
            new McpClientResourcesFilterConfiguration(List.of("r1"), List.of("r2")),
            new McpClientPromptsFilterConfiguration(List.of("p1"), List.of("p2")));

    final var options = standalone(filters).createFilterOptions().orElseThrow();

    assertThat(options.toolFilters()).isEqualTo(new AllowDenyList(List.of("t1"), List.of("t2")));
    assertThat(options.resourceFilters())
        .isEqualTo(new AllowDenyList(List.of("r1"), List.of("r2")));
    assertThat(options.promptFilters()).isEqualTo(new AllowDenyList(List.of("p1"), List.of("p2")));
  }

  @Test
  void standalone_mapsOnlyExcludedToDenied_forEveryPrimitive() {
    final var filters =
        new McpClientStandaloneFiltersConfiguration(
            new McpClientToolsFilterConfiguration(null, List.of("t")),
            new McpClientResourcesFilterConfiguration(null, List.of("r")),
            new McpClientPromptsFilterConfiguration(null, List.of("p")));

    final var options = standalone(filters).createFilterOptions().orElseThrow();

    assertThat(options.toolFilters()).isEqualTo(new AllowDenyList(List.of(), List.of("t")));
    assertThat(options.resourceFilters()).isEqualTo(new AllowDenyList(List.of(), List.of("r")));
    assertThat(options.promptFilters()).isEqualTo(new AllowDenyList(List.of(), List.of("p")));
  }

  @Test
  void standalone_keepsPrimitivesIndependent() {
    final var filters =
        new McpClientStandaloneFiltersConfiguration(
            null, new McpClientResourcesFilterConfiguration(null, List.of("r")), null);

    final var options = standalone(filters).createFilterOptions().orElseThrow();

    assertThat(options.toolFilters()).isEqualTo(EVERYTHING);
    assertThat(options.resourceFilters()).isEqualTo(new AllowDenyList(List.of(), List.of("r")));
    assertThat(options.promptFilters()).isEqualTo(EVERYTHING);
  }

  @Test
  void standalone_hasNoFilterOptions_whenFiltersAreMissing() {
    assertThat(standalone(null).createFilterOptions()).isEmpty();
  }

  private static ToolModeConfiguration toolMode(McpClientToolModeFiltersConfiguration filters) {
    return new ToolModeConfiguration(
        new McpClientOperationConfiguration("tools/call", Map.of()), null, filters);
  }

  private static StandaloneModeConfiguration standalone(
      McpClientStandaloneFiltersConfiguration filters) {
    return new StandaloneModeConfiguration(new ListToolsOperationConfiguration(), null, filters);
  }
}
