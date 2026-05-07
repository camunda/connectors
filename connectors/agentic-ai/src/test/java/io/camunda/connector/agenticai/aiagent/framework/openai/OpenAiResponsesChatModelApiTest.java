/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.toolCallResultMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.ObjectMappers;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.services.blocking.ResponseService;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatOptions;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatStreamListener;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpenAiResponsesChatModelApiTest {

  private static final String MODEL_ID = "gpt-5";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final ModelCapabilities CAPABILITIES =
      new ModelCapabilities(
          List.of(Modality.TEXT, Modality.IMAGE),
          List.of(Modality.TEXT, Modality.IMAGE, Modality.PDF),
          List.of(Modality.TEXT),
          true,
          true,
          true,
          true,
          400000,
          128000);

  @Mock private OpenAIClient client;
  @Mock private ResponseService responseService;

  @Captor private ArgumentCaptor<ResponseCreateParams> paramsCaptor;

  private OpenAiResponsesChatModelApi api;

  @BeforeEach
  void setUp() {
    when(client.responses()).thenReturn(responseService);
    api =
        new OpenAiResponsesChatModelApi(
            client, MODEL_ID, OBJECT_MAPPER, CAPABILITIES, 1024L, null, null);
  }

  @Test
  void capabilitiesReturnsConfiguredInstance() {
    assertThat(api.capabilities()).isSameAs(CAPABILITIES);
  }

  @Test
  void buildsExpectedParamsForSimpleConversation() {
    when(responseService.create((ResponseCreateParams) paramsCaptor.capture()))
        .thenReturn(textOnlyResponse("hello"));

    var request =
        new ChatRequest(List.of(systemMessage("be helpful"), userMessage("hi")), List.of(), null);
    api.complete(request, defaultOptions(), ChatStreamListener.NOOP).join();

    var params = paramsCaptor.getValue();
    assertThat(params.model().flatMap(m -> m.string())).hasValue(MODEL_ID);
    assertThat(params.maxOutputTokens()).hasValue(1024L);
    assertThat(params.instructions()).hasValue("be helpful");
    assertThat(params.input().get().asResponse()).hasSize(1); // user
  }

  @Test
  void mapsAssistantToolCallsBackToContentBlocks() {
    when(responseService.create(any(ResponseCreateParams.class)))
        .thenReturn(toolCallResponse("getWeather", "abc", "{\"location\":\"MUC\"}"));

    var response =
        api.complete(
                new ChatRequest(List.of(userMessage("weather?")), tools(), null),
                defaultOptions(),
                ChatStreamListener.NOOP)
            .join();

    var assistant = response.assistantMessage();
    assertThat(assistant.toolCalls())
        .extracting(ToolCall::id, ToolCall::name)
        .containsExactly(Tuple.tuple("abc", "getWeather"));
    assertThat(assistant.toolCalls().getFirst().arguments()).containsEntry("location", "MUC");
    assertThat(assistant.stopReason())
        .isEqualTo(io.camunda.connector.agenticai.model.message.StopReason.TOOL_USE);
  }

  @Test
  void priorAssistantToolCallsAndResultsRoundTripIntoInputItems() {
    when(responseService.create((ResponseCreateParams) paramsCaptor.capture()))
        .thenReturn(textOnlyResponse("ack"));

    var prior =
        assistantMessage(
            "let me check",
            List.of(
                ToolCall.builder()
                    .id("abc")
                    .name("getWeather")
                    .arguments(Map.of("location", "MUC"))
                    .build()));
    var results =
        toolCallResultMessage(
            List.of(
                ToolCallResult.builder().id("abc").name("getWeather").content("Sunny").build()));

    api.complete(
            new ChatRequest(
                List.of(userMessage("weather?"), prior, results, userMessage("thanks")),
                tools(),
                null),
            defaultOptions(),
            ChatStreamListener.NOOP)
        .join();

    var params = paramsCaptor.getValue();
    var items = params.input().get().asResponse();
    // user, assistant text, function call, function call output, user → 5 input items
    assertThat(items).hasSize(5);
  }

  @Test
  void wrapsSdkExceptionInConnectorException() {
    when(responseService.create(any(ResponseCreateParams.class)))
        .thenThrow(new RuntimeException("boom"));

    var future =
        api.complete(
            new ChatRequest(List.of(userMessage("hi")), List.of(), null),
            defaultOptions(),
            ChatStreamListener.NOOP);

    assertThatThrownBy(future::join)
        .isInstanceOf(CompletionException.class)
        .cause()
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("OpenAI Responses call failed");
  }

  private static ChatOptions defaultOptions() {
    return new ChatOptions(null, null, null, Map.of());
  }

  private static List<ToolDefinition> tools() {
    return List.of(
        ToolDefinition.builder()
            .name("getWeather")
            .description("Returns the current weather")
            .inputSchema(
                Map.of(
                    "type", "object",
                    "properties", Map.of("location", Map.of("type", "string")),
                    "required", List.of("location")))
            .build());
  }

  private static Response textOnlyResponse(String text) {
    return responseFromJson(
        """
        {
          "id": "resp_1",
          "object": "response",
          "created_at": 1700000000,
          "status": "completed",
          "model": "%s",
          "parallel_tool_calls": true,
          "tool_choice": "auto",
          "tools": [],
          "output": [
            {
              "type": "message",
              "id": "msg_1",
              "status": "completed",
              "role": "assistant",
              "content": [
                { "type": "output_text", "text": "%s", "annotations": [] }
              ]
            }
          ],
          "usage": {
            "input_tokens": 10,
            "output_tokens": 5,
            "total_tokens": 15,
            "input_tokens_details": { "cached_tokens": 0 },
            "output_tokens_details": { "reasoning_tokens": 0 }
          }
        }
        """
            .formatted(MODEL_ID, text));
  }

  private static Response toolCallResponse(String name, String callId, String argumentsJson) {
    final var escapedArgs = argumentsJson.replace("\"", "\\\"");
    return responseFromJson(
        """
        {
          "id": "resp_2",
          "object": "response",
          "created_at": 1700000000,
          "status": "completed",
          "model": "%s",
          "parallel_tool_calls": true,
          "tool_choice": "auto",
          "tools": [],
          "output": [
            {
              "type": "function_call",
              "call_id": "%s",
              "name": "%s",
              "arguments": "%s"
            }
          ],
          "usage": {
            "input_tokens": 15,
            "output_tokens": 8,
            "total_tokens": 23,
            "input_tokens_details": { "cached_tokens": 0 },
            "output_tokens_details": { "reasoning_tokens": 0 }
          }
        }
        """
            .formatted(MODEL_ID, callId, name, escapedArgs));
  }

  private static Response responseFromJson(String json) {
    try {
      return ObjectMappers.jsonMapper().readValue(json, Response.class);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to deserialize test Response fixture", e);
    }
  }
}
