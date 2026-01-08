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
import dev.langchain4j.mcp.client.McpResource;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourcesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceDescription;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListResourcesRequestTest {

  @Mock private McpClient mcpClient;

  private final ListResourcesRequest testee = new ListResourcesRequest();

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

  private McpResource createMcpResource(
      String uri, String name, String description, String mimeType) {
    return new McpResource(uri, name, description, mimeType);
  }

  private ResourceDescription createResourceDescription(
      String uri, String name, String description, String mimeType) {
    return new ResourceDescription(uri, name, description, mimeType);
  }
}
