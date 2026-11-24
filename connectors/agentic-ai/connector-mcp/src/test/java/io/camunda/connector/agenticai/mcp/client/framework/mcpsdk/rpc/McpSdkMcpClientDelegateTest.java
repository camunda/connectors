/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import static io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc.McpRpcTestFixtures.EXAMPLE_META;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpSdkMcpClientDelegateTest {

  @Mock private McpSyncClient mcpClient;

  private McpSdkMcpClientDelegate testee() {
    return new McpSdkMcpClientDelegate("testClient", mcpClient, new ObjectMapper());
  }

  @ParameterizedTest
  @EnumSource(Operation.class)
  void passesMetaToTheSdkClient(Operation operation) {
    operation.assertPassesMeta(mcpClient, testee());
    verifyNoMoreInteractions(mcpClient);
  }

  /** Each operation of the delegate. */
  enum Operation {
    LIST_TOOLS {
      @Override
      void assertPassesMeta(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        when(client.listTools(isNull(), same(EXAMPLE_META)))
            .thenReturn(McpSchema.ListToolsResult.builder(List.of()).build());

        testee.listTools(EVERYTHING, EXAMPLE_META);

        verify(client).listTools(null, EXAMPLE_META);
      }
    },
    CALL_TOOL {
      @Override
      void assertPassesMeta(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        when(client.callTool(any()))
            .thenReturn(McpSchema.CallToolResult.builder(List.of()).build());

        testee.callTool(Map.of("name", "tool", "arguments", Map.of()), EVERYTHING, EXAMPLE_META);

        final var request = ArgumentCaptor.forClass(McpSchema.CallToolRequest.class);
        verify(client).callTool(request.capture());
        assertThat(request.getValue().meta()).isEqualTo(EXAMPLE_META);
      }
    },
    LIST_RESOURCES {
      @Override
      void assertPassesMeta(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        when(client.listResources(isNull(), same(EXAMPLE_META)))
            .thenReturn(McpSchema.ListResourcesResult.builder(List.of()).build());

        testee.listResources(EVERYTHING, EXAMPLE_META);

        verify(client).listResources(null, EXAMPLE_META);
      }
    },
    READ_RESOURCE {
      @Override
      void assertPassesMeta(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        when(client.readResource(any(McpSchema.ReadResourceRequest.class)))
            .thenReturn(McpSchema.ReadResourceResult.builder(List.of()).build());

        testee.readResource(Map.of("uri", "resource://a"), EVERYTHING, EXAMPLE_META);

        final var request = ArgumentCaptor.forClass(McpSchema.ReadResourceRequest.class);
        verify(client).readResource(request.capture());
        assertThat(request.getValue().meta()).isEqualTo(EXAMPLE_META);
      }
    },
    LIST_RESOURCE_TEMPLATES {
      @Override
      void assertPassesMeta(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        when(client.listResourceTemplates(isNull(), same(EXAMPLE_META)))
            .thenReturn(McpSchema.ListResourceTemplatesResult.builder(List.of()).build());

        testee.listResourceTemplates(EVERYTHING, EXAMPLE_META);

        verify(client).listResourceTemplates(null, EXAMPLE_META);
      }
    },
    LIST_PROMPTS {
      @Override
      void assertPassesMeta(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        when(client.listPrompts(isNull(), same(EXAMPLE_META)))
            .thenReturn(McpSchema.ListPromptsResult.builder(List.of()).build());

        testee.listPrompts(EVERYTHING, EXAMPLE_META);

        verify(client).listPrompts(null, EXAMPLE_META);
      }
    },
    GET_PROMPT {
      @Override
      void assertPassesMeta(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        when(client.getPrompt(any()))
            .thenReturn(McpSchema.GetPromptResult.builder(List.of()).build());

        testee.getPrompt(Map.of("name", "prompt"), EVERYTHING, EXAMPLE_META);

        final var request = ArgumentCaptor.forClass(McpSchema.GetPromptRequest.class);
        verify(client).getPrompt(request.capture());
        assertThat(request.getValue().meta()).isEqualTo(EXAMPLE_META);
      }
    };

    private static final AllowDenyList EVERYTHING = AllowDenyList.allowingEverything();

    abstract void assertPassesMeta(McpSyncClient client, McpSdkMcpClientDelegate testee);
  }
}
