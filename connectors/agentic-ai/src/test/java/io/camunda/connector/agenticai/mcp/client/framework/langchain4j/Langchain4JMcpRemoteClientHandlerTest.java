/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j;

import static io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.StreamableHttpMcpRemoteClientTransportConfiguration.*;
import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.McpClient;
import io.camunda.connector.agenticai.mcp.client.McpClientOperationConverter;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry.McpRemoteClientIdentifier;
import io.camunda.connector.agenticai.mcp.client.McpToolNameFilter;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientCallToolOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientListToolsOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperationConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpClientToolsConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.ToolModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientRequest;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientRequest.McpRemoteClientRequestData;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.SseHttpMcpRemoteClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.SseHttpMcpRemoteClientTransportConfiguration.SseHttpMcpRemoteClientConnection;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.StreamableHttpMcpRemoteClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Langchain4JMcpRemoteClientHandlerTest {

  private static final long PROCESS_DEFINITION_KEY = 123456L;
  private static final String ELEMENT_ID = "TestClientElement";
  private static final McpRemoteClientIdentifier CLIENT_ID =
      new McpRemoteClientIdentifier(PROCESS_DEFINITION_KEY, ELEMENT_ID);

  private static final String STREAMABLE_HTTP_URL = "http://localhost:123456/mcp";
  private static final String SSE_URL = "http://localhost:123456/sse";
  private static final Map<String, String> HTTP_HEADERS = Map.of("Authorization", "dummy");
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(12);

  private static final StreamableHttpMcpRemoteClientTransportConfiguration
      STREAMABLE_HTTP_TRANSPORT_CONFIG =
          new StreamableHttpMcpRemoteClientTransportConfiguration(
              new StreamableHttpMcpRemoteClientConnection(
                  STREAMABLE_HTTP_URL, HTTP_HEADERS, HTTP_TIMEOUT));

  private static final SseHttpMcpRemoteClientTransportConfiguration SSE_TRANSPORT_CONFIG =
      new SseHttpMcpRemoteClientTransportConfiguration(
          new SseHttpMcpRemoteClientConnection(SSE_URL, HTTP_HEADERS, HTTP_TIMEOUT));

  private static final McpClientOperationConfiguration LIST_TOOLS_OPERATION =
      new McpClientOperationConfiguration("tools/list", Map.of());

  private static final McpClientToolsConfiguration EMPTY_FILTER_CONFIGURATION =
      new McpClientToolsConfiguration(List.of(), List.of());
  private static final McpToolNameFilter EMPTY_FILTER =
      McpToolNameFilter.from(EMPTY_FILTER_CONFIGURATION);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final McpClientOperationConverter operationConverter =
      new McpClientOperationConverter(objectMapper);

  @Mock private McpRemoteClientRegistry<McpClient> remoteClientRegistry;
  @Mock private Langchain4JMcpClientExecutor clientExecutor;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private OutboundConnectorContext context;

  @Mock private McpClient mcpClient;

  private Langchain4JMcpRemoteClientHandler handler;

  @BeforeEach
  void setUp() {
    when(context.getJobContext().getProcessDefinitionKey()).thenReturn(PROCESS_DEFINITION_KEY);
    when(context.getJobContext().getElementId()).thenReturn(ELEMENT_ID);

    handler =
        new Langchain4JMcpRemoteClientHandler(
            operationConverter, remoteClientRegistry, clientExecutor);
  }

  @ParameterizedTest
  @MethodSource("transports")
  void handlesListToolsRequest(McpRemoteClientTransportConfiguration transport) {
    final var request = createRequest(transport, LIST_TOOLS_OPERATION);
    final var expectedResult = new McpClientListToolsResult(List.of());

    when(remoteClientRegistry.getClient(CLIENT_ID, transport)).thenReturn(mcpClient);
    when(clientExecutor.execute(
            eq(mcpClient),
            assertArg(
                operation -> assertThat(operation).isInstanceOf(McpClientListToolsOperation.class)),
            eq(EMPTY_FILTER)))
        .thenReturn(expectedResult);

    final var result = handler.handle(context, request);

    assertThat(result).isEqualTo(expectedResult);
  }

  @ParameterizedTest
  @MethodSource("callToolArguments")
  void handlesCallToolRequest(
      McpRemoteClientTransportConfiguration transport, Map<String, Object> arguments) {
    final var request =
        createRequest(
            transport,
            new McpClientOperationConfiguration(
                "tools/call", Map.of("name", "test-tool", "arguments", arguments)));
    final var expectedResult =
        new McpClientCallToolResult("test-tool", List.of(textContent("Success")), false);

    when(remoteClientRegistry.getClient(CLIENT_ID, transport)).thenReturn(mcpClient);
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

  @ParameterizedTest
  @MethodSource("transports")
  void throwsExceptionOnInvalidOperation(McpRemoteClientTransportConfiguration transport) {
    assertThatThrownBy(
            () ->
                handler.handle(
                    context,
                    createRequest(
                        transport, new McpClientOperationConfiguration("invalid", Map.of()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Could not resolve type id 'invalid'");
  }

  @ParameterizedTest
  @MethodSource("transports")
  void throwsExceptionWhenClientCouldNotBeCreated(McpRemoteClientTransportConfiguration transport) {
    final var exception = new IllegalArgumentException("Failed to create client");
    when(remoteClientRegistry.getClient(CLIENT_ID, transport)).thenThrow(exception);

    assertThatThrownBy(
            () -> handler.handle(context, createRequest(transport, LIST_TOOLS_OPERATION)))
        .isEqualTo(exception);
  }

  @ParameterizedTest
  @MethodSource("transports")
  void throwsExceptionWhenExecutorFails(McpRemoteClientTransportConfiguration transport) {
    final var exception = new IllegalArgumentException("Execution error");

    when(remoteClientRegistry.getClient(CLIENT_ID, transport)).thenReturn(mcpClient);
    when(clientExecutor.execute(eq(mcpClient), any(McpClientOperation.class), eq(EMPTY_FILTER)))
        .thenThrow(exception);

    assertThatThrownBy(
            () -> handler.handle(context, createRequest(transport, LIST_TOOLS_OPERATION)))
        .isEqualTo(exception);
  }

  private McpRemoteClientRequest createRequest(
      McpRemoteClientTransportConfiguration transport, McpClientOperationConfiguration operation) {
    return new McpRemoteClientRequest(
        new McpRemoteClientRequestData(
            transport, new ToolModeConfiguration(operation), EMPTY_FILTER_CONFIGURATION));
  }

  static List<McpRemoteClientTransportConfiguration> transports() {
    return List.of(STREAMABLE_HTTP_TRANSPORT_CONFIG, SSE_TRANSPORT_CONFIG);
  }

  static List<Arguments> callToolArguments() {
    List<Arguments> result = new ArrayList<>();
    transports()
        .forEach(
            transport -> {
              result.add(arguments(transport, Map.of("arg1", "value1", "arg2", 5)));
              result.add(
                  arguments(
                      transport,
                      Map.of("nested", Map.of("key", "value"), "array", List.of(1, 2, 3))));
            });

    return result;
  }
}
