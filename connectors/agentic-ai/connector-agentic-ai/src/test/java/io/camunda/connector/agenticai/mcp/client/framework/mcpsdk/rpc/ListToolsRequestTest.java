/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

  private final ListToolsRequest testee = new ListToolsRequest("testClient");

  @Test
  void returnsEmptyList_whenNoToolsAvailable() {
    when(mcpClient.listTools(null, null))
        .thenReturn(new McpSchema.ListToolsResult(Collections.emptyList(), null));

    final var result = testee.execute(mcpClient, EMPTY_FILTER, null);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListToolsResult.class, res -> assertThat(res.toolDefinitions()).isEmpty());
  }

  @Test
  void returnsToolDefinitions_whenToolsAvailable() {
    final var toolSpec1 = createTool("tool1", "First Tool", "Tool 1 description");
    final var toolSpec2 = createTool("tool2", "Second Tool", "Tool 2 description");

    when(mcpClient.listTools(null, null))
        .thenReturn(new McpSchema.ListToolsResult(List.of(toolSpec1, toolSpec2), null));

    final var result = testee.execute(mcpClient, EMPTY_FILTER, null);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListToolsResult.class,
            res -> {
              assertThat(res.toolDefinitions()).hasSize(2);
              assertThat(res.toolDefinitions())
                  .extracting(McpToolDefinition::name)
                  .containsExactly("tool1", "tool2");
              assertThat(res.toolDefinitions())
                  .extracting(McpToolDefinition::title)
                  .containsExactly("First Tool", "Second Tool");
              assertThat(res.toolDefinitions())
                  .extracting(McpToolDefinition::description)
                  .containsExactly("Tool 1 description", "Tool 2 description");
            });
  }

  @Test
  void filtersTools_whenFilterConfigured() {
    final var toolSpec1 = createTool("allowed-tool", "Allowed Tool", "Allowed tool");
    final var toolSpec2 = createTool("blocked-tool", "Blocked Tool", "Blocked tool");
    final var filter =
        AllowDenyListBuilder.builder().allowed(List.of("allowed-tool")).denied(List.of()).build();

    when(mcpClient.listTools(null, null))
        .thenReturn(new McpSchema.ListToolsResult(List.of(toolSpec1, toolSpec2), null));

    final var result = testee.execute(mcpClient, filter, null);

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
    final var toolSpec1 = createTool("blocked-tool1", "Blocked Tool 1", "Blocked tool 1");
    final var toolSpec2 = createTool("blocked-tool2", "Blocked Tool 2", "Blocked tool 2");
    final var filter =
        AllowDenyListBuilder.builder().allowed(List.of("allowed-tool")).denied(List.of()).build();

    when(mcpClient.listTools(null, null))
        .thenReturn(new McpSchema.ListToolsResult(List.of(toolSpec1, toolSpec2), null));

    final var result = testee.execute(mcpClient, filter, null);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListToolsResult.class, res -> assertThat(res.toolDefinitions()).isEmpty());
  }

  @Test
  void forwardsMetaUnmodified_whenMetaConfigured() {
    final var meta = Map.<String, Object>of("source_group_ids_include", List.of("version-uuid"));
    when(mcpClient.listTools(isNull(), eq(meta)))
        .thenReturn(new McpSchema.ListToolsResult(Collections.emptyList(), null));

    testee.execute(mcpClient, EMPTY_FILTER, meta);

    verify(mcpClient).listTools(isNull(), eq(meta));
  }

  @Test
  void doesNotSendMeta_whenMetaNotConfigured() {
    when(mcpClient.listTools(isNull(), isNull()))
        .thenReturn(new McpSchema.ListToolsResult(Collections.emptyList(), null));

    testee.execute(mcpClient, EMPTY_FILTER, null);

    verify(mcpClient).listTools(isNull(), isNull());
  }

  private McpSchema.Tool createTool(String name, String title, String description) {
    return McpSchema.Tool.builder(
            name,
            Map.of(
                "type", "object",
                "properties",
                    Map.of("toolArg", Map.of("type", "string", "description", "A tool argument")),
                "required", List.of("toolArg")))
        .title(title)
        .description(description)
        .build();
  }
}
