/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyListBuilder;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourceTemplatesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceTemplate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListResourceTemplatesRequestTest {

  private static final AllowDenyList EMPTY_FILTER = AllowDenyList.allowingEverything();

  @Mock private McpClient mcpClient;

  private final ListResourceTemplatesRequest testee = new ListResourceTemplatesRequest();

  @Test
  void returnsEmptyList_whenNoResourcesAvailable() {
    when(mcpClient.listResourceTemplates()).thenReturn(Collections.emptyList());

    final var result = testee.execute(mcpClient, EMPTY_FILTER);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListResourceTemplatesResult.class,
            res -> assertThat(res.resourceTemplates()).isEmpty());
  }

  @Test
  void returnsResourceDescriptions_whenResourceTemplatesAvailable() {
    final var mcpResourceTemplate1 =
        createMcpResourceTemplate(
            "file://resource1.txt", "resource1", "A first resource", "text/plain");
    final var mcpResourceTemplate2 =
        createMcpResourceTemplate(
            "file://resource2.md", "resource2", "A second resource", "text/markdown");

    final var resourceTemplate1 =
        createResourceTemplate(
            "file://resource1.txt", "resource1", "A first resource", "text/plain");
    final var resourceTemplate2 =
        createResourceTemplate(
            "file://resource2.md", "resource2", "A second resource", "text/markdown");

    when(mcpClient.listResourceTemplates())
        .thenReturn(List.of(mcpResourceTemplate1, mcpResourceTemplate2));

    final var result = testee.execute(mcpClient, EMPTY_FILTER);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListResourceTemplatesResult.class,
            res ->
                assertThat(res.resourceTemplates())
                    .containsExactly(resourceTemplate1, resourceTemplate2));
  }

  @Test
  void filtersResourceTemplates_whenFilterConfigured() {
    final var mcpResourceTemplate1 =
        createMcpResourceTemplate(
            "file://allowed-template.txt", "allowed-template", "Allowed template", "text/plain");
    final var mcpResourceTemplate2 =
        createMcpResourceTemplate(
            "file://blocked-template.txt", "blocked-template", "Blocked template", "text/plain");
    final var resourceTemplate1 =
        createResourceTemplate(
            "file://allowed-template.txt", "allowed-template", "Allowed template", "text/plain");
    final var filter =
        AllowDenyListBuilder.builder()
            .allowed(List.of("file://allowed-template.txt"))
            .denied(List.of())
            .build();

    when(mcpClient.listResourceTemplates())
        .thenReturn(List.of(mcpResourceTemplate1, mcpResourceTemplate2));

    final var result = testee.execute(mcpClient, filter);

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
            "Blocked template 1",
            "text/plain");
    final var mcpResourceTemplate2 =
        createMcpResourceTemplate(
            "file://blocked-template2.txt",
            "blocked-template2",
            "Blocked template 2",
            "text/plain");
    final var filter =
        AllowDenyListBuilder.builder()
            .allowed(List.of("file://allowed-template.txt"))
            .denied(List.of())
            .build();

    when(mcpClient.listResourceTemplates())
        .thenReturn(List.of(mcpResourceTemplate1, mcpResourceTemplate2));

    final var result = testee.execute(mcpClient, filter);

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
            "Blocked template 1",
            "text/plain");
    final var filter =
        AllowDenyListBuilder.builder()
            .allowed(List.of("file://allowed-template.txt"))
            .denied(List.of("file://allowed-template.txt"))
            .build();

    when(mcpClient.listResourceTemplates()).thenReturn(List.of(mcpResourceTemplate1));

    final var result = testee.execute(mcpClient, filter);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListResourceTemplatesResult.class,
            res -> assertThat(res.resourceTemplates()).isEmpty());
  }

  private McpResourceTemplate createMcpResourceTemplate(
      String uri, String name, String description, String mimeType) {
    return new McpResourceTemplate(uri, name, description, mimeType);
  }

  private ResourceTemplate createResourceTemplate(
      String uri, String name, String description, String mimeType) {
    return new ResourceTemplate(uri, name, description, mimeType);
  }
}
