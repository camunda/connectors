/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyListBuilder;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientGetPromptResult;
import io.camunda.connector.api.error.ConnectorException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetPromptRequestTest {

  private static final AllowDenyList EMPTY_FILTER = AllowDenyList.allowingEverything();

  @Mock private McpSyncClient mcpClient;

  private GetPromptRequest testee;

  @BeforeEach
  void setUp() {
    testee = new GetPromptRequest();
  }

  @ParameterizedTest
  @NullAndEmptySource
  void handlesEmptyArguments(Map<String, Object> arguments) {
    when(mcpClient.getPrompt(any(McpSchema.GetPromptRequest.class)))
        .thenReturn(
            mcpPromptResult(
                "Code Review",
                List.of(
                    new McpSchema.PromptMessage(
                        McpSchema.Role.USER,
                        new McpSchema.TextContent("Please review the following code snippet.")))));
    when(mcpClient.getClientInfo()).thenReturn(new McpSchema.Implementation("test-client", "1.0"));

    final var parameters = new LinkedHashMap<String, Object>();
    parameters.put("name", "code_review");
    if (arguments != null) {
      parameters.put("arguments", arguments);
    }

    final var result = testee.execute(mcpClient, EMPTY_FILTER, parameters);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientGetPromptResult.class,
            res -> {
              assertThat(res.description()).isEqualTo("Code Review");
              assertThat(res.messages())
                  .hasSize(1)
                  .first()
                  .satisfies(
                      promptMessage -> {
                        assertThat(promptMessage.role()).isEqualTo("user");
                        assertThat(promptMessage.content())
                            .isInstanceOfSatisfying(
                                McpClientGetPromptResult.TextMessage.class,
                                textMessage ->
                                    assertThat(textMessage.text())
                                        .isEqualTo("Please review the following code snippet."));
                      });
            });
  }

  @ParameterizedTest
  @MethodSource("promptMessagePermutations")
  void returnsProperContent_whenClientReturnsContent(
      Consumer<McpClientGetPromptResult.PromptMessage> messageConstraints,
      List<McpSchema.PromptMessage> messages) {
    when(mcpClient.getPrompt(any(McpSchema.GetPromptRequest.class)))
        .thenReturn(mcpPromptResult("Code Review", messages));
    when(mcpClient.getClientInfo()).thenReturn(new McpSchema.Implementation("test-client", "1.0"));

    final var parameters = Map.of("name", "code_review", "arguments", Map.of("assignee", "dev1"));

    final var result = testee.execute(mcpClient, EMPTY_FILTER, parameters);
    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientGetPromptResult.class,
            promptResult -> {
              assertThat(promptResult.description()).isEqualTo("Code Review");
              assertThat(promptResult.messages())
                  .hasSize(1)
                  .first()
                  .isInstanceOfSatisfying(
                      McpClientGetPromptResult.PromptMessage.class, messageConstraints);
            });
  }

  @Test
  void getsPrompt_whenPromptPassesAllowFilter() {
    when(mcpClient.getPrompt(any(McpSchema.GetPromptRequest.class)))
        .thenReturn(
            mcpPromptResult(
                "Allowed",
                List.of(
                    new McpSchema.PromptMessage(
                        McpSchema.Role.USER, new McpSchema.TextContent("Content")))));
    when(mcpClient.getClientInfo()).thenReturn(new McpSchema.Implementation("test-client", "1.0"));

    final var filter = AllowDenyListBuilder.builder().allowed(List.of("allowed-prompt")).build();
    final var parameters =
        Map.of("name", "allowed-prompt", "arguments", Map.of("param1", "value1"));

    final var result = testee.execute(mcpClient, filter, parameters);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientGetPromptResult.class,
            res -> assertThat(res.description()).isEqualTo("Allowed"));
  }

  @Test
  void getsPrompt_whenPromptNotInDenyFilter() {
    when(mcpClient.getPrompt(any(McpSchema.GetPromptRequest.class)))
        .thenReturn(
            mcpPromptResult(
                "Safe",
                List.of(
                    new McpSchema.PromptMessage(
                        McpSchema.Role.USER, new McpSchema.TextContent("Content")))));
    when(mcpClient.getClientInfo()).thenReturn(new McpSchema.Implementation("test-client", "1.0"));

    final var filter = AllowDenyListBuilder.builder().denied(List.of("blocked-prompt")).build();
    final var parameters = Map.of("name", "safe-prompt", "arguments", Map.of("param1", "value1"));

    final var result = testee.execute(mcpClient, filter, parameters);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientGetPromptResult.class, res -> assertThat(res.description()).isEqualTo("Safe"));
  }

  @Test
  void throwsConnectorException_whenParamsAreNull() {
    assertThatThrownBy(() -> testee.execute(mcpClient, EMPTY_FILTER, null))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo("MCP_CLIENT_INVALID_PARAMS");
              assertThat(exception.getMessage())
                  .isEqualTo("Parameters for get prompt request cannot be empty.");
            });
  }

  @Test
  void throwsConnectorException_whenPromptNameIsMissing() {
    final Map<String, Object> parameters = Map.of("arguments", Map.of("assignee", "dev1"));

    assertThatThrownBy(() -> testee.execute(mcpClient, EMPTY_FILTER, parameters))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo("MCP_CLIENT_INVALID_PARAMS");
              assertThat(exception.getMessage()).isEqualTo("Prompt name is required in params.");
            });
  }

  @Test
  void throwsConnectorException_whenPromptNameIsNotString() {
    final Map<String, Object> parameters =
        Map.of("name", 1, "arguments", Map.of("assignee", "dev1"));

    assertThatThrownBy(() -> testee.execute(mcpClient, EMPTY_FILTER, parameters))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo("MCP_CLIENT_INVALID_PARAMS");
              assertThat(exception.getMessage()).isEqualTo("Prompt name must be a string.");
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "    "})
  void throwsConnectorException_whenPromptNameIsEmptyOrBlank(String promptName) {
    final Map<String, Object> parameters =
        Map.of("name", promptName, "arguments", Map.of("assignee", "dev1"));

    assertThatThrownBy(() -> testee.execute(mcpClient, EMPTY_FILTER, parameters))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo("MCP_CLIENT_INVALID_PARAMS");
              assertThat(exception.getMessage()).isEqualTo("Prompt name cannot be empty or blank.");
            });
  }

  @Test
  void throwsConnectorException_whenArgumentsNoMap() {
    final Map<String, Object> parameters =
        Map.of("name", "code_review", "arguments", List.of(1, 2, 3));

    assertThatThrownBy(() -> testee.execute(mcpClient, EMPTY_FILTER, parameters))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo("MCP_CLIENT_INVALID_PARAMS");
              assertThat(exception.getMessage())
                  .isEqualTo("Incorrect format for prompt arguments. Expecting an object.");
            });
  }

  @Test
  void throwsConnectorException_whenMcpClientFails() {
    when(mcpClient.getPrompt(any(McpSchema.GetPromptRequest.class)))
        .thenThrow(new RuntimeException("MCP client error"));
    when(mcpClient.getClientInfo()).thenReturn(new McpSchema.Implementation("test-client", "1.0"));

    final var parameters = Map.of("name", "code_review", "arguments", Map.of("assignee", "dev1"));

    assertThatThrownBy(() -> testee.execute(mcpClient, EMPTY_FILTER, parameters))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo("MCP_CLIENT_GET_PROMPT_ERROR");
              assertThat(exception.getMessage())
                  .isEqualTo("Error getting prompt 'code_review': MCP client error");
            });
  }

  @Test
  void throwsException_whenPromptNotIncludedInFilter() {
    final var filter = AllowDenyListBuilder.builder().allowed(List.of("allowed-prompt")).build();
    when(mcpClient.getClientInfo()).thenReturn(new McpSchema.Implementation("test-client", "1.0"));

    final var parameters =
        Map.of("name", "blocked-prompt", "arguments", Map.of("param1", "value1"));

    assertThatThrownBy(() -> testee.execute(mcpClient, filter, parameters))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo("MCP_CLIENT_GET_PROMPT_ERROR");
              assertThat(exception.getMessage())
                  .isEqualTo(
                      "Getting prompt 'blocked-prompt' is not allowed by filter configuration: [allowed=[allowed-prompt], denied=[]]");
            });
  }

  @Test
  void throwsException_whenPromptExcludedInFilter() {
    final var filter = AllowDenyListBuilder.builder().denied(List.of("blocked-prompt")).build();
    when(mcpClient.getClientInfo()).thenReturn(new McpSchema.Implementation("test-client", "1.0"));

    final var parameters =
        Map.of("name", "blocked-prompt", "arguments", Map.of("param1", "value1"));

    assertThatThrownBy(() -> testee.execute(mcpClient, filter, parameters))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo("MCP_CLIENT_GET_PROMPT_ERROR");
              assertThat(exception.getMessage())
                  .isEqualTo(
                      "Getting prompt 'blocked-prompt' is not allowed by filter configuration: [allowed=[], denied=[blocked-prompt]]");
            });
  }

  @Test
  void throwsException_whenPromptInDenyListEvenIfInAllowList() {
    final var filter =
        AllowDenyListBuilder.builder()
            .allowed(List.of("conflicted-prompt"))
            .denied(List.of("conflicted-prompt"))
            .build();
    when(mcpClient.getClientInfo()).thenReturn(new McpSchema.Implementation("test-client", "1.0"));

    final var parameters =
        Map.of("name", "conflicted-prompt", "arguments", Map.of("param1", "value1"));

    assertThatThrownBy(() -> testee.execute(mcpClient, filter, parameters))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo("MCP_CLIENT_GET_PROMPT_ERROR");
              assertThat(exception.getMessage())
                  .contains(
                      "Getting prompt 'conflicted-prompt' is not allowed by filter configuration");
            });
  }

  private McpSchema.GetPromptResult mcpPromptResult(
      String description, List<McpSchema.PromptMessage> messages) {
    return new McpSchema.GetPromptResult(description, messages);
  }

  static Stream<Arguments> promptMessagePermutations() {
    return Stream.of(
        Arguments.argumentSet(
            "with text",
            (Consumer<McpClientGetPromptResult.PromptMessage>)
                (McpClientGetPromptResult.PromptMessage promptMessage) -> {
                  assertThat(promptMessage.role()).isEqualTo("user");
                  assertThat(promptMessage.content())
                      .isInstanceOfSatisfying(
                          McpClientGetPromptResult.TextMessage.class,
                          textContent -> assertThat(textContent.text()).isEqualTo("some text"));
                },
            List.of(
                new McpSchema.PromptMessage(
                    McpSchema.Role.USER, new McpSchema.TextContent("some text")))),
        Arguments.argumentSet(
            "with image",
            (Consumer<McpClientGetPromptResult.PromptMessage>)
                (McpClientGetPromptResult.PromptMessage promptMessage) -> {
                  assertThat(promptMessage.role()).isEqualTo("user");
                  assertThat(promptMessage.content())
                      .isInstanceOfSatisfying(
                          McpClientGetPromptResult.BlobMessage.class,
                          blobContent ->
                              assertThat(blobContent.data())
                                  .isEqualTo("some binary".getBytes(StandardCharsets.UTF_8)));
                },
            List.of(
                new McpSchema.PromptMessage(
                    McpSchema.Role.USER,
                    new McpSchema.ImageContent(
                        null,
                        Base64.getEncoder().encodeToString("some binary".getBytes()),
                        "image/png")))),
        Arguments.argumentSet(
            "with text resource",
            (Consumer<McpClientGetPromptResult.PromptMessage>)
                (McpClientGetPromptResult.PromptMessage promptMessage) -> {
                  assertThat(promptMessage.role()).isEqualTo("user");
                  assertThat(promptMessage.content())
                      .isInstanceOfSatisfying(
                          McpClientGetPromptResult.EmbeddedResourceContent.class,
                          embeddedResourceContent ->
                              assertThat(embeddedResourceContent.resource())
                                  .isEqualTo(
                                      new McpClientGetPromptResult.EmbeddedResourceContent
                                          .EmbeddedResource.TextResource(
                                          "uri", "text/plain", "some text")));
                },
            List.of(
                new McpSchema.PromptMessage(
                    McpSchema.Role.USER,
                    new McpSchema.EmbeddedResource(
                        null,
                        new McpSchema.TextResourceContents("uri", "text/plain", "some text"))))),
        Arguments.argumentSet(
            "with blob resource",
            (Consumer<McpClientGetPromptResult.PromptMessage>)
                (McpClientGetPromptResult.PromptMessage promptMessage) -> {
                  assertThat(promptMessage.role()).isEqualTo("user");
                  assertThat(promptMessage.content())
                      .isInstanceOfSatisfying(
                          McpClientGetPromptResult.EmbeddedResourceContent.class,
                          embeddedResource ->
                              assertThat(embeddedResource.resource())
                                  .isInstanceOfSatisfying(
                                      McpClientGetPromptResult.EmbeddedResourceContent
                                          .EmbeddedResource.BlobResource.class,
                                      resource -> {
                                        assertThat(resource.uri()).isEqualTo("uri");
                                        assertThat(resource.blob())
                                            .isEqualTo("blob".getBytes(StandardCharsets.UTF_8));
                                        assertThat(resource.mimeType()).isEqualTo("audio/mpeg");
                                      }));
                },
            List.of(
                new McpSchema.PromptMessage(
                    McpSchema.Role.USER,
                    new McpSchema.EmbeddedResource(
                        null,
                        new McpSchema.BlobResourceContents(
                            "uri",
                            "audio/mpeg",
                            Base64.getEncoder().encodeToString("blob".getBytes())))))));
  }
}
