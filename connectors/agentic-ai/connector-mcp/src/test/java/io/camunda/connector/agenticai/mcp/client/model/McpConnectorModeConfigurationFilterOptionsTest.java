/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.StandaloneModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.ToolModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.ListToolsOperationConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpConnectorModeConfigurationFilterOptionsTest {

  private static final AllowDenyList EVERYTHING = AllowDenyList.allowingEverything();

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void toolMode_mapsIncludedAndExcludedToolsToAllowedAndDenied() throws JsonProcessingException {
    final var filters =
        read(
            """
            {"tools": {"included": ["a", "b"], "excluded": ["c"]}}\
            """,
            McpClientToolModeFiltersConfiguration.class);

    final var options = toolMode(filters).createFilterOptions().orElseThrow();

    assertThat(options.toolFilters()).isEqualTo(new AllowDenyList(List.of("a", "b"), List.of("c")));
  }

  @Test
  void toolMode_allowsEverything_whenToolsFilterIsMissing() throws JsonProcessingException {
    final var filters = read("{}", McpClientToolModeFiltersConfiguration.class);

    final var options = toolMode(filters).createFilterOptions().orElseThrow();

    assertThat(options.toolFilters()).isEqualTo(EVERYTHING);
  }

  @Test
  void toolMode_hasNoFilterOptions_whenFiltersAreMissing() {
    assertThat(toolMode(null).createFilterOptions()).isEmpty();
  }

  @Test
  void standalone_mapsIncludedAndExcludedOfEveryPrimitiveToAllowedAndDenied()
      throws JsonProcessingException {
    final var filters =
        read(
            """
            {
              "tools": {"included": ["t1"], "excluded": ["t2"]},
              "resources": {"included": ["r1"], "excluded": ["r2"]},
              "prompts": {"included": ["p1"], "excluded": ["p2"]}
            }\
            """,
            McpClientStandaloneFiltersConfiguration.class);

    final var options = standalone(filters).createFilterOptions().orElseThrow();

    assertThat(options.toolFilters()).isEqualTo(new AllowDenyList(List.of("t1"), List.of("t2")));
    assertThat(options.resourceFilters())
        .isEqualTo(new AllowDenyList(List.of("r1"), List.of("r2")));
    assertThat(options.promptFilters()).isEqualTo(new AllowDenyList(List.of("p1"), List.of("p2")));
  }

  @Test
  void standalone_mapsOnlyExcludedToDenied_forEveryPrimitive() throws JsonProcessingException {
    final var filters =
        read(
            """
            {
              "tools": {"excluded": ["t"]},
              "resources": {"excluded": ["r"]},
              "prompts": {"excluded": ["p"]}
            }\
            """,
            McpClientStandaloneFiltersConfiguration.class);

    final var options = standalone(filters).createFilterOptions().orElseThrow();

    assertThat(options.toolFilters()).isEqualTo(new AllowDenyList(List.of(), List.of("t")));
    assertThat(options.resourceFilters()).isEqualTo(new AllowDenyList(List.of(), List.of("r")));
    assertThat(options.promptFilters()).isEqualTo(new AllowDenyList(List.of(), List.of("p")));
  }

  @Test
  void standalone_keepsPrimitivesIndependent() throws JsonProcessingException {
    final var filters =
        read(
            """
            {"resources": {"excluded": ["r"]}}\
            """,
            McpClientStandaloneFiltersConfiguration.class);

    final var options = standalone(filters).createFilterOptions().orElseThrow();

    assertThat(options.toolFilters()).isEqualTo(EVERYTHING);
    assertThat(options.resourceFilters()).isEqualTo(new AllowDenyList(List.of(), List.of("r")));
    assertThat(options.promptFilters()).isEqualTo(EVERYTHING);
  }

  @Test
  void standalone_hasNoFilterOptions_whenFiltersAreMissing() {
    assertThat(standalone(null).createFilterOptions()).isEmpty();
  }

  private <T> T read(String json, Class<T> type) throws JsonProcessingException {
    return objectMapper.readValue(json, type);
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
