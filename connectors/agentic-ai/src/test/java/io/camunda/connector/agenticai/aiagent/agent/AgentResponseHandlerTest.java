/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_TO_PARSE_RESPONSE_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentJobContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.document.Document;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentResponseHandlerTest {

  private static final AgentContext AGENT_CONTEXT =
      AgentContext.empty()
          .withState(AgentState.WAITING_FOR_TOOL_INPUT)
          .withMetrics(AgentMetrics.empty().withModelCalls(5));

  private static final List<ToolCallProcessVariable> TOOL_CALLS =
      List.of(
          ToolCallProcessVariable.from(
              ToolCall.builder()
                  .id("123456")
                  .name("tool1")
                  .arguments(Map.of("a", 10, "b", "twenty"))
                  .build()),
          ToolCallProcessVariable.from(ToolCall.builder().id("234567").name("tool2").build()));

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
  private final AgentResponseHandler responseHandler = new AgentResponseHandlerImpl(objectMapper);

  @Mock private AgentJobContext agentJobContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AgentRequest request;

  @Nested
  class Text {

    @ParameterizedTest
    @MethodSource("emptyAssistantMessages")
    void returnsEmptyResponseWhenAssistantMessageDoesNotContainText(
        AssistantMessage assistantMessage) {
      when(request.data().response())
          .thenReturn(new ResponseConfiguration(new TextResponseFormatConfiguration(false), false));

      final var response = createResponse(assistantMessage);

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isNull();
      assertThat(response.responseJson()).isNull();
    }

    @Test
    void returnsTextResponseIfConfigured() {
      when(request.data().response())
          .thenReturn(new ResponseConfiguration(new TextResponseFormatConfiguration(false), false));

      final var response = createResponse(assistantMessage(HAIKU_TEXT));

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(HAIKU_TEXT);
      assertThat(response.responseJson()).isNull();
    }

    @Test
    void returnsTextResponseIfResponseConfigurationIsMissing() {
      when(request.data().response()).thenReturn(null);

      final var response = createResponse(assistantMessage(HAIKU_TEXT));

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(HAIKU_TEXT);
      assertThat(response.responseJson()).isNull();
    }

    @Test
    void returnsTextResponseIfResponseFormatIsMissing() {
      when(request.data().response()).thenReturn(new ResponseConfiguration(null, false));

      final var response = createResponse(assistantMessage(HAIKU_TEXT));

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(HAIKU_TEXT);
      assertThat(response.responseJson()).isNull();
    }

    @Test
    void triesToParseResponseTextAsJsonIfConfigured() {
      when(request.data().response())
          .thenReturn(new ResponseConfiguration(new TextResponseFormatConfiguration(true), false));

      final var response = createResponse(assistantMessage(HAIKU_JSON));

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(HAIKU_JSON);
      assertThat(response.responseJson()).satisfies(HAIKU_JSON_ASSERTIONS);
    }

    @Test
    void returnsNullAsJsonObjectWhenParsingJsonFails() {
      when(request.data().response())
          .thenReturn(new ResponseConfiguration(new TextResponseFormatConfiguration(true), false));

      final var response = createResponse(assistantMessage(HAIKU_TEXT));

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(HAIKU_TEXT);
      assertThat(response.responseJson()).isNull();
    }

    @Test
    void returnsAssistantMessageIfConfigured() {
      when(request.data().response())
          .thenReturn(new ResponseConfiguration(new TextResponseFormatConfiguration(false), true));

      AssistantMessage assistantMessage = assistantMessage(HAIKU_TEXT);
      final var response = createResponse(assistantMessage);

      assertThat(response.responseMessage()).isNotNull().isEqualTo(assistantMessage);
      assertThat(response.responseText()).isEqualTo(HAIKU_TEXT);
      assertThat(response.responseJson()).isNull();
    }

    static Stream<AssistantMessage> emptyAssistantMessages() {
      return Stream.of(
          new AssistantMessage(List.of(), List.of(), Map.of()),
          assistantMessage(List.of(new DocumentContent(mock(Document.class)))));
    }
  }

  @Nested
  class Json {

    @Test
    void returnsParsedJsonResponse() {
      when(request.data().response())
          .thenReturn(
              new ResponseConfiguration(new JsonResponseFormatConfiguration(null, null), false));

      final var response = createResponse(assistantMessage(HAIKU_JSON));

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isNull();
      assertThat(response.responseJson()).satisfies(HAIKU_JSON_ASSERTIONS);
    }

    @Test
    void throwsExceptionWhenJsonParsingFails() {
      when(request.data().response())
          .thenReturn(
              new ResponseConfiguration(new JsonResponseFormatConfiguration(null, null), false));

      assertThatThrownBy(() -> createResponse(assistantMessage(HAIKU_TEXT)))
          .hasMessageStartingWith("Failed to parse response content as JSON")
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e -> {
                assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_FAILED_TO_PARSE_RESPONSE_CONTENT);
              });
    }
  }

  private AgentResponse createResponse(AssistantMessage assistantMessage) {
    final var response =
        responseHandler.createResponse(
            new AgentExecutionContext(agentJobContext, request),
            AGENT_CONTEXT,
            assistantMessage,
            TOOL_CALLS);

    assertThat(response).isNotNull();
    assertThat(response.context()).isEqualTo(AGENT_CONTEXT);
    assertThat(response.toolCalls()).containsExactlyElementsOf(TOOL_CALLS);

    return response;
  }
}
