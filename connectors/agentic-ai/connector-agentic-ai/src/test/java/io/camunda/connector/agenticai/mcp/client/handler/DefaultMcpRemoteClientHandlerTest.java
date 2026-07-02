/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.handler;

import static io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpMethod.*;
import static io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpMethod.LIST_RESOURCE_TEMPLATES;
import static io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.StreamableHttpMcpRemoteClientTransportConfiguration.*;
import static io.camunda.connector.agenticai.mcp.client.model.content.McpTextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry.McpRemoteClientIdentifier;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientDelegate;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientExecutor;
import io.camunda.connector.agenticai.mcp.client.filters.FilterOptions;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperationConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpClientStandaloneFiltersConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpClientToolModeFiltersConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpClientToolsFilterConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.StandaloneModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.ToolModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientOptionsConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientRequest;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientRequest.McpRemoteClientRequestData;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.SseHttpMcpRemoteClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.SseHttpMcpRemoteClientTransportConfiguration.SseHttpMcpRemoteClientConnection;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.StreamableHttpMcpRemoteClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.CallToolOperationConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.ListToolsOperationConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.result.*;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultMcpRemoteClientHandlerTest {

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
                  null, STREAMABLE_HTTP_URL, HTTP_HEADERS, HTTP_TIMEOUT));

  private static final SseHttpMcpRemoteClientTransportConfiguration SSE_TRANSPORT_CONFIG =
      new SseHttpMcpRemoteClientTransportConfiguration(
          new SseHttpMcpRemoteClientConnection(null, SSE_URL, HTTP_HEADERS, HTTP_TIMEOUT));

  private static final McpClientOperationConfiguration LIST_TOOLS_OPERATION =
      new McpClientOperationConfiguration("tools/list", Map.of());

  private static final McpClientToolsFilterConfiguration EMPTY_FILTER_CONFIGURATION =
      new McpClientToolsFilterConfiguration(List.of(), List.of());
  private static final FilterOptions EMPTY_FILTER = FilterOptions.defaultOptions();

  @Mock private McpRemoteClientRegistry remoteClientRegistry;
  @Mock private McpClientExecutor clientExecutor;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private OutboundConnectorContext context;

  @Mock private McpClientDelegate mcpClient;

  private DefaultMcpRemoteClientHandler handler;

  @BeforeEach
  void setUp() {
    when(context.getJobContext().getProcessDefinitionKey()).thenReturn(PROCESS_DEFINITION_KEY);
    when(context.getJobContext().getElementId()).thenReturn(ELEMENT_ID);

    handler = new DefaultMcpRemoteClientHandler(remoteClientRegistry, clientExecutor);
  }

  @ParameterizedTest
  @MethodSource(
      "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpRemoteClientHandlerTest#transports")
  void throwsExceptionWhenClientCouldNotBeCreated(McpRemoteClientTransportConfiguration transport) {
    final var exception = new IllegalArgumentException("Failed to create client");
    when(remoteClientRegistry.getClient(CLIENT_ID, transport, false)).thenThrow(exception);

    assertThatThrownBy(
            () ->
                handler.handle(
                    context, createToolModeRequest(transport, false, LIST_TOOLS_OPERATION)))
        .isEqualTo(exception);
  }

  @ParameterizedTest
  @MethodSource(
      "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpRemoteClientHandlerTest#transports")
  void throwsExceptionWhenExecutorFails(McpRemoteClientTransportConfiguration transport) {
    final var exception = new IllegalArgumentException("Execution error");

    when(remoteClientRegistry.getClient(CLIENT_ID, transport, false)).thenReturn(mcpClient);
    when(clientExecutor.execute(eq(mcpClient), any(McpClientOperation.class), eq(EMPTY_FILTER)))
        .thenThrow(exception);

    assertThatThrownBy(
            () ->
                handler.handle(
                    context, createToolModeRequest(transport, false, LIST_TOOLS_OPERATION)))
        .isEqualTo(exception);
  }

  @Test
  void usesDefaultOptions_whenConnectorModeDoesNotProvideFilters() {
    final var request =
        new McpRemoteClientRequest(
            new McpRemoteClientRequestData(
                null,
                new McpRemoteClientOptionsConfiguration(false),
                new ToolModeConfiguration(LIST_TOOLS_OPERATION, null)));

    when(remoteClientRegistry.getClient(CLIENT_ID, null, false)).thenReturn(mcpClient);
    when(clientExecutor.execute(eq(mcpClient), any(McpClientOperation.class), eq(EMPTY_FILTER)))
        .thenReturn(new McpClientListToolsResult(List.of()));

    handler.handle(context, request);
  }

  @Nested
  class ToolModeTests {

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpRemoteClientHandlerTest#transports")
    void handlesListToolsRequest(McpRemoteClientTransportConfiguration transport) {
      final var request = createToolModeRequest(transport, false, LIST_TOOLS_OPERATION);
      final var expectedResult = new McpClientListToolsResult(List.of());

      when(remoteClientRegistry.getClient(CLIENT_ID, transport, false)).thenReturn(mcpClient);
      when(clientExecutor.execute(
              eq(mcpClient),
              assertArg(
                  operation ->
                      assertThat(operation)
                          .returns(LIST_TOOLS, McpClientOperation::method)
                          .returns(Map.of(), McpClientOperation::params)),
              eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);

      assertThat(result).isEqualTo(expectedResult);
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpRemoteClientHandlerTest#mcpOperationArguments")
    void handlesCallToolRequest(
        McpRemoteClientTransportConfiguration transport, Map<String, Object> arguments) {
      final var request =
          createToolModeRequest(
              transport,
              false,
              new McpClientOperationConfiguration(
                  "tools/call", Map.of("name", "test-tool", "arguments", arguments)));
      final var expectedResult =
          new McpClientCallToolResult("test-tool", List.of(textContent("Success")), false);

      when(remoteClientRegistry.getClient(CLIENT_ID, transport, false)).thenReturn(mcpClient);
      when(clientExecutor.execute(
              eq(mcpClient),
              assertArg(
                  operation ->
                      assertThat(operation)
                          .isInstanceOfSatisfying(
                              McpClientOperation.McpClientOperationImpl.class,
                              op ->
                                  assertThat(op.params())
                                      .containsEntry("name", "test-tool")
                                      .hasEntrySatisfying(
                                          "arguments",
                                          args ->
                                              assertThat(args)
                                                  .asInstanceOf(InstanceOfAssertFactories.MAP)
                                                  .containsExactlyEntriesOf(arguments)))),
              eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);

      assertThat(result).isEqualTo(expectedResult);
    }
  }

  @Nested
  class StandaloneModeTests {

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpRemoteClientHandlerTest#transports")
    void handlesListToolsRequest(McpRemoteClientTransportConfiguration transport) {
      final var request =
          createStandaloneModeRequest(transport, false, new ListToolsOperationConfiguration());
      final var expectedResult = new McpClientListToolsResult(List.of());

      when(remoteClientRegistry.getClient(CLIENT_ID, transport, false)).thenReturn(mcpClient);
      when(clientExecutor.execute(
              eq(mcpClient),
              assertArg(
                  operation ->
                      assertThat(operation)
                          .returns(LIST_TOOLS, McpClientOperation::method)
                          .returns(Map.of(), McpClientOperation::params)),
              eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);

      assertThat(result).isEqualTo(expectedResult);
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpRemoteClientHandlerTest#mcpOperationArguments")
    void handlesCallToolRequest(
        McpRemoteClientTransportConfiguration transport, Map<String, Object> arguments) {
      final var request =
          createStandaloneModeRequest(
              transport, false, new CallToolOperationConfiguration("test-tool", arguments));
      final var expectedResult =
          new McpClientCallToolResult("test-tool", List.of(textContent("Success")), false);

      when(remoteClientRegistry.getClient(CLIENT_ID, transport, false)).thenReturn(mcpClient);
      when(clientExecutor.execute(
              eq(mcpClient),
              assertArg(
                  operation ->
                      assertThat(operation)
                          .isInstanceOfSatisfying(
                              McpClientOperation.McpClientOperationImpl.class,
                              op -> {
                                assertThat(op.method()).isEqualTo(CALL_TOOL);
                                assertThat(op.params())
                                    .containsEntry("name", "test-tool")
                                    .hasEntrySatisfying(
                                        "arguments",
                                        args ->
                                            assertThat(args)
                                                .asInstanceOf(InstanceOfAssertFactories.MAP)
                                                .containsExactlyEntriesOf(arguments));
                              })),
              eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);

      assertThat(result).isEqualTo(expectedResult);
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpRemoteClientHandlerTest#transports")
    void handlesCallToolRequestWithNullArguments(McpRemoteClientTransportConfiguration transport) {
      final var request =
          createStandaloneModeRequest(
              transport, false, new CallToolOperationConfiguration("test-tool", null));
      final var expectedResult =
          new McpClientCallToolResult("test-tool", List.of(textContent("Success")), false);

      when(remoteClientRegistry.getClient(CLIENT_ID, transport, false)).thenReturn(mcpClient);
      when(clientExecutor.execute(
              eq(mcpClient),
              assertArg(
                  operation ->
                      assertThat(operation)
                          .isInstanceOfSatisfying(
                              McpClientOperation.McpClientOperationImpl.class,
                              op ->
                                  assertThat(op.params())
                                      .containsEntry("name", "test-tool")
                                      .doesNotContainKey("arguments"))),
              eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);

      assertThat(result).isEqualTo(expectedResult);
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpRemoteClientHandlerTest#transports")
    void handlesListResourcesRequest(McpRemoteClientTransportConfiguration transport) {
      final var request =
          createStandaloneModeRequest(
              transport,
              false,
              new McpStandaloneOperationConfiguration.ListResourcesOperationConfiguration());
      final var expectedResult = new McpClientListToolsResult(List.of());

      when(remoteClientRegistry.getClient(CLIENT_ID, transport, false)).thenReturn(mcpClient);
      when(clientExecutor.execute(
              eq(mcpClient),
              assertArg(
                  operation ->
                      assertThat(operation)
                          .returns(LIST_RESOURCES, McpClientOperation::method)
                          .returns(Map.of(), McpClientOperation::params)),
              eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);

      assertThat(result).isEqualTo(expectedResult);
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpRemoteClientHandlerTest#transports")
    void handlesListResourceTemplatesRequest(McpRemoteClientTransportConfiguration transport) {
      final var request =
          createStandaloneModeRequest(
              transport,
              false,
              new McpStandaloneOperationConfiguration
                  .ListResourceTemplatesOperationConfiguration());
      final var expectedResult = new McpClientListResourceTemplatesResult(List.of());

      when(remoteClientRegistry.getClient(CLIENT_ID, transport, false)).thenReturn(mcpClient);
      when(clientExecutor.execute(
              eq(mcpClient),
              assertArg(
                  operation ->
                      assertThat(operation)
                          .returns(LIST_RESOURCE_TEMPLATES, McpClientOperation::method)
                          .returns(Map.of(), McpClientOperation::params)),
              eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);

      assertThat(result).isEqualTo(expectedResult);
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpRemoteClientHandlerTest#transports")
    void handlesReadResourceRequest(McpRemoteClientTransportConfiguration transport) {
      final var request =
          createStandaloneModeRequest(
              transport,
              false,
              new McpStandaloneOperationConfiguration.ReadResourceOperationConfiguration(
                  "resource-1"));
      final var expectedResult =
          new McpClientReadResourceResult(
              List.of(new ResourceData.TextResourceData("uri", "text/plain", "Sample text")));

      when(remoteClientRegistry.getClient(CLIENT_ID, transport, false)).thenReturn(mcpClient);
      when(clientExecutor.execute(
              eq(mcpClient),
              assertArg(
                  operation ->
                      assertThat(operation)
                          .isInstanceOfSatisfying(
                              McpClientOperation.McpClientOperationImpl.class,
                              op ->
                                  assertThat(operation)
                                      .returns(READ_RESOURCE, McpClientOperation::method)
                                      .returns(
                                          Map.of("uri", "resource-1"),
                                          McpClientOperation::params))),
              eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);

      assertThat(result).isEqualTo(expectedResult);
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpRemoteClientHandlerTest#transports")
    void handlesListPromptsRequest(McpRemoteClientTransportConfiguration transport) {
      final var request =
          createStandaloneModeRequest(
              transport,
              false,
              new McpStandaloneOperationConfiguration.ListPromptsOperationConfiguration());
      final var expectedResult = new McpClientListPromptsResult(List.of());

      when(remoteClientRegistry.getClient(CLIENT_ID, transport, false)).thenReturn(mcpClient);
      when(clientExecutor.execute(
              eq(mcpClient),
              assertArg(
                  operation ->
                      assertThat(operation)
                          .returns(LIST_PROMPTS, McpClientOperation::method)
                          .returns(Map.of(), McpClientOperation::params)),
              eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);

      assertThat(result).isEqualTo(expectedResult);
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpRemoteClientHandlerTest#mcpOperationArguments")
    void handlesGetPromptRequest(
        McpRemoteClientTransportConfiguration transport, Map<String, Object> arguments) {
      final var request =
          createStandaloneModeRequest(
              transport,
              false,
              new McpStandaloneOperationConfiguration.GetPromptOperationConfiguration(
                  "code_review", arguments));
      final var expectedResult =
          new McpClientGetPromptResult(
              "Code review",
              List.of(
                  new McpClientGetPromptResult.PromptMessage(
                      "user",
                      new McpClientGetPromptResult.TextMessage("Review code the code for me."))));

      when(remoteClientRegistry.getClient(CLIENT_ID, transport, false)).thenReturn(mcpClient);
      when(clientExecutor.execute(
              eq(mcpClient),
              assertArg(
                  operation ->
                      assertThat(operation)
                          .isInstanceOfSatisfying(
                              McpClientOperation.McpClientOperationImpl.class,
                              op -> {
                                assertThat(op.method()).isEqualTo(GET_PROMPT);
                                assertThat(op.params())
                                    .containsEntry("name", "code_review")
                                    .hasEntrySatisfying(
                                        "arguments",
                                        args ->
                                            assertThat(args)
                                                .asInstanceOf(InstanceOfAssertFactories.MAP)
                                                .containsExactlyEntriesOf(arguments));
                              })),
              eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);

      assertThat(result).isEqualTo(expectedResult);
    }
  }

  @Nested
  class CachingBehaviorTests {

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpRemoteClientHandlerTest#transports")
    void requestsClientWithCachingEnabledWhenClientCacheIsTrue(
        McpRemoteClientTransportConfiguration transport) {
      final var request = createToolModeRequest(transport, true, LIST_TOOLS_OPERATION);
      final var expectedResult = new McpClientListToolsResult(List.of());

      when(remoteClientRegistry.getClient(CLIENT_ID, transport, true)).thenReturn(mcpClient);
      when(clientExecutor.execute(eq(mcpClient), any(McpClientOperation.class), eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);
      assertThat(result).isEqualTo(expectedResult);

      verify(remoteClientRegistry, never()).closeClient(any(), any());
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpRemoteClientHandlerTest#transports")
    void closesNonCachedClientAfterExecution(McpRemoteClientTransportConfiguration transport) {
      final var request = createToolModeRequest(transport, false, LIST_TOOLS_OPERATION);
      final var expectedResult = new McpClientListToolsResult(List.of());

      when(remoteClientRegistry.getClient(CLIENT_ID, transport, false)).thenReturn(mcpClient);
      when(clientExecutor.execute(eq(mcpClient), any(McpClientOperation.class), eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);
      assertThat(result).isEqualTo(expectedResult);

      verify(remoteClientRegistry).closeClient(CLIENT_ID, mcpClient);
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpRemoteClientHandlerTest#transports")
    void requestsNonCachedClientWhenOptionsAreNull(
        McpRemoteClientTransportConfiguration transport) {
      final var request =
          new McpRemoteClientRequest(
              new McpRemoteClientRequestData(
                  transport,
                  null,
                  new ToolModeConfiguration(
                      LIST_TOOLS_OPERATION,
                      new McpClientToolModeFiltersConfiguration(EMPTY_FILTER_CONFIGURATION))));

      final var expectedResult = new McpClientListToolsResult(List.of());

      when(remoteClientRegistry.getClient(CLIENT_ID, transport, false)).thenReturn(mcpClient);
      when(clientExecutor.execute(eq(mcpClient), any(McpClientOperation.class), eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);
      assertThat(result).isEqualTo(expectedResult);

      verify(remoteClientRegistry).closeClient(CLIENT_ID, mcpClient);
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpRemoteClientHandlerTest#transports")
    void requestsNonCachedClientWhenClientCacheOptionIsNull(
        McpRemoteClientTransportConfiguration transport) {
      final var request =
          new McpRemoteClientRequest(
              new McpRemoteClientRequestData(
                  transport,
                  new McpRemoteClientOptionsConfiguration(null),
                  new ToolModeConfiguration(
                      LIST_TOOLS_OPERATION,
                      new McpClientToolModeFiltersConfiguration(EMPTY_FILTER_CONFIGURATION))));

      final var expectedResult = new McpClientListToolsResult(List.of());

      when(remoteClientRegistry.getClient(CLIENT_ID, transport, false)).thenReturn(mcpClient);
      when(clientExecutor.execute(eq(mcpClient), any(McpClientOperation.class), eq(EMPTY_FILTER)))
          .thenReturn(expectedResult);

      final var result = handler.handle(context, request);
      assertThat(result).isEqualTo(expectedResult);

      verify(remoteClientRegistry).closeClient(CLIENT_ID, mcpClient);
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpRemoteClientHandlerTest#transports")
    void closesNonCachedClientEvenWhenExecutionFails(
        McpRemoteClientTransportConfiguration transport) {
      final var request = createToolModeRequest(transport, false, LIST_TOOLS_OPERATION);

      when(remoteClientRegistry.getClient(CLIENT_ID, transport, false)).thenReturn(mcpClient);
      when(clientExecutor.execute(eq(mcpClient), any(McpClientOperation.class), eq(EMPTY_FILTER)))
          .thenThrow(new RuntimeException("Execution failed"));

      assertThatThrownBy(() -> handler.handle(context, request))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Execution failed");

      verify(remoteClientRegistry).closeClient(CLIENT_ID, mcpClient);
    }
  }

  private McpRemoteClientRequest createToolModeRequest(
      McpRemoteClientTransportConfiguration transport,
      boolean clientCache,
      McpClientOperationConfiguration operation) {
    return new McpRemoteClientRequest(
        new McpRemoteClientRequestData(
            transport,
            new McpRemoteClientOptionsConfiguration(clientCache),
            new ToolModeConfiguration(
                operation, new McpClientToolModeFiltersConfiguration(EMPTY_FILTER_CONFIGURATION))));
  }

  private McpRemoteClientRequest createStandaloneModeRequest(
      McpRemoteClientTransportConfiguration transport,
      boolean clientCache,
      McpStandaloneOperationConfiguration operation) {
    return new McpRemoteClientRequest(
        new McpRemoteClientRequestData(
            transport,
            new McpRemoteClientOptionsConfiguration(clientCache),
            new StandaloneModeConfiguration(
                operation,
                new McpClientStandaloneFiltersConfiguration(
                    EMPTY_FILTER_CONFIGURATION, null, null))));
  }

  static List<McpRemoteClientTransportConfiguration> transports() {
    return List.of(STREAMABLE_HTTP_TRANSPORT_CONFIG, SSE_TRANSPORT_CONFIG);
  }

  static List<Arguments> mcpOperationArguments() {
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
