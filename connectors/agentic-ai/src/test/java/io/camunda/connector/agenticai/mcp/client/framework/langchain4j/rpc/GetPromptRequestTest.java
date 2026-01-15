/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.langchain4j.mcp.client.*;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientGetPromptResult;
import io.camunda.connector.api.error.ConnectorException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetPromptRequestTest {

  @Mock private McpClient mcpClient;

  private GetPromptRequest testee;

  @BeforeEach
  void setUp() {
    testee = new GetPromptRequest();
  }

  @ParameterizedTest
  @NullAndEmptySource
  void handlesEmptyArguments(Map<String, Object> arguments) {
    when(mcpClient.getPrompt(any(), any()))
        .thenReturn(
            mcpPromptResult(
                "Code Review",
                List.of(
                    new McpPromptMessage(
                        McpRole.USER,
                        new McpTextContent("Please review the following code snippet.")))));
    final var parameters = new LinkedHashMap<String, Object>();
    parameters.put("name", "code_review");
    if (arguments != null) {
      parameters.put("arguments", arguments);
    }

    final var result = testee.execute(mcpClient, parameters);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientGetPromptResult.class,
            res -> {
              assertThat(res.description()).isEqualTo("Code Review");
              assertThat(res.messages())
                  .hasSize(1)
                  .first()
                  .isInstanceOfSatisfying(
                      McpClientGetPromptResult.TextMessage.class,
                      promptMessage -> {
                        assertThat(promptMessage.role()).isEqualTo("user");
                        assertThat(promptMessage.text())
                            .isEqualTo("Please review the following code snippet.");
                      });
            });
  }

  @Test
  void throwsConnectorException_whenParamsAreNull() {
    assertThatThrownBy(() -> testee.execute(mcpClient, null))
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

    assertThatThrownBy(() -> testee.execute(mcpClient, parameters))
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

    assertThatThrownBy(() -> testee.execute(mcpClient, parameters))
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

    assertThatThrownBy(() -> testee.execute(mcpClient, parameters))
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

    assertThatThrownBy(() -> testee.execute(mcpClient, parameters))
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
    when(mcpClient.getPrompt(eq("code_review"), any()))
        .thenThrow(new RuntimeException("MCP client error"));

    final var parameters = Map.of("name", "code_review", "arguments", Map.of("assignee", "dev1"));

    assertThatThrownBy(() -> testee.execute(mcpClient, parameters))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo("MCP_CLIENT_GET_PROMPT_ERROR");
              assertThat(exception.getMessage())
                  .isEqualTo("Error getting prompt 'code_review': MCP client error");
            });
  }

  @ParameterizedTest
  @MethodSource("promptMessagePermutations")
  void returnsProperContent_whenClientReturnsContent(
      Class<McpClientGetPromptResult.PromptMessage> messageClass,
      Consumer<McpClientGetPromptResult.PromptMessage> messageConstraints,
      List<McpPromptMessage> messages) {
    when(mcpClient.getPrompt(eq("code_review"), any()))
        .thenReturn(mcpPromptResult("Code Review", messages));

    final var parameters = Map.of("name", "code_review", "arguments", Map.of("assignee", "dev1"));

    final var result = testee.execute(mcpClient, parameters);
    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientGetPromptResult.class,
            promptResult -> {
              assertThat(promptResult.description()).isEqualTo("Code Review");
              assertThat(promptResult.messages())
                  .hasSize(1)
                  .first()
                  .isInstanceOfSatisfying(messageClass, messageConstraints);
            });
  }

  private McpGetPromptResult mcpPromptResult(String description, List<McpPromptMessage> messages) {
    return new McpGetPromptResult(description, messages);
  }

  static Stream<Arguments> promptMessagePermutations() {
    return Stream.of(
        Arguments.argumentSet(
            "with text",
            McpClientGetPromptResult.TextMessage.class,
            (Consumer<McpClientGetPromptResult.TextMessage>)
                (McpClientGetPromptResult.TextMessage textMessage) -> {
                  assertThat(textMessage.role()).isEqualTo("user");
                  assertThat(textMessage.text()).isEqualTo("some text");
                },
            List.of(new McpPromptMessage(McpRole.USER, new McpTextContent("some text")))),
        Arguments.argumentSet(
            "with image",
            McpClientGetPromptResult.BlobMessage.class,
            (Consumer<McpClientGetPromptResult.BlobMessage>)
                (McpClientGetPromptResult.BlobMessage blobMessage) -> {
                  assertThat(blobMessage.role()).isEqualTo("user");
                  assertThat(blobMessage.data())
                      .isEqualTo("some binary".getBytes(StandardCharsets.UTF_8));
                },
            List.of(
                new McpPromptMessage(
                    McpRole.USER, new McpImageContent("c29tZSBiaW5hcnk=", "image/png")))),
        Arguments.argumentSet(
            "with text resource",
            McpClientGetPromptResult.EmbeddedResourceMessage.class,
            (Consumer<McpClientGetPromptResult.EmbeddedResourceMessage>)
                (McpClientGetPromptResult.EmbeddedResourceMessage embeddedResourceMessage) -> {
                  assertThat(embeddedResourceMessage.role()).isEqualTo("user");
                  assertThat(embeddedResourceMessage.resource())
                      .isEqualTo(
                          new McpClientGetPromptResult.EmbeddedResourceMessage.EmbeddedResource
                              .TextResource("uri", "some text", "text/plain"));
                },
            List.of(
                new McpPromptMessage(
                    McpRole.USER,
                    new McpEmbeddedResource(
                        new McpTextResourceContents("uri", "some text", "text/plain"))))),
        Arguments.argumentSet(
            "with blob resource",
            McpClientGetPromptResult.EmbeddedResourceMessage.class,
            (Consumer<McpClientGetPromptResult.EmbeddedResourceMessage>)
                (McpClientGetPromptResult.EmbeddedResourceMessage embeddedResourceMessage) -> {
                  assertThat(embeddedResourceMessage.role()).isEqualTo("user");
                  assertThat(embeddedResourceMessage.resource())
                      .isInstanceOfSatisfying(
                          McpClientGetPromptResult.EmbeddedResourceMessage.EmbeddedResource
                              .BlobResource.class,
                          blobResource -> {
                            assertThat(blobResource.uri()).isEqualTo("uri");
                            assertThat(blobResource.blob())
                                .isEqualTo("blob".getBytes(StandardCharsets.UTF_8));
                            assertThat(blobResource.mimeType()).isEqualTo("audio/mpeg");
                          });
                },
            List.of(
                new McpPromptMessage(
                    McpRole.USER,
                    new McpEmbeddedResource(
                        new McpBlobResourceContents("uri", "YmxvYg==", "audio/mpeg"))))));
  }
}
