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
import io.camunda.connector.agenticai.mcp.client.McpClientFactory;
import io.camunda.connector.agenticai.mcp.client.McpToolNameFilter;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.HttpMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientCallToolOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientListToolsOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperationConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpClientToolsConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientRequest;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientRequest.McpRemoteClientRequestData;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientRequest.McpRemoteClientRequestData.HttpConnectionConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Langchain4JMcpRemoteClientHandlerTest {

  private static final String ELEMENT_ID = "TestClientElement";
  private static final long ELEMENT_INSTANCE_KEY = 123456L;
  private static final String CLIENT_ID = "TestClientElement_123456";

  private static final String SSE_URL = "http://localhost:123456/sse";
  private static final Map<String, String> HTTP_HEADERS = Map.of("Authorization", "dummy");
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(12);

  private static final HttpConnectionConfiguration HTTP_CONFIG =
      new HttpConnectionConfiguration(SSE_URL, HTTP_HEADERS, HTTP_TIMEOUT);
  private static final McpClientConfiguration EXPECTED_CLIENT_CONFIGURATION =
      new McpClientConfiguration(
          true,
          null,
          new HttpMcpClientTransportConfiguration(
              SSE_URL, HTTP_HEADERS, HTTP_TIMEOUT, false, false),
          null,
          null,
          null);

  private static final McpClientOperationConfiguration LIST_TOOLS_OPERATION =
      new McpClientOperationConfiguration("tools/list", Map.of());

  private static final McpClientToolsConfiguration EMPTY_FILTER_CONFIGURATION =
      new McpClientToolsConfiguration(List.of(), List.of());
  private static final McpToolNameFilter EMPTY_FILTER =
      McpToolNameFilter.from(EMPTY_FILTER_CONFIGURATION);

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private McpClientFactory<McpClient> clientFactory;
  @Mock private Langchain4JMcpClientExecutor clientExecutor;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private OutboundConnectorContext context;

  @Mock private McpClient mcpClient;

  private Langchain4JMcpRemoteClientHandler handler;

  @BeforeEach
  void setUp() {
    when(context.getJobContext().getElementId()).thenReturn(ELEMENT_ID);
    when(context.getJobContext().getElementInstanceKey()).thenReturn(ELEMENT_INSTANCE_KEY);

    handler = new Langchain4JMcpRemoteClientHandler(objectMapper, clientFactory, clientExecutor);
  }

  @Test
  void handlesListToolsRequest() {
    final var request = createRequest(LIST_TOOLS_OPERATION);
    final var expectedResult = new McpClientListToolsResult(List.of());

    when(clientFactory.createClient(eq(CLIENT_ID), eq(EXPECTED_CLIENT_CONFIGURATION)))
        .thenReturn(mcpClient);
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
  void handlesCallToolRequest(Map<String, Object> arguments) {
    final var request =
        createRequest(
            new McpClientOperationConfiguration(
                "tools/call", Map.of("name", "test-tool", "arguments", arguments)));
    final var expectedResult =
        new McpClientCallToolResult("test-tool", List.of(textContent("Success")), false);

    when(clientFactory.createClient(eq(CLIENT_ID), eq(EXPECTED_CLIENT_CONFIGURATION)))
        .thenReturn(mcpClient);
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
  void throwsExceptionOnInvalidOperation() {
    assertThatThrownBy(
            () ->
                handler.handle(
                    context,
                    createRequest(new McpClientOperationConfiguration("invalid", Map.of()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Could not resolve type id 'invalid'");
  }

  @Test
  void throwsExceptionWhenClientIsNotFound() {
    final var exception = new IllegalArgumentException("Failed to create client");
    when(clientFactory.createClient(eq(CLIENT_ID), eq(EXPECTED_CLIENT_CONFIGURATION)))
        .thenThrow(exception);

    assertThatThrownBy(() -> handler.handle(context, createRequest(LIST_TOOLS_OPERATION)))
        .isInstanceOf(RuntimeException.class)
        .hasCause(exception);
  }

  @Test
  void throwsExceptionWhenExecutorFails() {
    final var exception = new IllegalArgumentException("Execution error");

    when(clientFactory.createClient(eq(CLIENT_ID), eq(EXPECTED_CLIENT_CONFIGURATION)))
        .thenReturn(mcpClient);
    when(clientExecutor.execute(eq(mcpClient), any(McpClientOperation.class), eq(EMPTY_FILTER)))
        .thenThrow(exception);

    assertThatThrownBy(() -> handler.handle(context, createRequest(LIST_TOOLS_OPERATION)))
        .isInstanceOf(RuntimeException.class)
        .hasCause(exception);
  }

  private McpRemoteClientRequest createRequest(McpClientOperationConfiguration operation) {
    return new McpRemoteClientRequest(
        new McpRemoteClientRequestData(HTTP_CONFIG, EMPTY_FILTER_CONFIGURATION, operation));
  }

  static Stream<Map<String, Object>> callToolArguments() {
    return Stream.of(
        Map.of("arg1", "value1", "arg2", 5),
        Map.of("nested", Map.of("key", "value"), "array", List.of(1, 2, 3)));
  }
}
