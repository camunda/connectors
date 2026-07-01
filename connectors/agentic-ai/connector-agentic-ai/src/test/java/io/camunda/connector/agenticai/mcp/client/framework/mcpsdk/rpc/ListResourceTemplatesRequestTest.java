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
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourceTemplatesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceTemplate;
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
class ListResourceTemplatesRequestTest {

  private static final AllowDenyList EMPTY_FILTER = AllowDenyList.allowingEverything();

  @Mock private McpSyncClient mcpClient;

  private final ListResourceTemplatesRequest testee =
      new ListResourceTemplatesRequest("testClient");

  @Test
  void returnsEmptyList_whenNoResourcesAvailable() {
    when(mcpClient.listResourceTemplates(null, null))
        .thenReturn(new McpSchema.ListResourceTemplatesResult(Collections.emptyList(), null));

    final var result = testee.execute(mcpClient, EMPTY_FILTER, null);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListResourceTemplatesResult.class,
            res -> assertThat(res.resourceTemplates()).isEmpty());
  }

  @Test
  void returnsResourceDescriptions_whenResourceTemplatesAvailable() {
    final var mcpResourceTemplate1 =
        createMcpResourceTemplate(
            "file://resource1.txt",
            "resource1",
            "First Resource Template",
            "A first resource",
            "text/plain");
    final var mcpResourceTemplate2 =
        createMcpResourceTemplate(
            "file://resource2.md",
            "resource2",
            "Second Resource Template",
            "A second resource",
            "text/markdown");

    final var resourceTemplate1 =
        createResourceTemplate(
            "file://resource1.txt",
            "resource1",
            "First Resource Template",
            "A first resource",
            "text/plain");
    final var resourceTemplate2 =
        createResourceTemplate(
            "file://resource2.md",
            "resource2",
            "Second Resource Template",
            "A second resource",
            "text/markdown");

    when(mcpClient.listResourceTemplates(null, null))
        .thenReturn(
            new McpSchema.ListResourceTemplatesResult(
                List.of(mcpResourceTemplate1, mcpResourceTemplate2), null));

    final var result = testee.execute(mcpClient, EMPTY_FILTER, null);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListResourceTemplatesResult.class,
            res -> {
              assertThat(res.resourceTemplates())
                  .containsExactly(resourceTemplate1, resourceTemplate2);
              assertThat(res.resourceTemplates())
                  .extracting(ResourceTemplate::title)
                  .containsExactly("First Resource Template", "Second Resource Template");
            });
  }

  @Test
  void filtersResourceTemplates_whenFilterConfigured() {
    final var mcpResourceTemplate1 =
        createMcpResourceTemplate(
            "file://allowed-template.txt",
            "allowed-template",
            "Allowed Template",
            "Allowed template",
            "text/plain");
    final var mcpResourceTemplate2 =
        createMcpResourceTemplate(
            "file://blocked-template.txt",
            "blocked-template",
            "Blocked Template",
            "Blocked template",
            "text/plain");
    final var resourceTemplate1 =
        createResourceTemplate(
            "file://allowed-template.txt",
            "allowed-template",
            "Allowed Template",
            "Allowed template",
            "text/plain");
    final var filter =
        AllowDenyListBuilder.builder()
            .allowed(List.of("file://allowed-template.txt"))
            .denied(List.of())
            .build();

    when(mcpClient.listResourceTemplates(null, null))
        .thenReturn(
            new McpSchema.ListResourceTemplatesResult(
                List.of(mcpResourceTemplate1, mcpResourceTemplate2), null));

    final var result = testee.execute(mcpClient, filter, null);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListResourceTemplatesResult.class,
            res -> assertThat(res.resourceTemplates()).containsExactly(resourceTemplate1));
  }

  @Test
  void returnsEmptyList_whenAllResourceTemplatesFiltered() {
    final var mcpResourceTemplate1 =
        createMcpResourceTemplate(
            "file://blocked-template1.txt",
            "blocked-template1",
            "Blocked Template 1",
            "Blocked template 1",
            "text/plain");
    final var mcpResourceTemplate2 =
        createMcpResourceTemplate(
            "file://blocked-template2.txt",
            "blocked-template2",
            "Blocked Template 2",
            "Blocked template 2",
            "text/plain");
    final var filter =
        AllowDenyListBuilder.builder()
            .allowed(List.of("file://allowed-template.txt"))
            .denied(List.of())
            .build();

    when(mcpClient.listResourceTemplates(null, null))
        .thenReturn(
            new McpSchema.ListResourceTemplatesResult(
                List.of(mcpResourceTemplate1, mcpResourceTemplate2), null));

    final var result = testee.execute(mcpClient, filter, null);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListResourceTemplatesResult.class,
            res -> assertThat(res.resourceTemplates()).isEmpty());
  }

  @Test
  void returnsEmptyList_whenResourceTemplatesAllowedAndDeniedSimultaneously() {
    final var mcpResourceTemplate1 =
        createMcpResourceTemplate(
            "file://blocked-template1.txt",
            "blocked-template1",
            "Blocked Template 1",
            "Blocked template 1",
            "text/plain");
    final var filter =
        AllowDenyListBuilder.builder()
            .allowed(List.of("file://allowed-template.txt"))
            .denied(List.of("file://allowed-template.txt"))
            .build();

    when(mcpClient.listResourceTemplates(null, null))
        .thenReturn(new McpSchema.ListResourceTemplatesResult(List.of(mcpResourceTemplate1), null));

    final var result = testee.execute(mcpClient, filter, null);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListResourceTemplatesResult.class,
            res -> assertThat(res.resourceTemplates()).isEmpty());
  }

  @Test
  void forwardsMetaUnmodified_whenMetaConfigured() {
    final var meta = Map.<String, Object>of("source_group_ids_include", List.of("version-uuid"));
    when(mcpClient.listResourceTemplates(isNull(), eq(meta)))
        .thenReturn(new McpSchema.ListResourceTemplatesResult(Collections.emptyList(), null));

    testee.execute(mcpClient, EMPTY_FILTER, meta);

    verify(mcpClient).listResourceTemplates(isNull(), eq(meta));
  }

  @Test
  void doesNotSendMeta_whenMetaNotConfigured() {
    when(mcpClient.listResourceTemplates(isNull(), isNull()))
        .thenReturn(new McpSchema.ListResourceTemplatesResult(Collections.emptyList(), null));

    testee.execute(mcpClient, EMPTY_FILTER, null);

    verify(mcpClient).listResourceTemplates(isNull(), isNull());
  }

  private McpSchema.ResourceTemplate createMcpResourceTemplate(
      String uriTemplate, String name, String title, String description, String mimeType) {
    return McpSchema.ResourceTemplate.builder(uriTemplate, name)
        .title(title)
        .description(description)
        .mimeType(mimeType)
        .build();
  }

  private ResourceTemplate createResourceTemplate(
      String uriTemplate, String name, String title, String description, String mimeType) {
    return new ResourceTemplate(uriTemplate, name, title, description, mimeType);
  }
}
