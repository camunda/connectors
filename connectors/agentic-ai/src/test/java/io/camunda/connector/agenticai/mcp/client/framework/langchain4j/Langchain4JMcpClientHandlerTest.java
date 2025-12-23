/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j;

import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.McpClient;
import io.camunda.connector.agenticai.mcp.client.McpClientOperationConverter;
import io.camunda.connector.agenticai.mcp.client.McpClientRegistry;
import io.camunda.connector.agenticai.mcp.client.McpToolNameFilter;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc.Langchain4JMcpClientExecutor;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientCallToolOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientListToolsOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperationConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpClientRequest;
import io.camunda.connector.agenticai.mcp.client.model.McpClientRequest.McpClientRequestData;
import io.camunda.connector.agenticai.mcp.client.model.McpClientRequest.McpClientRequestData.ClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpClientToolsConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.StandaloneModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.ToolModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Langchain4JMcpClientHandlerTest {

  private static final String CLIENT_ID = "test-client";
  private static final ClientConfiguration CLIENT_CONFIG = new ClientConfiguration(CLIENT_ID);

  private static final McpClientOperationConfiguration LIST_TOOLS_OPERATION =
      new McpClientOperationConfiguration("tools/list", Map.of());

  private static final McpClientToolsConfiguration EMPTY_FILTER_CONFIGURATION =
      new McpClientToolsConfiguration(List.of(), List.of());
  private static final McpToolNameFilter EMPTY_FILTER =
      McpToolNameFilter.from(EMPTY_FILTER_CONFIGURATION);

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Spy
  private final McpClientOperationConverter operationConverter =
      new McpClientOperationConverter(objectMapper);

  @Mock private McpClientRegistry<McpClient> clientRegistry;
  @Mock private Langchain4JMcpClientExecutor clientExecutor;

  @Mock private OutboundConnectorContext context;
  @Mock private McpClient mcpClient;

  private Langchain4JMcpClientHandler handler;

  @BeforeEach
  void setUp() {
    handler = new Langchain4JMcpClientHandler(operationConverter, clientRegistry, clientExecutor);
  }

  @Test
  void throwsExceptionWhenOperationConversionFails() {
    final var request = createToolModeRequest(LIST_TOOLS_OPERATION);

    final var exception = new IllegalArgumentException("Failed to convert operation");
    when(operationConverter.convertOperation(request.data().connectorMode())).thenThrow(exception);

    assertThatThrownBy(() -> handler.handle(context, request)).isEqualTo(exception);
  }

  @Test
  void throwsExceptionWhenClientIsNotFound() {
    final var exception = new IllegalArgumentException("Client not found: non-existent-client");
    when(clientRegistry.getClient(CLIENT_ID)).thenThrow(exception);

    assertThatThrownBy(() -> handler.handle(context, createToolModeRequest(LIST_TOOLS_OPERATION)))
        .isEqualTo(exception);
  }

  @Test
  void throwsExceptionWhenExecutorFails() {
    final var exception = new IllegalArgumentException("Execution error");

    when(clientRegistry.getClient(CLIENT_ID)).thenReturn(mcpClient);
    when(clientExecutor.execute(eq(mcpClient), any(McpClientOperation.class), eq(EMPTY_FILTER)))
        .thenThrow(exception);

    assertThatThrownBy(() -> handler.handle(context, createToolModeRequest(LIST_TOOLS_OPERATION)))
        .isEqualTo(exception);
  }

  @Nested
  class ToolModeTests {

    @Test
    void handlesListToolsRequest() {
      final var request = createToolModeRequest(LIST_TOOLS_OPERATION);
      final var expectedResult = new McpClientListToolsResult(List.of());

      when(clientRegistry.getClient(CLIENT_ID)).thenReturn(mcpClient);
      when(clientExecutor.execute(
              eq(mcpClient),
              assertArg(
                  operation ->
                      assertThat(operation).isInstanceOf(McpClientListToolsOperation.class)),
              eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);

      assertThat(result).isEqualTo(expectedResult);
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.framework.langchain4j.Langchain4JMcpClientHandlerTest#callToolArguments")
    void handlesCallToolRequest(Map<String, Object> arguments) {
      final var request =
          createToolModeRequest(
              new McpClientOperationConfiguration(
                  "tools/call", Map.of("name", "test-tool", "arguments", arguments)));
      final var expectedResult =
          new McpClientCallToolResult("test-tool", List.of(textContent("Success")), false);

      when(clientRegistry.getClient(CLIENT_ID)).thenReturn(mcpClient);
      when(clientExecutor.execute(
              eq(mcpClient),
              assertArg(
                  operation ->
                      assertThat(operation)
                          .isInstanceOfSatisfying(
                              McpClientCallToolOperation.class,
                              op -> {
                                assertThat(op.params().name()).isEqualTo("test-tool");
                                assertThat(op.params().arguments())
                                    .containsExactlyEntriesOf(arguments);
                              })),
              eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);

      assertThat(result).isEqualTo(expectedResult);
    }
  }

  @Nested
  class StandaloneModeTests {
    @Test
    void handlesListToolsRequest() {
      final var request =
          createStandaloneModeRequest(
              new McpStandaloneOperationConfiguration.ListToolsOperationConfiguration());
      final var expectedResult = new McpClientListToolsResult(List.of());

      when(clientRegistry.getClient(CLIENT_ID)).thenReturn(mcpClient);
      when(clientExecutor.execute(
              eq(mcpClient),
              assertArg(
                  operation ->
                      assertThat(operation).isInstanceOf(McpClientListToolsOperation.class)),
              eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);

      assertThat(result).isEqualTo(expectedResult);
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.framework.langchain4j.Langchain4JMcpClientHandlerTest#callToolArguments")
    void handlesCallToolRequest(Map<String, Object> arguments) {
      final var request =
          createStandaloneModeRequest(
              new McpStandaloneOperationConfiguration.CallToolOperationConfiguration(
                  "test-tool", arguments));
      final var expectedResult =
          new McpClientCallToolResult("test-tool", List.of(textContent("Success")), false);

      when(clientRegistry.getClient(CLIENT_ID)).thenReturn(mcpClient);
      when(clientExecutor.execute(
              eq(mcpClient),
              assertArg(
                  operation ->
                      assertThat(operation)
                          .isInstanceOfSatisfying(
                              McpClientCallToolOperation.class,
                              op -> {
                                assertThat(op.params().name()).isEqualTo("test-tool");
                                assertThat(op.params().arguments())
                                    .containsExactlyEntriesOf(arguments);
                              })),
              eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);

      assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void handlesCallToolRequestWithNullArguments() {
      final var request =
          createStandaloneModeRequest(
              new McpStandaloneOperationConfiguration.CallToolOperationConfiguration(
                  "test-tool", null));
      final var expectedResult =
          new McpClientCallToolResult("test-tool", List.of(textContent("Success")), false);

      when(clientRegistry.getClient(CLIENT_ID)).thenReturn(mcpClient);
      when(clientExecutor.execute(
              eq(mcpClient),
              assertArg(
                  operation ->
                      assertThat(operation)
                          .isInstanceOfSatisfying(
                              McpClientCallToolOperation.class,
                              op -> {
                                assertThat(op.params().name()).isEqualTo("test-tool");
                                assertThat(op.params().arguments()).isNull();
                              })),
              eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);

      assertThat(result).isEqualTo(expectedResult);
    }
  }

  private McpClientRequest createToolModeRequest(McpClientOperationConfiguration operation) {
    return new McpClientRequest(
        new McpClientRequestData(
            CLIENT_CONFIG, new ToolModeConfiguration(operation), EMPTY_FILTER_CONFIGURATION));
  }

  private McpClientRequest createStandaloneModeRequest(
      McpStandaloneOperationConfiguration operation) {
    return new McpClientRequest(
        new McpClientRequestData(
            CLIENT_CONFIG, new StandaloneModeConfiguration(operation), EMPTY_FILTER_CONFIGURATION));
  }

  static Stream<Map<String, Object>> callToolArguments() {
    return Stream.of(
        Map.of("arg1", "value1", "arg2", 5),
        Map.of("nested", Map.of("key", "value"), "array", List.of(1, 2, 3)));
  }
}
