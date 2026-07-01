/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.handler;

import static io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpMethod.*;
import static io.camunda.connector.agenticai.mcp.client.model.content.McpTextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.mcp.client.McpClientRegistry;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientDelegate;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientExecutor;
import io.camunda.connector.agenticai.mcp.client.filters.FilterOptions;
import io.camunda.connector.agenticai.mcp.client.model.*;
import io.camunda.connector.agenticai.mcp.client.model.McpClientRequest.McpClientRequestData;
import io.camunda.connector.agenticai.mcp.client.model.McpClientRequest.McpClientRequestData.ClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.StandaloneModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.ToolModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.result.*;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultMcpClientHandlerTest {

  private static final String CLIENT_ID = "test-client";
  private static final ClientConfiguration CLIENT_CONFIG = new ClientConfiguration(CLIENT_ID);

  private static final McpClientOperationConfiguration LIST_TOOLS_OPERATION =
      new McpClientOperationConfiguration("tools/list", Map.of());

  private static final McpClientToolsFilterConfiguration EMPTY_FILTER_CONFIGURATION =
      new McpClientToolsFilterConfiguration(List.of(), List.of());
  private static final FilterOptions EMPTY_FILTER = FilterOptions.defaultOptions();

  @Mock private McpClientRegistry clientRegistry;
  @Mock private McpClientExecutor clientExecutor;

  @Mock private OutboundConnectorContext context;
  @Mock private McpClientDelegate mcpClient;

  private DefaultMcpClientHandler handler;

  @BeforeEach
  void setUp() {
    handler = new DefaultMcpClientHandler(clientRegistry, clientExecutor);
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

  @Test
  void usesDefaultOptions_whenConnectorModeDoesNotProvideFilters() {
    final var request =
        new McpClientRequest(
            new McpClientRequestData(
                CLIENT_CONFIG,
                new ToolModeConfiguration(LIST_TOOLS_OPERATION, null))); // No filters provided

    when(clientRegistry.getClient(CLIENT_ID)).thenReturn(mcpClient);
    when(clientExecutor.execute(eq(mcpClient), any(McpClientOperation.class), eq(EMPTY_FILTER)))
        .thenReturn(new McpClientListToolsResult(List.of()));

    handler.handle(context, request);
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
        "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpClientHandlerTest#mcpOperationArguments")
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
        "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpClientHandlerTest#mcpOperationArguments")
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

    @Test
    void handlesListResourcesRequest() {
      final var request =
          createStandaloneModeRequest(
              new McpStandaloneOperationConfiguration.ListResourcesOperationConfiguration());
      final var expectedResult = new McpClientListToolsResult(List.of());

      when(clientRegistry.getClient(CLIENT_ID)).thenReturn(mcpClient);
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

    @Test
    void handlesListResourceTemplatesRequest() {
      final var request =
          createStandaloneModeRequest(
              new McpStandaloneOperationConfiguration
                  .ListResourceTemplatesOperationConfiguration());
      final var expectedResult = new McpClientListResourceTemplatesResult(List.of());

      when(clientRegistry.getClient(CLIENT_ID)).thenReturn(mcpClient);
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

    @Test
    void handlesReadResourceRequest() {
      final var request =
          createStandaloneModeRequest(
              new McpStandaloneOperationConfiguration.ReadResourceOperationConfiguration(
                  "resource-1"));
      final var expectedResult =
          new McpClientReadResourceResult(
              List.of(new ResourceData.TextResourceData("uri", "text/plain", "Sample text")));

      when(clientRegistry.getClient(CLIENT_ID)).thenReturn(mcpClient);
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

    @Test
    void handlesListPromptsRequest() {
      final var request =
          createStandaloneModeRequest(
              new McpStandaloneOperationConfiguration.ListPromptsOperationConfiguration());
      final var expectedResult = new McpClientListPromptsResult(List.of());

      when(clientRegistry.getClient(CLIENT_ID)).thenReturn(mcpClient);
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
  }

  @ParameterizedTest
  @MethodSource(
      "io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpClientHandlerTest#mcpOperationArguments")
  void handlesGetPromptRequest(Map<String, Object> arguments) {
    final var request =
        createStandaloneModeRequest(
            new McpStandaloneOperationConfiguration.GetPromptOperationConfiguration(
                "code_review", arguments));
    final var expectedResult =
        new McpClientGetPromptResult(
            "Code review",
            List.of(
                new McpClientGetPromptResult.PromptMessage(
                    "user",
                    new McpClientGetPromptResult.TextMessage("Review code the code for me."))));

    when(clientRegistry.getClient(CLIENT_ID)).thenReturn(mcpClient);
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

  private McpClientRequest createToolModeRequest(McpClientOperationConfiguration operation) {
    return new McpClientRequest(
        new McpClientRequestData(
            CLIENT_CONFIG,
            new ToolModeConfiguration(
                operation, new McpClientToolModeFiltersConfiguration(EMPTY_FILTER_CONFIGURATION))));
  }

  private McpClientRequest createStandaloneModeRequest(
      McpStandaloneOperationConfiguration operation) {
    return new McpClientRequest(
        new McpClientRequestData(
            CLIENT_CONFIG,
            new StandaloneModeConfiguration(
                operation,
                new McpClientStandaloneFiltersConfiguration(
                    EMPTY_FILTER_CONFIGURATION, null, null))));
  }

  static Stream<Map<String, Object>> mcpOperationArguments() {
    return Stream.of(
        Map.of("arg1", "value1", "arg2", 5),
        Map.of("nested", Map.of("key", "value"), "array", List.of(1, 2, 3)));
  }
}
