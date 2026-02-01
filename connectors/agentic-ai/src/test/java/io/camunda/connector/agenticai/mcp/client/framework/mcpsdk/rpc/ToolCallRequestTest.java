/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyListBuilder;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.model.message.content.BinaryContent;
import io.camunda.connector.agenticai.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.api.error.ConnectorException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.ThrowingConsumer;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ToolCallRequestTest {

  private static final AllowDenyList EMPTY_FILTER = AllowDenyList.allowingEverything();

  @Mock private McpSyncClient mcpClient;

  private final ToolCallRequest testee = new ToolCallRequest(new ObjectMapper());

  @Test
  void executesTool_whenToolAllowedByFilter() {
    when(mcpClient.callTool(any(McpSchema.CallToolRequest.class)))
        .thenReturn(callToolResult("Tool execution result"));
    when(mcpClient.getClientInfo()).thenReturn(new McpSchema.Implementation("test-client", "1.0"));

    final var result =
        testee.execute(
            mcpClient,
            EMPTY_FILTER,
            Map.of("name", "test-tool", "arguments", Map.of("arg1", "value1")));

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientCallToolResult.class,
            res -> {
              assertThat(res.name()).isEqualTo("test-tool");
              assertThat(res.isError()).isFalse();
              assertThat(res.content()).containsExactly(textContent("Tool execution result"));
            });
  }

  @ParameterizedTest
  @MethodSource("variousContents")
  void returnsProperlyMappedToolContent_whenDifferentContentTypesReturned(
      ToolCallExpectation expectation) {
    when(mcpClient.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(expectation.result);
    when(mcpClient.getClientInfo()).thenReturn(new McpSchema.Implementation("test-client", "1.0"));

    final var result =
        testee.execute(
            mcpClient,
            EMPTY_FILTER,
            Map.of("name", "a-name", "arguments", Map.of("arg1", "value1")));

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientCallToolResult.class,
            toolCallResult -> {
              assertThat(toolCallResult).isEqualTo(expectation.domainResult);
            });
  }

  @ParameterizedTest
  @NullAndEmptySource
  void handlesEmptyArguments(Map<String, Object> arguments) {
    when(mcpClient.callTool(any(McpSchema.CallToolRequest.class)))
        .thenReturn(callToolResult("Success with no args"));
    when(mcpClient.getClientInfo()).thenReturn(new McpSchema.Implementation("test-client", "1.0"));

    final var parameters = new LinkedHashMap<String, Object>();
    parameters.put("name", "test-tool");
    if (arguments != null) {
      parameters.put("arguments", arguments);
    }

    final var result = testee.execute(mcpClient, EMPTY_FILTER, parameters);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientCallToolResult.class,
            res -> {
              assertThat(res.name()).isEqualTo("test-tool");
              assertThat(res.isError()).isFalse();
              assertThat(res.content()).containsExactly(textContent("Success with no args"));
            });
  }

  @ParameterizedTest
  @MethodSource("toolExecutionArguments")
  void handlesDifferentTypesOfArguments(String toolName, Map<String, Object> arguments) {
    when(mcpClient.callTool(any(McpSchema.CallToolRequest.class)))
        .thenReturn(callToolResult("Successful result"));
    when(mcpClient.getClientInfo()).thenReturn(new McpSchema.Implementation("test-client", "1.0"));

    final var result =
        testee.execute(mcpClient, EMPTY_FILTER, Map.of("name", toolName, "arguments", arguments));

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientCallToolResult.class,
            res -> {
              assertThat(res.name()).isEqualTo(toolName);
              assertThat(res.isError()).isFalse();
              assertThat(res.content()).containsExactly(textContent("Successful result"));
            });
  }

  @Test
  void returnsDefaultResponseText_whenResponseContentIsEmpty() {
    when(mcpClient.callTool(any(McpSchema.CallToolRequest.class)))
        .thenReturn(new McpSchema.CallToolResult(List.of(), false, null, null));
    when(mcpClient.getClientInfo()).thenReturn(new McpSchema.Implementation("test-client", "1.0"));

    final var result =
        testee.execute(
            mcpClient,
            EMPTY_FILTER,
            Map.of("name", "test-tool", "arguments", Map.of("arg1", "value1")));

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientCallToolResult.class,
            res -> {
              assertThat(res.name()).isEqualTo("test-tool");
              assertThat(res.isError()).isFalse();
              assertThat(res.content())
                  .containsExactly(
                      textContent("Tool execution succeeded, but returned no result."));
            });
  }

  @Test
  void throwsException_whenToolNameIsNotPresent() {
    reset(mcpClient);

    assertThatThrownBy(
            () ->
                testee.execute(
                    mcpClient, EMPTY_FILTER, Map.of("arguments", Map.of("arg1", "value1"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Tool name must not be null");
  }

  @Test
  void returnsError_whenToolNotIncludedInFilter() {
    final var filter = AllowDenyListBuilder.builder().allowed(List.of("allowed-tool")).build();
    when(mcpClient.getClientInfo()).thenReturn(new McpSchema.Implementation("test-client", "1.0"));

    final var result =
        testee.execute(
            mcpClient,
            filter,
            Map.of("name", "blocked-tool", "arguments", Map.of("arg1", "value1")));

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientCallToolResult.class,
            res -> {
              assertThat(res.name()).isEqualTo("blocked-tool");
              assertThat(res.isError()).isTrue();
              assertThat(res.content())
                  .containsExactly(
                      textContent(
                          "Executing tool 'blocked-tool' is not allowed by filter configuration: [allowed=[allowed-tool], denied=[]]"));
            });
  }

  @Test
  void throwsException_whenInvalidCallToolOperationParamsProvided() {
    assertThatThrownBy(
            () ->
                testee.execute(
                    mcpClient,
                    EMPTY_FILTER,
                    Map.of("name", List.of("foo", "bar"), "something", "else")))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            ex -> {
              assertThat(ex.getErrorCode()).isEqualTo("MCP_CLIENT_INVALID_PARAMS");
              assertThat(ex)
                  .hasMessageStartingWith("Unable to convert parameters passed to MCP client:")
                  .hasMessageContaining(
                      "Cannot deserialize value of type `java.lang.String` from Array value");
            });
  }

  @Test
  void returnsError_whenToolExcludedInFilter() {
    final var filter = AllowDenyListBuilder.builder().denied(List.of("blocked-tool")).build();
    when(mcpClient.getClientInfo()).thenReturn(new McpSchema.Implementation("test-client", "1.0"));

    final var result =
        testee.execute(
            mcpClient,
            filter,
            Map.of("name", "blocked-tool", "arguments", Map.of("arg1", "value1")));

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientCallToolResult.class,
            res -> {
              assertThat(res.name()).isEqualTo("blocked-tool");
              assertThat(res.isError()).isTrue();
              assertThat(res.content())
                  .containsExactly(
                      textContent(
                          "Executing tool 'blocked-tool' is not allowed by filter configuration: [allowed=[], denied=[blocked-tool]]"));
            });
  }

  @Test
  void returnsError_whenToolExecutionFails() {
    when(mcpClient.callTool(any(McpSchema.CallToolRequest.class)))
        .thenThrow(new RuntimeException("Tool execution failed"));
    when(mcpClient.getClientInfo()).thenReturn(new McpSchema.Implementation("test-client", "1.0"));

    final var result =
        testee.execute(
            mcpClient,
            EMPTY_FILTER,
            Map.of("name", "failing-tool", "arguments", Map.of("arg1", "value1")));

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientCallToolResult.class,
            res -> {
              assertThat(res.name()).isEqualTo("failing-tool");
              assertThat(res.isError()).isTrue();
              assertThat(res.content())
                  .containsExactly(
                      textContent("Error executing tool 'failing-tool': Tool execution failed"));
            });
  }

  private static Stream<Arguments> variousContents() {
    return Stream.of(
        argumentSet(
            "text content",
            new ToolCallExpectation(
                callToolResult("This is a text"),
                new McpClientCallToolResult(
                    "a-name", List.of(new TextContent("This is a text", null)), false))),
        argumentSet(
            "image content",
            new ToolCallExpectation(
                callToolResult("image".getBytes(StandardCharsets.UTF_8)),
                new McpClientCallToolResult(
                    "a-name",
                    List.of(
                        new BinaryContent(
                            "image".getBytes(StandardCharsets.UTF_8), "image/png", null)),
                    false))),
        argumentSet(
            "structured content",
            new ToolCallExpectation(
                callToolResult(Map.of("key", "value", "key2", List.of(1, 2, 3))),
                new McpClientCallToolResult(
                    "a-name",
                    List.of(
                        new ObjectContent(Map.of("key", "value", "key2", List.of(1, 2, 3)), null)),
                    false))),
        argumentSet(
            "embedded resource - text",
            new ToolCallExpectation(
                callToolResultWithEmbeddedTextResource("uri://resource", "text/plain", "resource text"),
                new McpClientCallToolResult(
                    "a-name",
                    List.of(
                        new io.camunda.connector.agenticai.model.message.content.EmbeddedResourceContent(
                            new io.camunda.connector.agenticai.model.message.content.EmbeddedResourceContent.TextResource(
                                "uri://resource", "text/plain", "resource text"),
                            null)),
                    false))),
        argumentSet(
            "embedded resource - blob",
            new ToolCallExpectation(
                callToolResultWithEmbeddedBlobResource("uri://resource", "application/octet-stream", "blob data".getBytes(StandardCharsets.UTF_8)),
                new McpClientCallToolResult(
                    "a-name",
                    List.of(
                        new io.camunda.connector.agenticai.model.message.content.EmbeddedResourceContent(
                            new io.camunda.connector.agenticai.model.message.content.EmbeddedResourceContent.BlobResource(
                                "uri://resource", "application/octet-stream", "blob data".getBytes(StandardCharsets.UTF_8)),
                            null)),
                    false))),
        argumentSet(
            "resource link",
            new ToolCallExpectation(
                callToolResultWithResourceLink("uri://external-resource"),
                new McpClientCallToolResult(
                    "a-name",
                    List.of(
                        new io.camunda.connector.agenticai.model.message.content.ResourceLinkContent(
                            "uri://external-resource", null)),
                    false))));
  }

  private static McpSchema.CallToolResult callToolResult(String resultText) {
    return new McpSchema.CallToolResult(
        List.of(new McpSchema.TextContent(resultText)), false, null, null);
  }

  private static McpSchema.CallToolResult callToolResult(byte[] blob) {
    return new McpSchema.CallToolResult(
        List.of(
            new McpSchema.ImageContent(
                null, Base64.getEncoder().encodeToString(blob), "image/png")),
        false,
        null,
        null);
  }

  private static McpSchema.CallToolResult callToolResult(Object structuredContent) {
    return new McpSchema.CallToolResult(null, false, structuredContent, null);
  }

  private static McpSchema.CallToolResult callToolResultWithEmbeddedTextResource(
      String uri, String mimeType, String text) {
    return new McpSchema.CallToolResult(
        List.of(
            new McpSchema.EmbeddedResource(
                null, new McpSchema.TextResourceContents(uri, mimeType, text))),
        false,
        null,
        null);
  }

  private static McpSchema.CallToolResult callToolResultWithEmbeddedBlobResource(
      String uri, String mimeType, byte[] blob) {
    return new McpSchema.CallToolResult(
        List.of(
            new McpSchema.EmbeddedResource(
                null,
                new McpSchema.BlobResourceContents(
                    uri, mimeType, Base64.getEncoder().encodeToString(blob)))),
        false,
        null,
        null);
  }

  private static McpSchema.CallToolResult callToolResultWithResourceLink(String uri) {
    return new McpSchema.CallToolResult(
        List.of(new McpSchema.ResourceLink(uri, null)), false, null, null);
  }

  static Stream<Arguments> toolExecutionArguments() {
    return Stream.of(
        arguments("valid-tool", Map.of("key", "value")),
        arguments("tool-with-complex-args", Map.of("nested", Map.of("key", "value"))));
  }

  private record ToolCallExpectation(
      McpSchema.CallToolResult result, McpClientCallToolResult domainResult) {}
}
