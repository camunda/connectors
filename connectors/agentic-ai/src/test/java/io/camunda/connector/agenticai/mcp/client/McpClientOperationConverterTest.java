/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientCallToolOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientListToolsOperation;
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

  private final McpClientOperationConverter converter =
      new McpClientOperationConverter(new ObjectMapper());

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
                assertThat(ex)
                    .hasMessage(
                        "Unsupported MCP operation 'invalid'. Supported operations: 'tools/list', 'tools/call'");
              });
    }

    @Test
    void convertsListToolsOperation() {
      var modeConfiguration =
          new ToolModeConfiguration(new McpClientOperationConfiguration("tools/list", Map.of()));

      var result = converter.convertOperation(modeConfiguration);

      assertThat(result).isInstanceOf(McpClientListToolsOperation.class);
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
              McpClientCallToolOperation.class,
              operation -> {
                assertThat(operation.params().name()).isEqualTo("test-tool");
                assertThat(operation.params().arguments()).containsEntry("key", "value");
              });
    }

    @Test
    void convertsCallToolOperationWithNullArguments() {
      var modeConfiguration =
          new ToolModeConfiguration(
              new McpClientOperationConfiguration("tools/call", Map.of("name", "test-tool")));

      var result = converter.convertOperation(modeConfiguration);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientCallToolOperation.class,
              operation -> {
                assertThat(operation.params().name()).isEqualTo("test-tool");
                assertThat(operation.params().arguments()).isNull();
              });
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
              McpClientCallToolOperation.class,
              operation -> {
                assertThat(operation.params().name()).isEqualTo("test-tool");
                assertThat(operation.params().arguments()).containsExactlyEntriesOf(arguments);
              });
    }

    @Test
    void throwsExceptionOnInvalidCallToolOperationParams() {
      var modeConfiguration =
          new ToolModeConfiguration(
              new McpClientOperationConfiguration(
                  "tools/call", Map.of("name", List.of("foo", "bar"), "something", "else")));

      assertThatThrownBy(() -> converter.convertOperation(modeConfiguration))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              ex -> {
                assertThat(ex.getErrorCode()).isEqualTo("MCP_CLIENT_INVALID_PARAMS");
                assertThat(ex)
                    .hasMessageStartingWith("Unable to convert parameters passed to MCP client:")
                    .hasMessageContaining(
                        "Cannot deserialize value of type `java.lang.String` from Array value");
              });
    }
  }

  @Nested
  class StandaloneModeTests {

    @Test
    void convertsListToolsOperation() {
      var modeConfiguration =
          new StandaloneModeConfiguration(new ListToolsOperationConfiguration());

      var result = converter.convertOperation(modeConfiguration);

      assertThat(result).isInstanceOf(McpClientListToolsOperation.class);
    }

    @Test
    void convertsCallToolOperation() {
      var modeConfiguration =
          new StandaloneModeConfiguration(
              new CallToolOperationConfiguration("test-tool", Map.of("key", "value")));

      var result = converter.convertOperation(modeConfiguration);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientCallToolOperation.class,
              operation -> {
                assertThat(operation.params().name()).isEqualTo("test-tool");
                assertThat(operation.params().arguments()).containsEntry("key", "value");
              });
    }

    @Test
    void convertsCallToolOperationWithNullArguments() {
      var modeConfiguration =
          new StandaloneModeConfiguration(new CallToolOperationConfiguration("test-tool", null));

      var result = converter.convertOperation(modeConfiguration);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientCallToolOperation.class,
              operation -> {
                assertThat(operation.params().name()).isEqualTo("test-tool");
                assertThat(operation.params().arguments()).isNull();
              });
    }

    @Test
    void convertsCallToolOperationWithEmptyArguments() {
      var modeConfiguration =
          new StandaloneModeConfiguration(
              new CallToolOperationConfiguration("test-tool", Map.of()));

      var result = converter.convertOperation(modeConfiguration);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientCallToolOperation.class,
              operation -> {
                assertThat(operation.params().name()).isEqualTo("test-tool");
                assertThat(operation.params().arguments()).isEmpty();
              });
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
              McpClientCallToolOperation.class,
              op -> {
                assertThat(op.params().name()).isEqualTo("test-tool");
                assertThat(op.params().arguments()).containsExactlyEntriesOf(arguments);
              });
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
