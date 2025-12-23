/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpResource;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyListBuilder;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourcesResult;
import io.camunda.connector.agenticai.model.tool.ResourceDescription;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListResourcesRequestTest {

  private static final AllowDenyList EMPTY_FILTER = AllowDenyListBuilder.builder().build();

  @Mock private McpClient mcpClient;

  private ListResourcesRequest testee = new ListResourcesRequest();

  @Test
  void returnsEmptyList_whenNoResourcesAvailable() {
    when(mcpClient.listResources()).thenReturn(Collections.emptyList());

    final var result = testee.execute(mcpClient);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListResourcesResult.class, res -> assertThat(res.resources()).isEmpty());
  }

  @Test
  void returnsResourceDescriptions_whenResourcesAvailable() {
    final var mcpResource1 =
        createMcpResource("file://resource1.txt", "resource1", "A first resource", "text/plain");
    final var mcpResource2 =
        createMcpResource("file://resource2.md", "resource2", "A second resource", "text/markdown");

    final var resourceDescription1 =
        createResourceDescription(
            "file://resource1.txt", "resource1", "A first resource", "text/plain");
    final var resourceDescription2 =
        createResourceDescription(
            "file://resource2.md", "resource2", "A second resource", "text/markdown");

    when(mcpClient.listResources()).thenReturn(List.of(mcpResource1, mcpResource2));

    final var result = testee.execute(mcpClient);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListResourcesResult.class,
            res ->
                assertThat(res.resources())
                    .containsExactly(resourceDescription1, resourceDescription2));
  }

  //
  //    @Test
  //    void filtersTools_whenFilterConfigured() {
  //      final var toolSpec1 = createToolSpecification("allowed-tool", "Allowed tool");
  //      final var toolSpec2 = createToolSpecification("blocked-tool", "Blocked tool");
  //      final var toolDefinition1 = createToolDefinition("allowed-tool", "Allowed tool");
  //      final var filter =
  //          McpToolNameFilter.from(
  //              new McpClientToolsConfiguration(List.of("allowed-tool"), List.of()));
  //
  //      when(mcpClient.listTools()).thenReturn(List.of(toolSpec1, toolSpec2));
  //
  // when(toolSpecificationConverter.asToolDefinition(toolSpec1)).thenReturn(toolDefinition1);
  //
  //      final var result = executor.execute(mcpClient, new McpClientListToolsOperation(),
  // filter);
  //
  //      assertThat(result)
  //          .isInstanceOfSatisfying(
  //              McpClientListToolsResult.class,
  //              res -> {
  //                assertThat(res.toolDefinitions()).containsExactly(toolDefinition1);
  //              });
  //    }
  //
  //    @Test
  //    void returnsEmptyList_whenAllToolsFiltered() {
  //      final var toolSpec1 = createToolSpecification("blocked-tool1", "Blocked tool 1");
  //      final var toolSpec2 = createToolSpecification("blocked-tool2", "Blocked tool 2");
  //      final var filter =
  //          McpToolNameFilter.from(
  //              new McpClientToolsConfiguration(List.of("allowed-tool"), List.of()));
  //
  //      when(mcpClient.listTools()).thenReturn(List.of(toolSpec1, toolSpec2));
  //
  //      final var result = executor.execute(mcpClient, new McpClientListToolsOperation(),
  // filter);
  //
  //      assertThat(result)
  //          .isInstanceOfSatisfying(
  //              McpClientListToolsResult.class, res ->
  // assertThat(res.toolDefinitions()).isEmpty());
  //    }

  private McpResource createMcpResource(
      String uri, String name, String description, String mimeType) {
    return new McpResource(uri, name, description, mimeType);
  }

  private ResourceDescription createResourceDescription(
      String uri, String name, String description, String mimeType) {
    return new ResourceDescription(uri, name, description, mimeType);
  }
}
