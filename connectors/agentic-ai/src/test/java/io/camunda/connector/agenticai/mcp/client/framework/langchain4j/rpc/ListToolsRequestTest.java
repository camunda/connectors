/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyListBuilder;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListToolsRequestTest {

  private static final AllowDenyList EMPTY_FILTER = AllowDenyListBuilder.builder().build();

  @Mock private ToolSpecificationConverter toolSpecificationConverter;
  @Mock private McpClient mcpClient;

  @InjectMocks private ListToolsRequest testee;

  @Test
  void returnsEmptyList_whenNoToolsAvailable() {
    when(mcpClient.listTools()).thenReturn(Collections.emptyList());

    final var result = testee.execute(mcpClient, EMPTY_FILTER);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListToolsResult.class, res -> assertThat(res.toolDefinitions()).isEmpty());
  }

  @Test
  void returnsToolDefinitions_whenToolsAvailable() {
    final var toolSpec1 = createToolSpecification("tool1", "Tool 1 description");
    final var toolSpec2 = createToolSpecification("tool2", "Tool 2 description");
    final var toolDefinition1 = createToolDefinition("tool1", "Tool 1 description");
    final var toolDefinition2 = createToolDefinition("tool2", "Tool 2 description");

    when(mcpClient.listTools()).thenReturn(List.of(toolSpec1, toolSpec2));

    when(toolSpecificationConverter.asToolDefinition(toolSpec1)).thenReturn(toolDefinition1);
    when(toolSpecificationConverter.asToolDefinition(toolSpec2)).thenReturn(toolDefinition2);

    final var result = testee.execute(mcpClient, EMPTY_FILTER);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListToolsResult.class,
            res ->
                assertThat(res.toolDefinitions())
                    .containsExactly(toolDefinition1, toolDefinition2));
  }

  @Test
  void filtersTools_whenFilterConfigured() {
    final var toolSpec1 = createToolSpecification("allowed-tool", "Allowed tool");
    final var toolSpec2 = createToolSpecification("blocked-tool", "Blocked tool");
    final var toolDefinition1 = createToolDefinition("allowed-tool", "Allowed tool");
    final var filter =
        AllowDenyListBuilder.builder().allowed(List.of("allowed-tool")).denied(List.of()).build();

    when(mcpClient.listTools()).thenReturn(List.of(toolSpec1, toolSpec2));
    when(toolSpecificationConverter.asToolDefinition(toolSpec1)).thenReturn(toolDefinition1);

    final var result = testee.execute(mcpClient, filter);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListToolsResult.class,
            res -> {
              assertThat(res.toolDefinitions()).containsExactly(toolDefinition1);
            });
  }

  @Test
  void returnsEmptyList_whenAllToolsFiltered() {
    final var toolSpec1 = createToolSpecification("blocked-tool1", "Blocked tool 1");
    final var toolSpec2 = createToolSpecification("blocked-tool2", "Blocked tool 2");
    final var filter =
        AllowDenyListBuilder.builder().allowed(List.of("allowed-tool")).denied(List.of()).build();

    when(mcpClient.listTools()).thenReturn(List.of(toolSpec1, toolSpec2));

    final var result = testee.execute(mcpClient, filter);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListToolsResult.class, res -> assertThat(res.toolDefinitions()).isEmpty());
  }

  private ToolSpecification createToolSpecification(String name, String description) {
    return ToolSpecification.builder()
        .name(name)
        .description(description)
        .parameters(JsonObjectSchema.builder().build())
        .build();
  }

  private ToolDefinition createToolDefinition(String name, String description) {
    return ToolDefinition.builder()
        .name(name)
        .description(description)
        .inputSchema(Map.of("type", "object"))
        .build();
  }
}
