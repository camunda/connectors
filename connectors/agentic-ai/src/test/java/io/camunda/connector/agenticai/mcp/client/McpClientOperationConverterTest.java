/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperationConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.StandaloneModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.ToolModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.CallToolOperationConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.ListToolsOperationConfiguration;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class McpClientOperationConverterTest {

  private final McpClientOperationConverter converter = new McpClientOperationConverter();

  @Nested
  class ToolModeTests {

    @Test
    void throwsExceptionOnInvalidOperation() {
      var modeConfiguration =
          new ToolModeConfiguration(new McpClientOperationConfiguration("invalid", Map.of()));

      assertThatThrownBy(() -> converter.convertOperation(modeConfiguration))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              ex -> {
                assertThat(ex.getErrorCode()).isEqualTo("MCP_CLIENT_UNSUPPORTED_OPERATION");
                assertThat(ex).hasMessageStartingWith("Unsupported MCP operation 'invalid'");
              });
    }

    @Test
    void convertsListToolsOperation() {
      var modeConfiguration =
          new ToolModeConfiguration(new McpClientOperationConfiguration("tools/list", Map.of()));

      var result = converter.convertOperation(modeConfiguration);

      var expected =
          new McpClientOperation.McpClientOperationImpl(
              McpClientOperation.Operation.LIST_TOOLS, Map.of());
      assertThat(result).isEqualTo(expected);
    }

    @Test
    void convertsCallToolOperation() {
      var modeConfiguration =
          new ToolModeConfiguration(
              new McpClientOperationConfiguration(
                  "tools/call", Map.of("name", "test-tool", "arguments", Map.of("key", "value"))));

      var result = converter.convertOperation(modeConfiguration);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientOperation.McpClientOperationImpl.class,
              operation ->
                  assertThat(operation.parameters())
                      .containsEntry("name", "test-tool")
                      .containsEntry("arguments", Map.of("key", "value")));
    }

    @Test
    void convertsCallToolOperationWithNullArguments() {
      var modeConfiguration =
          new ToolModeConfiguration(
              new McpClientOperationConfiguration("tools/call", Map.of("name", "test-tool")));

      var result = converter.convertOperation(modeConfiguration);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientOperation.McpClientOperationImpl.class,
              operation ->
                  assertThat(operation.parameters())
                      .containsEntry("name", "test-tool")
                      .doesNotContainKey("arguments"));
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.McpClientOperationConverterTest#complexArguments")
    void convertsCallToolOperationWithComplexArguments(Map<String, Object> arguments) {
      var modeConfiguration =
          new ToolModeConfiguration(
              new McpClientOperationConfiguration(
                  "tools/call", Map.of("name", "test-tool", "arguments", arguments)));

      var result = converter.convertOperation(modeConfiguration);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientOperation.McpClientOperationImpl.class,
              operation ->
                  assertThat(operation.parameters())
                      .containsEntry("name", "test-tool")
                      .hasEntrySatisfying(
                          "arguments",
                          args ->
                              assertThat(args)
                                  .asInstanceOf(MAP)
                                  .containsExactlyEntriesOf(arguments)));
    }
  }

  @Nested
  class StandaloneModeTests {

    @Test
    void convertsListToolsOperation() {
      var modeConfiguration =
          new StandaloneModeConfiguration(new ListToolsOperationConfiguration());

      var result = converter.convertOperation(modeConfiguration);

      var expected =
          new McpClientOperation.McpClientOperationImpl(
              McpClientOperation.Operation.LIST_TOOLS, Map.of());
      assertThat(result).isEqualTo(expected);
    }

    @Test
    void convertsCallToolOperation() {
      var modeConfiguration =
          new StandaloneModeConfiguration(
              new CallToolOperationConfiguration("test-tool", Map.of("key", "value")));

      var result = converter.convertOperation(modeConfiguration);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientOperation.McpClientOperationImpl.class,
              operation ->
                  assertThat(operation.parameters())
                      .containsEntry("name", "test-tool")
                      .containsEntry("arguments", Map.of("key", "value")));
    }

    @Test
    void convertsCallToolOperationWithNullArguments() {
      var modeConfiguration =
          new StandaloneModeConfiguration(new CallToolOperationConfiguration("test-tool", null));

      var result = converter.convertOperation(modeConfiguration);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientOperation.McpClientOperationImpl.class,
              operation ->
                  assertThat(operation.parameters())
                      .containsEntry("name", "test-tool")
                      .doesNotContainKey("arguments"));
    }

    @Test
    void convertsCallToolOperationWithEmptyArguments() {
      var modeConfiguration =
          new StandaloneModeConfiguration(
              new CallToolOperationConfiguration("test-tool", Map.of()));

      var result = converter.convertOperation(modeConfiguration);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientOperation.McpClientOperationImpl.class,
              operation ->
                  assertThat(operation.parameters())
                      .containsEntry("name", "test-tool")
                      .doesNotContainKey("arguments"));
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.McpClientOperationConverterTest#complexArguments")
    void convertsCallToolOperationWithComplexArguments(Map<String, Object> arguments) {
      var modeConfiguration =
          new StandaloneModeConfiguration(
              new CallToolOperationConfiguration("test-tool", arguments));

      var result = converter.convertOperation(modeConfiguration);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientOperation.McpClientOperationImpl.class,
              operation ->
                  assertThat(operation.parameters())
                      .containsEntry("name", "test-tool")
                      .hasEntrySatisfying(
                          "arguments",
                          args ->
                              assertThat(args)
                                  .asInstanceOf(MAP)
                                  .containsExactlyEntriesOf(arguments)));
    }
  }

  static Stream<Map<String, Object>> complexArguments() {
    return Stream.of(
        Map.of("string", "value", "number", 42, "boolean", true),
        Map.of("nested", Map.of("key1", "val1", "key2", "val2")),
        Map.of("array", List.of(1, 2, 3, 4, 5)),
        Map.of(
            "complex", Map.of("nested", Map.of("deep", "value"), "list", List.of("a", "b", "c"))));
  }
}
