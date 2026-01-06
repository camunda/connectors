/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyListBuilder;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.api.error.ConnectorException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ToolCallRequestTest {

  private static final AllowDenyList EMPTY_FILTER = AllowDenyListBuilder.builder().build();

  @Mock private McpClient mcpClient;

  private final ToolCallRequest testee = new ToolCallRequest(new ObjectMapper());

  @Test
  void executesTool_whenToolAllowedByFilter() {
    when(mcpClient.executeTool(any())).thenReturn(toolExecutionResult("Tool execution result"));

    final var result =
        testee.execute(
            mcpClient,
            EMPTY_FILTER,
            Map.of("name", "test-tool", "arguments", Map.of("arg1", "value1")));

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientCallToolResult.class,
            res -> {
              assertThat(res.name()).isEqualTo("test-tool");
              assertThat(res.isError()).isFalse();
              assertThat(res.content()).containsExactly(textContent("Tool execution result"));
            });
  }

  @ParameterizedTest
  @NullAndEmptySource
  void handlesEmptyArguments(Map<String, Object> arguments) {
    when(mcpClient.executeTool(any())).thenReturn(toolExecutionResult("Success with no args"));

    final var parameters = new LinkedHashMap<String, Object>();
    parameters.put("name", "test-tool");
    if (arguments != null) {
      parameters.put("arguments", arguments);
    }

    final var result = testee.execute(mcpClient, EMPTY_FILTER, parameters);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientCallToolResult.class,
            res -> {
              assertThat(res.name()).isEqualTo("test-tool");
              assertThat(res.isError()).isFalse();
              assertThat(res.content()).containsExactly(textContent("Success with no args"));
            });
  }

  @ParameterizedTest
  @MethodSource("toolExecutionArguments")
  void handlesDifferentTypesOfArguments(String toolName, Map<String, Object> arguments) {
    when(mcpClient.executeTool(any())).thenReturn(toolExecutionResult("Successful result"));

    final var result =
        testee.execute(mcpClient, EMPTY_FILTER, Map.of("name", toolName, "arguments", arguments));

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientCallToolResult.class,
            res -> {
              assertThat(res.name()).isEqualTo(toolName);
              assertThat(res.isError()).isFalse();
              assertThat(res.content()).containsExactly(textContent("Successful result"));
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void returnsDefaultResponseText_whenResponseIsBlank(String resultText) {
    when(mcpClient.executeTool(any())).thenReturn(toolExecutionResult(resultText));

    final var result =
        testee.execute(
            mcpClient,
            EMPTY_FILTER,
            Map.of("name", "test-tool", "arguments", Map.of("arg1", "value1")));

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientCallToolResult.class,
            res -> {
              assertThat(res.name()).isEqualTo("test-tool");
              assertThat(res.isError()).isFalse();
              assertThat(res.content())
                  .containsExactly(
                      textContent("Tool execution succeeded, but returned no result."));
            });
  }

  @Test
  void throwsException_whenToolNameIsNotPresent() {
    reset(mcpClient);

    assertThatThrownBy(
            () ->
                testee.execute(
                    mcpClient, EMPTY_FILTER, Map.of("arguments", Map.of("arg1", "value1"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Tool name must not be null");
  }

  @Test
  void returnsError_whenToolNotIncludedInFilter() {
    final var filter = AllowDenyListBuilder.builder().allowed(List.of("allowed-tool")).build();

    final var result =
        testee.execute(
            mcpClient,
            filter,
            Map.of("name", "blocked-tool", "arguments", Map.of("arg1", "value1")));

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientCallToolResult.class,
            res -> {
              assertThat(res.name()).isEqualTo("blocked-tool");
              assertThat(res.isError()).isTrue();
              assertThat(res.content())
                  .containsExactly(
                      textContent(
                          "Executing tool 'blocked-tool' is not allowed by filter configuration: [allowed=[allowed-tool], denied=[]]"));
            });
  }

  @Test
  void throwsException_whenInvalidCallToolOperationParamsProvided() {
    assertThatThrownBy(
            () ->
                testee.execute(
                    mcpClient,
                    EMPTY_FILTER,
                    Map.of("name", List.of("foo", "bar"), "something", "else")))
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

  @Test
  void returnsError_whenToolExcludedInFilter() {
    final var filter = AllowDenyListBuilder.builder().denied(List.of("blocked-tool")).build();

    final var result =
        testee.execute(
            mcpClient,
            filter,
            Map.of("name", "blocked-tool", "arguments", Map.of("arg1", "value1")));

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientCallToolResult.class,
            res -> {
              assertThat(res.name()).isEqualTo("blocked-tool");
              assertThat(res.isError()).isTrue();
              assertThat(res.content())
                  .containsExactly(
                      textContent(
                          "Executing tool 'blocked-tool' is not allowed by filter configuration: [allowed=[], denied=[blocked-tool]]"));
            });
  }

  @Test
  void returnsError_whenToolExecutionFails() {
    when(mcpClient.executeTool(any())).thenThrow(new RuntimeException("Tool execution failed"));

    final var result =
        testee.execute(
            mcpClient,
            EMPTY_FILTER,
            Map.of("name", "failing-tool", "arguments", Map.of("arg1", "value1")));

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientCallToolResult.class,
            res -> {
              assertThat(res.name()).isEqualTo("failing-tool");
              assertThat(res.isError()).isTrue();
              assertThat(res.content())
                  .containsExactly(
                      textContent("Error executing tool 'failing-tool': Tool execution failed"));
            });
  }

  private ToolExecutionResult toolExecutionResult(String resultText) {
    return ToolExecutionResult.builder().resultText(resultText).build();
  }

  static Stream<Arguments> toolExecutionArguments() {
    return Stream.of(
        arguments("valid-tool", Map.of("key", "value")),
        arguments("tool-with-complex-args", Map.of("nested", Map.of("key", "value"))));
  }
}
