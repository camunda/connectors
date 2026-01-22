/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.mcp.client.McpClientResultDocumentHandler;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientDelegate;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientExecutor;
import io.camunda.connector.agenticai.mcp.client.filters.FilterOptions;
import io.camunda.connector.agenticai.mcp.client.filters.FilterOptionsBuilder;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.result.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpClientExecutorTest {

  private static final FilterOptions EMPTY_FILTER = FilterOptionsBuilder.builder().build();

  @Mock private McpClientResultDocumentHandler mcpClientResultDocumentHandler;

  @Mock private McpClientDelegate mcpClient;

  private McpClientExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new McpClientExecutor(mcpClientResultDocumentHandler);
    lenient()
        .when(mcpClientResultDocumentHandler.convertBinariesToDocumentsIfPresent(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void returnsMcpListToolsResult_whenListToolsExecuted() {
    when(mcpClient.listTools(any())).thenReturn(new McpClientListToolsResult(List.of()));

    final var operation = McpClientOperation.of("tools/list");
    final var result = executor.execute(mcpClient, operation, EMPTY_FILTER);

    assertThat(result).isInstanceOf(McpClientListToolsResult.class);
  }

  @Test
  void returnsMcpCallToolResult_whenCallToolsExecuted() {
    when(mcpClient.callTool(anyMap(), any()))
        .thenReturn(new McpClientCallToolResult("tool-output", List.of(), false));

    final var operation =
        McpClientOperation.of(
            "tools/call", Map.of("name", "test-tool", "arguments", Map.of("arg1", "value1")));
    final var result = executor.execute(mcpClient, operation, EMPTY_FILTER);

    assertThat(result).isInstanceOf(McpClientCallToolResult.class);
  }

  @Test
  void returnsMcpListResourcesResult_whenListResourcesExecuted() {
    when(mcpClient.listResources(any())).thenReturn(new McpClientListResourcesResult(List.of()));

    final var operation = McpClientOperation.of("resources/list");
    final var result = executor.execute(mcpClient, operation, EMPTY_FILTER);

    assertThat(result).isInstanceOf(McpClientListResourcesResult.class);
  }

  @Test
  void returnsMcpListResourceTemplatesResult_whenListResourceTemplatesExecuted() {
    when(mcpClient.listResourceTemplates(any()))
        .thenReturn(new McpClientListResourceTemplatesResult(List.of()));

    final var operation = McpClientOperation.of("resources/templates/list");
    final var result = executor.execute(mcpClient, operation, EMPTY_FILTER);

    assertThat(result).isInstanceOf(McpClientListResourceTemplatesResult.class);
  }

  @Test
  void returnsMcpReadResourceResult_whenReadResourceExecuted() {
    when(mcpClient.readResource(anyMap(), any()))
        .thenReturn(new McpClientReadResourceResult(List.of()));
    final var operation =
        McpClientOperation.of("resources/read", Map.of("uri", "test-resource-uri"));
    final var result = executor.execute(mcpClient, operation, EMPTY_FILTER);

    assertThat(result).isInstanceOf(McpClientReadResourceResult.class);
  }

  @Test
  void returnsMcpListPromptsResult_whenListPromptsExecuted() {
    when(mcpClient.listPrompts(any())).thenReturn(new McpClientListPromptsResult(List.of()));

    final var operation = McpClientOperation.of("prompts/list");
    final var result = executor.execute(mcpClient, operation, EMPTY_FILTER);

    assertThat(result).isInstanceOf(McpClientListPromptsResult.class);
  }

  @Test
  void returnsMcpGetPromptResult_whenGetPromptExecuted() {
    when(mcpClient.getPrompt(anyMap(), any()))
        .thenReturn(new McpClientGetPromptResult("Code review", List.of()));
    final var operation = McpClientOperation.of("prompts/get", Map.of("name", "test-prompt"));

    final var result = executor.execute(mcpClient, operation, EMPTY_FILTER);

    assertThat(result).isInstanceOf(McpClientGetPromptResult.class);
  }
}
