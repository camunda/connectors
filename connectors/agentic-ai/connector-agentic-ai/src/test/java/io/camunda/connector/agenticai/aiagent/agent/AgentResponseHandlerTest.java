/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_TO_PARSE_RESPONSE_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentResponseHandlerTest {

  // Base context with modelCalls=5; toAgentContext() adds 1 from ingest → expected context has 6
  private static final AgentContext BASE_AGENT_CONTEXT =
      AgentContext.empty()
          .withState(AgentState.READY)
          .withMetrics(AgentMetrics.empty().withModelCalls(5));

  private static final AgentContext EXPECTED_AGENT_CONTEXT =
      BASE_AGENT_CONTEXT.withMetrics(AgentMetrics.empty().withModelCalls(6));

  // Assistant messages in these tests carry no tool calls, so the registry returns empty
  private static final List<ToolCallProcessVariable> EXPECTED_TOOL_CALLS = List.of();

  private static final String HAIKU_TEXT =
      "Endless waves whisper | moonlight dances on the tide | secrets drift below.";
  private static final String HAIKU_JSON =
      "{\"text\":\"%s\", \"length\": %d}".formatted(HAIKU_TEXT, HAIKU_TEXT.length());
  private static final ThrowingConsumer<Object> HAIKU_JSON_ASSERTIONS =
      json ->
          assertThat(json)
              .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
              .containsExactly(entry("text", HAIKU_TEXT), entry("length", HAIKU_TEXT.length()));

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private GatewayToolHandlerRegistry gatewayToolHandlers;

  private AgentResponseHandler responseHandler;

  @BeforeEach
  void setUp() {
    responseHandler = new AgentResponseHandlerImpl(objectMapper, gatewayToolHandlers);
    // by default, registry passes tool calls through unchanged
    when(gatewayToolHandlers.transformToolCalls(any(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
  }

  /**
   * Builds a conversation that has completed one turn with the given assistant message and response
   * configuration. The conversation has RAW_TOOL_CALLS on the assistant message by default when the
   * provided assistantMessage has no tool calls; the tool calls are taken from the message itself.
   */
  private AgentConversation conversationWith(
      ResponseConfiguration responseConfig, AssistantMessage assistantMessage) {
    var config = new AgentConfiguration(null, null, null, null, null, null, responseConfig);
    var history = TurnReconstructor.reconstruct(List.of());
    return AgentConversation.rehydrate(
            config,
            BASE_AGENT_CONTEXT,
            history,
            systemMessage("system"),
            List.of(),
            DocumentRegistry.empty())
        .ingest(assistantMessage, new AgentMetrics(1, AgentMetrics.TokenUsage.empty(), 0));
  }

  @Nested
  class Text {

    @ParameterizedTest
    @MethodSource("emptyAssistantMessages")
    void returnsEmptyResponseWhenAssistantMessageDoesNotContainText(
        AssistantMessage assistantMessage) {
      final var response =
          createResponse(
              new OutboundConnectorResponseConfiguration(
                  new TextResponseFormatConfiguration(false), false),
              assistantMessage);

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isNull();
      assertThat(response.responseJson()).isNull();
    }

    @Test
    void returnsTextResponseIfConfigured() {
      final var response =
          createResponse(
              new OutboundConnectorResponseConfiguration(
                  new TextResponseFormatConfiguration(false), false),
              assistantMessage(HAIKU_TEXT));

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(HAIKU_TEXT);
      assertThat(response.responseJson()).isNull();
    }

    @Test
    void returnsTextResponseIfResponseConfigurationIsMissing() {
      final var response = createResponse(null, assistantMessage(HAIKU_TEXT));

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(HAIKU_TEXT);
      assertThat(response.responseJson()).isNull();
    }

    @Test
    void returnsTextResponseIfResponseFormatIsMissing() {
      final var response =
          createResponse(
              new OutboundConnectorResponseConfiguration(null, false),
              assistantMessage(HAIKU_TEXT));

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(HAIKU_TEXT);
      assertThat(response.responseJson()).isNull();
    }

    @Test
    void triesToParseResponseTextAsJsonIfConfigured() {
      final var response =
          createResponse(
              new OutboundConnectorResponseConfiguration(
                  new TextResponseFormatConfiguration(true), false),
              assistantMessage(HAIKU_JSON));

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(HAIKU_JSON);
      assertThat(response.responseJson()).satisfies(HAIKU_JSON_ASSERTIONS);
    }

    @Test
    void returnsNullAsJsonObjectWhenParsingJsonFails() {
      final var response =
          createResponse(
              new OutboundConnectorResponseConfiguration(
                  new TextResponseFormatConfiguration(true), false),
              assistantMessage(HAIKU_TEXT));

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(HAIKU_TEXT);
      assertThat(response.responseJson()).isNull();
    }

    @Test
    void parsesJsonWrappedInMarkdownCodeBlocks() {
      // given - JSON wrapped in markdown code blocks (common with Anthropic Claude)
      String markdownWrappedJson = "```json\n" + HAIKU_JSON + "\n```";

      // when
      final var response =
          createResponse(
              new OutboundConnectorResponseConfiguration(
                  new TextResponseFormatConfiguration(true), false),
              assistantMessage(markdownWrappedJson));

      // then
      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(markdownWrappedJson);
      assertThat(response.responseJson()).satisfies(HAIKU_JSON_ASSERTIONS);
    }

    @Test
    void parsesJsonWrappedInMarkdownCodeBlocksWithoutLanguage() {
      // given - JSON wrapped in markdown code blocks without language specifier
      String markdownWrappedJson = "```\n" + HAIKU_JSON + "\n```";

      // when
      final var response =
          createResponse(
              new OutboundConnectorResponseConfiguration(
                  new TextResponseFormatConfiguration(true), false),
              assistantMessage(markdownWrappedJson));

      // then
      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(markdownWrappedJson);
      assertThat(response.responseJson()).satisfies(HAIKU_JSON_ASSERTIONS);
    }

    @Test
    void returnsAssistantMessageIfConfigured() {
      AssistantMessage assistantMessage = assistantMessage(HAIKU_TEXT);
      final var response =
          createResponse(
              new OutboundConnectorResponseConfiguration(
                  new TextResponseFormatConfiguration(false), true),
              assistantMessage);

      assertThat(response.responseMessage()).isNotNull().isEqualTo(assistantMessage);
      assertThat(response.responseText()).isEqualTo(HAIKU_TEXT);
      assertThat(response.responseJson()).isNull();
    }

    static Stream<AssistantMessage> emptyAssistantMessages() {
      return Stream.of(
          new AssistantMessage(List.of(), List.of(), Map.of()),
          assistantMessage(List.of(DocumentContent.documentContent(mock(Document.class)))));
    }
  }

  @Nested
  class Json {

    @Test
    void returnsParsedJsonResponse() {
      final var response =
          createResponse(
              new OutboundConnectorResponseConfiguration(
                  new JsonResponseFormatConfiguration(null, null), false),
              assistantMessage(HAIKU_JSON));

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isNull();
      assertThat(response.responseJson()).satisfies(HAIKU_JSON_ASSERTIONS);
    }

    @Test
    void throwsExceptionWhenJsonParsingFails() {
      assertThatThrownBy(
              () ->
                  createResponse(
                      new OutboundConnectorResponseConfiguration(
                          new JsonResponseFormatConfiguration(null, null), false),
                      assistantMessage(HAIKU_TEXT)))
          .hasMessageStartingWith("Failed to parse response content as JSON")
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e ->
                  assertThat(e.getErrorCode())
                      .isEqualTo(ERROR_CODE_FAILED_TO_PARSE_RESPONSE_CONTENT));
    }

    @Test
    void parsesJsonWrappedInMarkdownCodeBlocks() {
      // given - JSON wrapped in markdown code blocks (common with Anthropic Claude on Bedrock)
      String markdownWrappedJson = "```json\n" + HAIKU_JSON + "\n```";

      // when
      final var response =
          createResponse(
              new OutboundConnectorResponseConfiguration(
                  new JsonResponseFormatConfiguration(null, null), false),
              assistantMessage(markdownWrappedJson));

      // then
      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isNull();
      assertThat(response.responseJson()).satisfies(HAIKU_JSON_ASSERTIONS);
    }

    @Test
    void parsesJsonWrappedInMarkdownCodeBlocksWithoutLanguage() {
      // given - JSON wrapped in markdown code blocks without language specifier
      String markdownWrappedJson = "```\n" + HAIKU_JSON + "\n```";

      // when
      final var response =
          createResponse(
              new OutboundConnectorResponseConfiguration(
                  new JsonResponseFormatConfiguration(null, null), false),
              assistantMessage(markdownWrappedJson));

      // then
      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isNull();
      assertThat(response.responseJson()).satisfies(HAIKU_JSON_ASSERTIONS);
    }
  }

  private AgentResponse createResponse(
      ResponseConfiguration responseConfiguration, AssistantMessage assistantMessage) {
    final var conversation = conversationWith(responseConfiguration, assistantMessage);
    final var response = responseHandler.createResponse(conversation);

    assertThat(response).isNotNull();
    assertThat(response.context()).isEqualTo(EXPECTED_AGENT_CONTEXT);
    assertThat(response.toolCalls()).containsExactlyElementsOf(EXPECTED_TOOL_CALLS);

    return response;
  }
}
