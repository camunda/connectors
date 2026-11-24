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
import io.camunda.connector.agenticai.mcp.client.model.McpToolDefinition;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.mcp.client.model.result.PromptDescription;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceDescription;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceTemplate;
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
  void passesOnlyTheNameThatIsAllowedAndNotDenied(Operation operation) {
    operation.assertAppliesFilter(mcpClient, testee());
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

      @Override
      void assertAppliesFilter(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        when(client.listTools())
            .thenReturn(
                McpSchema.ListToolsResult.builder(
                        NAMES.stream()
                            .map(name ->
                                McpSchema.Tool.builder(name, Map.of("type", "object")).build())
                            .toList())
                    .build());

        assertThat(testee.listTools(FILTER, Map.of()).toolDefinitions())
            .extracting(McpToolDefinition::name)
            .containsExactly(ALLOWED);
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
      void assertAppliesFilter(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        for (final var name : REJECTED) {
          assertThat(testee.callTool(Map.of("name", name, "arguments", Map.of()), FILTER, Map.of()))
              .isEqualTo(
                  new McpClientCallToolResult(
                      name,
                      List.of(
                          textContent(
                              "Executing tool '%s' is not allowed by filter configuration: %s"
                                  .formatted(name, FILTER))),
                      true));
        }
        verifyNoInteractions(client);
        when(client.callTool(any()))
            .thenReturn(McpSchema.CallToolResult.builder(List.of()).build());

        testee.callTool(Map.of("name", ALLOWED, "arguments", Map.of()), FILTER, Map.of());

        final var request = ArgumentCaptor.forClass(McpSchema.CallToolRequest.class);
        verify(client).callTool(request.capture());
        assertThat(request.getValue().name()).isEqualTo(ALLOWED);
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
      void assertAppliesFilter(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        when(client.listResources())
            .thenReturn(
                McpSchema.ListResourcesResult.builder(
                        NAMES.stream()
                            .map(uri -> McpSchema.Resource.builder(uri, "resource").build())
                            .toList())
                    .build());

        assertThat(testee.listResources(FILTER, Map.of()).resources())
            .extracting(ResourceDescription::uri)
            .containsExactly(ALLOWED);
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
      void assertAppliesFilter(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        for (final var uri : REJECTED) {
          assertThatThrownBy(() -> testee.readResource(Map.of("uri", uri), FILTER, Map.of()))
              .isInstanceOfSatisfying(
                  ConnectorException.class,
                  e -> {
                    assertThat(e.getErrorCode())
                        .isEqualTo(McpClientErrorCodes.ERROR_CODE_READ_RESOURCE_ERROR);
                    assertThat(e.getMessage())
                        .isEqualTo(
                            "Reading resource '%s' is not allowed by filter configuration: %s"
                                .formatted(uri, FILTER));
                  });
        }
        verifyNoInteractions(client);
        when(client.readResource(any(McpSchema.ReadResourceRequest.class)))
            .thenReturn(McpSchema.ReadResourceResult.builder(List.of()).build());

        testee.readResource(Map.of("uri", ALLOWED), FILTER, Map.of());

        final var request = ArgumentCaptor.forClass(McpSchema.ReadResourceRequest.class);
        verify(client).readResource(request.capture());
        assertThat(request.getValue().uri()).isEqualTo(ALLOWED);
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
      void assertAppliesFilter(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        when(client.listResourceTemplates())
            .thenReturn(
                McpSchema.ListResourceTemplatesResult.builder(
                        NAMES.stream()
                            .map(uri -> McpSchema.ResourceTemplate.builder(uri, "template").build())
                            .toList())
                    .build());

        assertThat(testee.listResourceTemplates(FILTER, Map.of()).resourceTemplates())
            .extracting(ResourceTemplate::uriTemplate)
            .containsExactly(ALLOWED);
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
      void assertAppliesFilter(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        when(client.listPrompts())
            .thenReturn(
                McpSchema.ListPromptsResult.builder(
                        NAMES.stream().map(name -> McpSchema.Prompt.builder(name).build()).toList())
                    .build());

        assertThat(testee.listPrompts(FILTER, Map.of()).promptDescriptions())
            .extracting(PromptDescription::name)
            .containsExactly(ALLOWED);
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
      void assertAppliesFilter(McpSyncClient client, McpSdkMcpClientDelegate testee) {
        for (final var name : REJECTED) {
          assertThatThrownBy(() -> testee.getPrompt(Map.of("name", name), FILTER, Map.of()))
              .isInstanceOfSatisfying(
                  ConnectorException.class,
                  e -> {
                    assertThat(e.getErrorCode())
                        .isEqualTo(McpClientErrorCodes.ERROR_CODE_GET_PROMPT_ERROR);
                    assertThat(e.getMessage())
                        .isEqualTo(
                            "Getting prompt '%s' is not allowed by filter configuration: %s"
                                .formatted(name, FILTER));
                  });
        }
        verifyNoInteractions(client);
        when(client.getPrompt(any()))
            .thenReturn(McpSchema.GetPromptResult.builder(List.of()).build());

        testee.getPrompt(Map.of("name", ALLOWED), FILTER, Map.of());

        final var request = ArgumentCaptor.forClass(McpSchema.GetPromptRequest.class);
        verify(client).getPrompt(request.capture());
        assertThat(request.getValue().name()).isEqualTo(ALLOWED);
      }
    };

    private static final AllowDenyList EVERYTHING = AllowDenyList.allowingEverything();
    private static final String ALLOWED = "allowed";
    private static final String ALLOWED_AND_DENIED = "allowed-and-denied";
    private static final String NOT_ALLOWED = "not-allowed";
    private static final List<String> NAMES = List.of(ALLOWED, ALLOWED_AND_DENIED, NOT_ALLOWED);
    private static final List<String> REJECTED = List.of(ALLOWED_AND_DENIED, NOT_ALLOWED);

    /** Denied wins over allowed, and a name that is not allowed is rejected. */
    private static final AllowDenyList FILTER =
        new AllowDenyList(List.of(ALLOWED, ALLOWED_AND_DENIED), List.of(ALLOWED_AND_DENIED));

    abstract void assertPassesMeta(McpSyncClient client, McpSdkMcpClientDelegate testee);

    abstract void assertAppliesFilter(McpSyncClient client, McpSdkMcpClientDelegate testee);
  }
}
