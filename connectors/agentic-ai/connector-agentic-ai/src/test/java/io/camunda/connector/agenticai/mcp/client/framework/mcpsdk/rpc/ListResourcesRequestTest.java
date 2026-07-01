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
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourcesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceDescription;
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
class ListResourcesRequestTest {

  private static final AllowDenyList EMPTY_FILTER = AllowDenyList.allowingEverything();

  @Mock private McpSyncClient mcpClient;

  private final ListResourcesRequest testee = new ListResourcesRequest("testClient");

  @Test
  void returnsEmptyList_whenNoResourcesAvailable() {
    when(mcpClient.listResources(null, null))
        .thenReturn(new McpSchema.ListResourcesResult(Collections.emptyList(), null));

    final var result = testee.execute(mcpClient, EMPTY_FILTER, null);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListResourcesResult.class, res -> assertThat(res.resources()).isEmpty());
  }

  @Test
  void returnsResourceDescriptions_whenResourcesAvailable() {
    final var mcpResource1 =
        createMcpResource(
            "file://resource1.txt",
            "resource1",
            "First Resource",
            "A first resource",
            "text/plain");
    final var mcpResource2 =
        createMcpResource(
            "file://resource2.md",
            "resource2",
            "Second Resource",
            "A second resource",
            "text/markdown");

    final var resourceDescription1 =
        createResourceDescription(
            "file://resource1.txt",
            "resource1",
            "First Resource",
            "A first resource",
            "text/plain");
    final var resourceDescription2 =
        createResourceDescription(
            "file://resource2.md",
            "resource2",
            "Second Resource",
            "A second resource",
            "text/markdown");

    when(mcpClient.listResources(null, null))
        .thenReturn(new McpSchema.ListResourcesResult(List.of(mcpResource1, mcpResource2), null));

    final var result = testee.execute(mcpClient, EMPTY_FILTER, null);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListResourcesResult.class,
            res -> {
              assertThat(res.resources())
                  .containsExactly(resourceDescription1, resourceDescription2);
              assertThat(res.resources())
                  .extracting(ResourceDescription::title)
                  .containsExactly("First Resource", "Second Resource");
            });
  }

  @Test
  void filtersResources_whenFilterConfigured() {
    final var mcpResource1 =
        createMcpResource(
            "file://allowed-resource.txt",
            "allowed-resource",
            "Allowed Resource",
            "Allowed resource",
            "text/plain");
    final var mcpResource2 =
        createMcpResource(
            "file://blocked-resource.txt",
            "blocked-resource",
            "Blocked Resource",
            "Blocked resource",
            "text/plain");
    final var resourceDescription1 =
        createResourceDescription(
            "file://allowed-resource.txt",
            "allowed-resource",
            "Allowed Resource",
            "Allowed resource",
            "text/plain");
    final var filter =
        AllowDenyListBuilder.builder()
            .allowed(List.of("file://allowed-resource.txt"))
            .denied(List.of())
            .build();

    when(mcpClient.listResources(null, null))
        .thenReturn(new McpSchema.ListResourcesResult(List.of(mcpResource1, mcpResource2), null));

    final var result = testee.execute(mcpClient, filter, null);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListResourcesResult.class,
            res -> assertThat(res.resources()).containsExactly(resourceDescription1));
  }

  @Test
  void returnsEmptyList_whenAllResourcesFiltered() {
    final var mcpResource1 =
        createMcpResource(
            "file://blocked-resource1.txt",
            "blocked-resource1",
            "Blocked Resource 1",
            "Blocked resource 1",
            "text/plain");
    final var mcpResource2 =
        createMcpResource(
            "file://blocked-resource2.txt",
            "blocked-resource2",
            "Blocked Resource 2",
            "Blocked resource 2",
            "text/plain");
    final var filter =
        AllowDenyListBuilder.builder()
            .allowed(List.of("file://allowed-resource.txt"))
            .denied(List.of())
            .build();

    when(mcpClient.listResources(null, null))
        .thenReturn(new McpSchema.ListResourcesResult(List.of(mcpResource1, mcpResource2), null));

    final var result = testee.execute(mcpClient, filter, null);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListResourcesResult.class, res -> assertThat(res.resources()).isEmpty());
  }

  @Test
  void returnsEmptyList_whenResourceAllowedAndDeniedSimultaneously() {
    final var mcpResource1 =
        createMcpResource(
            "file://blocked-resource1.txt",
            "blocked-resource1",
            "Blocked Resource 1",
            "Blocked resource 1",
            "text/plain");
    final var filter =
        AllowDenyListBuilder.builder()
            .allowed(List.of("file://allowed-resource.txt"))
            .denied(List.of("file://allowed-resource.txt"))
            .build();

    when(mcpClient.listResources(null, null))
        .thenReturn(new McpSchema.ListResourcesResult(List.of(mcpResource1), null));

    final var result = testee.execute(mcpClient, filter, null);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListResourcesResult.class, res -> assertThat(res.resources()).isEmpty());
  }

  @Test
  void forwardsMetaUnmodified_whenMetaConfigured() {
    final var meta = Map.<String, Object>of("source_group_ids_include", List.of("version-uuid"));
    when(mcpClient.listResources(isNull(), eq(meta)))
        .thenReturn(new McpSchema.ListResourcesResult(Collections.emptyList(), null));

    testee.execute(mcpClient, EMPTY_FILTER, meta);

    verify(mcpClient).listResources(isNull(), eq(meta));
  }

  @Test
  void doesNotSendMeta_whenMetaNotConfigured() {
    when(mcpClient.listResources(isNull(), isNull()))
        .thenReturn(new McpSchema.ListResourcesResult(Collections.emptyList(), null));

    testee.execute(mcpClient, EMPTY_FILTER, null);

    verify(mcpClient).listResources(isNull(), isNull());
  }

  private McpSchema.Resource createMcpResource(
      String uri, String name, String title, String description, String mimeType) {
    return McpSchema.Resource.builder(uri, name)
        .title(title)
        .description(description)
        .mimeType(mimeType)
        .build();
  }

  private ResourceDescription createResourceDescription(
      String uri, String name, String title, String description, String mimeType) {
    return new ResourceDescription(uri, name, title, description, mimeType);
  }
}
