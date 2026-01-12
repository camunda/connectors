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

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.*;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientGetPromptResult;
import io.camunda.connector.api.error.ConnectorException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetPromptRequestTest {

  @Mock private McpClient mcpClient;

  private GetPromptRequest testee;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    testee = new GetPromptRequest(objectMapper);
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
                  .satisfies(
                      promptMessage -> {
                        assertThat(promptMessage.role()).isEqualTo("USER");
                        assertThat(promptMessage.content())
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
                  .isEqualTo("Incorrect format for prompt arguments. Expecting object.");
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

  @Test
  void returnMcpClientGetPromptResult() {
    when(mcpClient.getPrompt(eq("code_review"), any()))
        .thenReturn(
            mcpPromptResult(
                "Code Review",
                List.of(
                    new McpPromptMessage(
                        McpRole.USER,
                        new McpTextContent("Please review the following code snippet.")))));

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
                  .satisfies(
                      promptMessage -> {
                        assertThat(promptMessage.role()).isEqualTo("USER");
                        assertThat(promptMessage.content())
                            .isEqualTo("Please review the following code snippet.");
                      });
            });
  }

  @ParameterizedTest
  @MethodSource("nonTextualResultMessages")
  void filterNonTextualMessages_whenNonTextualMessagesAreReturned(
      List<McpPromptMessage> resultMessages) {
    when(mcpClient.getPrompt(eq("code_review"), any()))
        .thenReturn(mcpPromptResult("Code Review", resultMessages));

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
                  .satisfies(
                      promptMessage -> {
                        assertThat(promptMessage.role()).isEqualTo("USER");
                        assertThat(promptMessage.content())
                            .isEqualTo("Please review the following code snippet.");
                      });
            });
  }

  private McpGetPromptResult mcpPromptResult(String description, List<McpPromptMessage> messages) {
    return new McpGetPromptResult(description, messages);
  }

  static Stream<Arguments> nonTextualResultMessages() {
    return Stream.of(
        Arguments.argumentSet(
            "with image",
            List.of(
                new McpPromptMessage(
                    McpRole.USER, new McpTextContent("Please review the following code snippet.")),
                new McpPromptMessage(
                    McpRole.USER, new McpImageContent("some-binary", "image/png")))),
        Arguments.argumentSet(
            "with text resource",
            List.of(
                new McpPromptMessage(
                    McpRole.USER, new McpTextContent("Please review the following code snippet.")),
                new McpPromptMessage(
                    McpRole.USER,
                    new McpEmbeddedResource(
                        new McpTextResourceContents("uri", "some text", "text/plain"))))),
        Arguments.argumentSet(
            "with blob resource",
            List.of(
                new McpPromptMessage(
                    McpRole.USER, new McpTextContent("Please review the following code snippet.")),
                new McpPromptMessage(
                    McpRole.USER,
                    new McpEmbeddedResource(
                        new McpBlobResourceContents("uri", "blob", "audio/mpeg"))))));
  }
}
