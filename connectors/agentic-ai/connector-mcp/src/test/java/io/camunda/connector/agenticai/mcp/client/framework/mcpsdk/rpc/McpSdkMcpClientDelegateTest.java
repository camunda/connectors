/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import static io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc.McpRpcTestFixtures.EXAMPLE_META;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpSdkMcpClientDelegateTest {

  private static final AllowDenyList FILTER = AllowDenyList.allowingEverything();

  @Mock private McpSyncClient mcpClient;

  private McpSdkMcpClientDelegate testee() {
    return new McpSdkMcpClientDelegate("testClient", mcpClient, new ObjectMapper());
  }

  private static McpSchema.ListToolsResult emptyTools() {
    return McpSchema.ListToolsResult.builder(Collections.emptyList()).build();
  }

  private static McpSchema.ListResourcesResult emptyResources() {
    return McpSchema.ListResourcesResult.builder(Collections.emptyList()).build();
  }

  private static McpSchema.ListResourceTemplatesResult emptyResourceTemplates() {
    return McpSchema.ListResourceTemplatesResult.builder(Collections.emptyList()).build();
  }

  private static McpSchema.ListPromptsResult emptyPrompts() {
    return McpSchema.ListPromptsResult.builder(Collections.emptyList()).build();
  }

  @Test
  void listToolsPassesMetaToTheSdkClient() {
    when(mcpClient.listTools(isNull(), same(EXAMPLE_META))).thenReturn(emptyTools());

    testee().listTools(FILTER, EXAMPLE_META);

    verify(mcpClient).listTools(null, EXAMPLE_META);
    verifyNoMoreInteractions(mcpClient);
  }

  @Test
  void listResourcesPassesMetaToTheSdkClient() {
    when(mcpClient.listResources(isNull(), same(EXAMPLE_META))).thenReturn(emptyResources());

    testee().listResources(FILTER, EXAMPLE_META);

    verify(mcpClient).listResources(null, EXAMPLE_META);
    verifyNoMoreInteractions(mcpClient);
  }

  @Test
  void listResourceTemplatesPassesMetaToTheSdkClient() {
    when(mcpClient.listResourceTemplates(isNull(), same(EXAMPLE_META)))
        .thenReturn(emptyResourceTemplates());

    testee().listResourceTemplates(FILTER, EXAMPLE_META);

    verify(mcpClient).listResourceTemplates(null, EXAMPLE_META);
    verifyNoMoreInteractions(mcpClient);
  }

  @Test
  void listPromptsPassesMetaToTheSdkClient() {
    when(mcpClient.listPrompts(isNull(), same(EXAMPLE_META))).thenReturn(emptyPrompts());

    testee().listPrompts(FILTER, EXAMPLE_META);

    verify(mcpClient).listPrompts(null, EXAMPLE_META);
    verifyNoMoreInteractions(mcpClient);
  }
}
