/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.camunda.connector.agenticai.mcp.client.model.McpClientToolsConfiguration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class McpToolNameFilterTest {

  @Nested
  class FilteringLogic {

    @Test
    void acceptsAllTools_whenEmptyConfiguration() {
      var filter = McpToolNameFilter.from(new McpClientToolsConfiguration(List.of(), List.of()));

      assertThat(filter.test("tool1")).isTrue();
      assertThat(filter.test("tool2")).isTrue();
      assertThat(filter.test("any-tool")).isTrue();
    }

    @ParameterizedTest
    @MethodSource("includeOnlyScenarios")
    void filtersToolsCorrectly_whenOnlyIncludeConfigured(
        List<String> included, String toolName, boolean expected) {
      var filter = McpToolNameFilter.from(new McpClientToolsConfiguration(included, List.of()));

      assertThat(filter.test(toolName)).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("excludeOnlyScenarios")
    void filtersToolsCorrectly_whenOnlyExcludeConfigured(
        List<String> excluded, String toolName, boolean expected) {
      var filter = McpToolNameFilter.from(new McpClientToolsConfiguration(List.of(), excluded));

      assertThat(filter.test(toolName)).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("includeAndExcludeScenarios")
    void filtersToolsCorrectly_whenBothIncludeAndExcludeConfigured(
        List<String> included, List<String> excluded, String toolName, boolean expected) {
      var filter = McpToolNameFilter.from(new McpClientToolsConfiguration(included, excluded));

      assertThat(filter.test(toolName)).isEqualTo(expected);
    }

    @Test
    void excludeTakesPrecedenceOverInclude() {
      var included = List.of("tool1", "tool2", "tool3");
      var excluded = List.of("tool2");
      var filter = McpToolNameFilter.from(new McpClientToolsConfiguration(included, excluded));

      assertThat(filter.test("tool1")).isTrue();
      assertThat(filter.test("tool2")).isFalse(); // excluded overrides included
      assertThat(filter.test("tool3")).isTrue();
      assertThat(filter.test("tool4")).isFalse(); // not in include list
    }

    static Stream<Arguments> includeOnlyScenarios() {
      return Stream.of(
          arguments(List.of("tool1"), "tool1", true),
          arguments(List.of("tool1"), "tool2", false),
          arguments(List.of("tool1", "tool2"), "tool1", true),
          arguments(List.of("tool1", "tool2"), "tool2", true),
          arguments(List.of("tool1", "tool2"), "tool3", false),
          arguments(List.of("file-read", "file-write"), "file-read", true),
          arguments(List.of("file-read", "file-write"), "database-query", false));
    }

    static Stream<Arguments> excludeOnlyScenarios() {
      return Stream.of(
          arguments(List.of("tool1"), "tool1", false),
          arguments(List.of("tool1"), "tool2", true),
          arguments(List.of("tool1", "tool2"), "tool1", false),
          arguments(List.of("tool1", "tool2"), "tool2", false),
          arguments(List.of("tool1", "tool2"), "tool3", true),
          arguments(List.of("dangerous-tool"), "safe-tool", true),
          arguments(List.of("dangerous-tool"), "dangerous-tool", false));
    }

    static Stream<Arguments> includeAndExcludeScenarios() {
      return Stream.of(
          // Tool in include but not in exclude - should be accepted
          arguments(List.of("tool1", "tool2"), List.of("tool3"), "tool1", true),
          // Tool in include and in exclude - should be rejected (exclude wins)
          arguments(List.of("tool1", "tool2"), List.of("tool2"), "tool2", false),
          // Tool not in include - should be rejected
          arguments(List.of("tool1", "tool2"), List.of("tool3"), "tool4", false),
          // Tool not in include but in exclude - should be rejected
          arguments(List.of("tool1", "tool2"), List.of("tool3", "tool4"), "tool4", false),
          // Complex scenario
          arguments(
              List.of("read", "write", "delete"),
              List.of("delete", "format"),
              "read",
              true), // included, not excluded
          arguments(
              List.of("read", "write", "delete"),
              List.of("delete", "format"),
              "delete",
              false), // included but excluded
          arguments(
              List.of("read", "write", "delete"),
              List.of("delete", "format"),
              "format",
              false), // not included, excluded
          arguments(
              List.of("read", "write", "delete"),
              List.of("delete", "format"),
              "admin",
              false) // not included, not excluded
          );
    }
  }

  @Nested
  class FactoryMethod {

    @Test
    void createsFilter_whenValidConfiguration() {
      var config = new McpClientToolsConfiguration(List.of("tool1"), List.of("tool2"));

      var filter = McpToolNameFilter.from(config);

      assertThat(filter).isNotNull();
      assertThat(filter.test("tool1")).isTrue();
      assertThat(filter.test("tool2")).isFalse();
    }

    @Test
    void createsFilterWithEmptyLists_whenNullConfiguration() {
      var filter = McpToolNameFilter.from(null);

      assertThat(filter).isNotNull();
      assertThat(filter.test("any-tool")).isTrue();
    }

    @Test
    void createsFilterWithEmptyLists_whenConfigurationHasNullLists() {
      var config = new McpClientToolsConfiguration(null, null);

      var filter = McpToolNameFilter.from(config);

      assertThat(filter).isNotNull();
      assertThat(filter.test("any-tool")).isTrue();
    }

    @Test
    void createsFilterWithPartialConfiguration_whenOnlyIncludeProvided() {
      var config = new McpClientToolsConfiguration(List.of("tool1"), null);

      var filter = McpToolNameFilter.from(config);

      assertThat(filter).isNotNull();
      assertThat(filter.test("tool1")).isTrue();
      assertThat(filter.test("tool2")).isFalse();
    }

    @Test
    void createsFilterWithPartialConfiguration_whenOnlyExcludeProvided() {
      var config = new McpClientToolsConfiguration(null, List.of("tool1"));

      var filter = McpToolNameFilter.from(config);

      assertThat(filter).isNotNull();
      assertThat(filter.test("tool1")).isFalse();
      assertThat(filter.test("tool2")).isTrue();
    }
  }
}
