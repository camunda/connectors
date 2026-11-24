/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import static io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc.McpRpcTestFixtures.EXAMPLE_META;
import static io.camunda.connector.agenticai.mcp.client.model.content.McpTextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.McpClientErrorCodes;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListPromptsResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourceTemplatesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourcesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.api.error.ConnectorException;
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

  @ParameterizedTest
  @EnumSource(Operation.class)
  void rejectsName_whenItIsAllowedAndDenied(Operation operation) {
    operation.assertRejectsAllowedAndDeniedName(mcpClient, testee());
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

      @Override
      void assertRejectsAllowedAndDeniedName(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        when(client.listTools())
            .thenReturn(
                McpSchema.ListToolsResult.builder(
                        List.of(McpSchema.Tool.builder(NAME, Map.of("type", "object")).build()))
                    .build());

        assertThat(testee.listTools(ALLOWED_AND_DENIED, Map.of()))
            .isEqualTo(new McpClientListToolsResult(List.of()));
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

      @Override
      void assertRejectsAllowedAndDeniedName(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        final var result =
            testee.callTool(
                Map.of("name", NAME, "arguments", Map.of()), ALLOWED_AND_DENIED, Map.of());

        assertThat(result)
            .isEqualTo(
                new McpClientCallToolResult(
                    NAME,
                    List.of(
                        textContent(
                            "Executing tool '%s' is not allowed by filter configuration: %s"
                                .formatted(NAME, ALLOWED_AND_DENIED))),
                    true));
        verifyNoInteractions(client);
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

      @Override
      void assertRejectsAllowedAndDeniedName(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        when(client.listResources())
            .thenReturn(
                McpSchema.ListResourcesResult.builder(
                        List.of(McpSchema.Resource.builder(NAME, "resource").build()))
                    .build());

        assertThat(testee.listResources(ALLOWED_AND_DENIED, Map.of()))
            .isEqualTo(new McpClientListResourcesResult(List.of()));
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

      @Override
      void assertRejectsAllowedAndDeniedName(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        assertThatThrownBy(
                () -> testee.readResource(Map.of("uri", NAME), ALLOWED_AND_DENIED, Map.of()))
            .isInstanceOfSatisfying(
                ConnectorException.class,
                e -> {
                  assertThat(e.getErrorCode())
                      .isEqualTo(McpClientErrorCodes.ERROR_CODE_READ_RESOURCE_ERROR);
                  assertThat(e.getMessage())
                      .isEqualTo(
                          "Reading resource '%s' is not allowed by filter configuration: %s"
                              .formatted(NAME, ALLOWED_AND_DENIED));
                });
        verifyNoInteractions(client);
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

      @Override
      void assertRejectsAllowedAndDeniedName(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        when(client.listResourceTemplates())
            .thenReturn(
                McpSchema.ListResourceTemplatesResult.builder(
                        List.of(McpSchema.ResourceTemplate.builder(NAME, "template").build()))
                    .build());

        assertThat(testee.listResourceTemplates(ALLOWED_AND_DENIED, Map.of()))
            .isEqualTo(new McpClientListResourceTemplatesResult(List.of()));
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

      @Override
      void assertRejectsAllowedAndDeniedName(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        when(client.listPrompts())
            .thenReturn(
                McpSchema.ListPromptsResult.builder(List.of(McpSchema.Prompt.builder(NAME).build()))
                    .build());

        assertThat(testee.listPrompts(ALLOWED_AND_DENIED, Map.of()))
            .isEqualTo(new McpClientListPromptsResult(List.of()));
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

      @Override
      void assertRejectsAllowedAndDeniedName(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        assertThatThrownBy(
                () -> testee.getPrompt(Map.of("name", NAME), ALLOWED_AND_DENIED, Map.of()))
            .isInstanceOfSatisfying(
                ConnectorException.class,
                e -> {
                  assertThat(e.getErrorCode())
                      .isEqualTo(McpClientErrorCodes.ERROR_CODE_GET_PROMPT_ERROR);
                  assertThat(e.getMessage())
                      .isEqualTo(
                          "Getting prompt '%s' is not allowed by filter configuration: %s"
                              .formatted(NAME, ALLOWED_AND_DENIED));
                });
        verifyNoInteractions(client);
      }
    };

    private static final AllowDenyList EVERYTHING = AllowDenyList.allowingEverything();
    private static final String NAME = "allowed-and-denied";
    private static final AllowDenyList ALLOWED_AND_DENIED =
        new AllowDenyList(List.of(NAME), List.of(NAME));

    abstract void assertPassesMeta(McpSyncClient client, McpSdkMcpClientDelegate testee);

    abstract void assertRejectsAllowedAndDeniedName(
        McpSyncClient client, McpSdkMcpClientDelegate testee);
  }
}
