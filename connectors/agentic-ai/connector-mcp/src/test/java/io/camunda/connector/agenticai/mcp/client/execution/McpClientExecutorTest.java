/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.mcp.client.McpClientResultDocumentHandler;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.filters.FilterOptions;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListPromptsResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourceTemplatesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourcesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.api.document.DocumentFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpClientExecutorTest {

  private static final Map<String, Object> META = Map.of("exampleMetaKey", "exampleMetaValue");

  private static final AllowDenyList TOOL_FILTERS = new AllowDenyList(List.of("tool"), List.of());
  private static final AllowDenyList RESOURCE_FILTERS =
      new AllowDenyList(List.of("resource"), List.of());
  private static final AllowDenyList PROMPT_FILTERS =
      new AllowDenyList(List.of("prompt"), List.of());
  private static final FilterOptions FILTER_OPTIONS =
      new FilterOptions(TOOL_FILTERS, RESOURCE_FILTERS, PROMPT_FILTERS);

  @Mock private DocumentFactory documentFactory;
  @Mock private McpClientDelegate delegate;

  private McpClientExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new McpClientExecutor(new McpClientResultDocumentHandler(documentFactory));
  }

  @Test
  void forwardsMeta_whenListingTools() {
    final var expectedResult = new McpClientListToolsResult(List.of());
    when(delegate.listTools(TOOL_FILTERS, META)).thenReturn(expectedResult);

    final var result =
        executor.execute(
            delegate, McpClientOperation.of("tools/list", Map.of(), META), FILTER_OPTIONS);

    assertThat(result).isEqualTo(expectedResult);
    verify(delegate).listTools(TOOL_FILTERS, META);
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void callsDelegateOnce_whenCallingTool() {
    final var params = Map.<String, Object>of("name", "tool", "arguments", Map.of());
    final var expectedResult = new McpClientCallToolResult("tool", List.of(), false);
    when(delegate.callTool(params, TOOL_FILTERS, META)).thenReturn(expectedResult);

    final var result =
        executor.execute(
            delegate, McpClientOperation.of("tools/call", params, META), FILTER_OPTIONS);

    assertThat(result).isEqualTo(expectedResult);
    verify(delegate).callTool(params, TOOL_FILTERS, META);
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void forwardsMeta_whenListingResources() {
    final var expectedResult = new McpClientListResourcesResult(List.of());
    when(delegate.listResources(RESOURCE_FILTERS, META)).thenReturn(expectedResult);

    final var result =
        executor.execute(
            delegate, McpClientOperation.of("resources/list", Map.of(), META), FILTER_OPTIONS);

    assertThat(result).isEqualTo(expectedResult);
    verify(delegate).listResources(RESOURCE_FILTERS, META);
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void forwardsMeta_whenListingResourceTemplates() {
    final var expectedResult = new McpClientListResourceTemplatesResult(List.of());
    when(delegate.listResourceTemplates(RESOURCE_FILTERS, META)).thenReturn(expectedResult);

    final var result =
        executor.execute(
            delegate,
            McpClientOperation.of("resources/templates/list", Map.of(), META),
            FILTER_OPTIONS);

    assertThat(result).isEqualTo(expectedResult);
    verify(delegate).listResourceTemplates(RESOURCE_FILTERS, META);
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void forwardsMeta_whenListingPrompts() {
    final var expectedResult = new McpClientListPromptsResult(List.of());
    when(delegate.listPrompts(PROMPT_FILTERS, META)).thenReturn(expectedResult);

    final var result =
        executor.execute(
            delegate, McpClientOperation.of("prompts/list", Map.of(), META), FILTER_OPTIONS);

    assertThat(result).isEqualTo(expectedResult);
    verify(delegate).listPrompts(PROMPT_FILTERS, META);
    verifyNoMoreInteractions(delegate);
  }
}
