/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyListBuilder;
import io.camunda.connector.agenticai.mcp.client.model.McpToolDefinition;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListToolsRequestTest {

  private static final AllowDenyList EMPTY_FILTER = AllowDenyList.allowingEverything();

  @Mock private McpSyncClient mcpClient;

  private final ListToolsRequest testee = new ListToolsRequest("testClient", new ObjectMapper());

  @Test
  void returnsEmptyList_whenNoToolsAvailable() {
    when(mcpClient.listTools())
        .thenReturn(new McpSchema.ListToolsResult(Collections.emptyList(), null));

    final var result = testee.execute(mcpClient, EMPTY_FILTER);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListToolsResult.class, res -> assertThat(res.toolDefinitions()).isEmpty());
  }

  @Test
  void returnsToolDefinitions_whenToolsAvailable() {
    final var toolSpec1 = createTool("tool1", "Tool 1 description");
    final var toolSpec2 = createTool("tool2", "Tool 2 description");

    when(mcpClient.listTools())
        .thenReturn(new McpSchema.ListToolsResult(List.of(toolSpec1, toolSpec2), null));

    final var result = testee.execute(mcpClient, EMPTY_FILTER);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListToolsResult.class,
            res -> {
              assertThat(res.toolDefinitions()).hasSize(2);
              assertThat(res.toolDefinitions())
                  .extracting(McpToolDefinition::name)
                  .containsExactly("tool1", "tool2");
              assertThat(res.toolDefinitions())
                  .extracting(McpToolDefinition::description)
                  .containsExactly("Tool 1 description", "Tool 2 description");
            });
  }

  @Test
  void filtersTools_whenFilterConfigured() {
    final var toolSpec1 = createTool("allowed-tool", "Allowed tool");
    final var toolSpec2 = createTool("blocked-tool", "Blocked tool");
    final var filter =
        AllowDenyListBuilder.builder().allowed(List.of("allowed-tool")).denied(List.of()).build();

    when(mcpClient.listTools())
        .thenReturn(new McpSchema.ListToolsResult(List.of(toolSpec1, toolSpec2), null));

    final var result = testee.execute(mcpClient, filter);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListToolsResult.class,
            res -> {
              assertThat(res.toolDefinitions()).hasSize(1);
              assertThat(res.toolDefinitions().getFirst().name()).isEqualTo("allowed-tool");
            });
  }

  @Test
  void returnsEmptyList_whenAllToolsFiltered() {
    final var toolSpec1 = createTool("blocked-tool1", "Blocked tool 1");
    final var toolSpec2 = createTool("blocked-tool2", "Blocked tool 2");
    final var filter =
        AllowDenyListBuilder.builder().allowed(List.of("allowed-tool")).denied(List.of()).build();

    when(mcpClient.listTools())
        .thenReturn(new McpSchema.ListToolsResult(List.of(toolSpec1, toolSpec2), null));

    final var result = testee.execute(mcpClient, filter);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListToolsResult.class, res -> assertThat(res.toolDefinitions()).isEmpty());
  }

  private McpSchema.Tool createTool(String name, String description) {
    return new McpSchema.Tool(
        name,
        "",
        description,
        new McpSchema.JsonSchema(
            "object",
            Map.of("toolArg", Map.of("type", "string", "description", "A tool argument")),
            List.of("toolArg"),
            null,
            null,
            null),
        null,
        null,
        null);
  }
}
