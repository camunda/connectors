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
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.completions.CompletionUsage;
import com.openai.services.blocking.ChatService;
import com.openai.services.blocking.chat.ChatCompletionService;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatOptions;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatStreamListener;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
class OpenAiChatCompletionsChatModelApiTest {

  private static final String MODEL_ID = "gpt-4o";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Mock private OpenAIClient client;
  @Mock private ChatService chatService;
  @Mock private ChatCompletionService chatCompletionService;

  @Captor private ArgumentCaptor<ChatCompletionCreateParams> paramsCaptor;

  private OpenAiChatCompletionsChatModelApi api;

  @BeforeEach
  void setUp() {
    when(client.chat()).thenReturn(chatService);
    when(chatService.completions()).thenReturn(chatCompletionService);
    api = new OpenAiChatCompletionsChatModelApi(client, MODEL_ID, OBJECT_MAPPER, 1024L, null, null);
  }

  @Test
  void capabilitiesAreTextOnly() {
    var caps = api.capabilities();
    assertThat(caps.userMessageModalities()).containsExactly(Modality.TEXT);
    assertThat(caps.toolResultModalities()).containsExactly(Modality.TEXT);
    assertThat(caps.assistantMessageModalities()).containsExactly(Modality.TEXT);
    assertThat(caps.supportsReasoning()).isFalse();
    assertThat(caps.supportsPromptCaching()).isFalse();
    assertThat(caps.supportsParallelToolCalls()).isTrue();
  }

  @Test
  void buildsExpectedParamsForSimpleConversation() {
    when(chatCompletionService.create((ChatCompletionCreateParams) paramsCaptor.capture()))
        .thenReturn(textOnlyResponse("hello"));

    var request =
        new ChatRequest(List.of(systemMessage("be helpful"), userMessage("hi")), List.of(), null);
    api.complete(request, defaultOptions(), ChatStreamListener.NOOP).join();

    var params = paramsCaptor.getValue();
    assertThat(params.model().asString()).isEqualTo(MODEL_ID);
    assertThat(params.maxCompletionTokens()).hasValue(1024L);
    assertThat(params.messages()).hasSize(2);
  }

  @Test
  void appliesMaxOutputTokensOverride() {
    when(chatCompletionService.create((ChatCompletionCreateParams) paramsCaptor.capture()))
        .thenReturn(textOnlyResponse("ok"));

    var options = new ChatOptions(2048, null, null, Map.of());
    api.complete(
            new ChatRequest(List.of(userMessage("hi")), List.of(), null),
            options,
            ChatStreamListener.NOOP)
        .join();

    assertThat(paramsCaptor.getValue().maxCompletionTokens()).hasValue(2048L);
  }

  @Test
  void mapsAssistantToolCallsBackToContentBlocks() {
    when(chatCompletionService.create(any(ChatCompletionCreateParams.class)))
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
  void priorAssistantToolCallsAndResultsRoundTripIntoParams() {
    when(chatCompletionService.create((ChatCompletionCreateParams) paramsCaptor.capture()))
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
    // user, assistant (with tool call), tool result, user → 4 messages
    assertThat(params.messages()).hasSize(4);
  }

  @Test
  void wrapsSdkExceptionInConnectorException() {
    when(chatCompletionService.create(any(ChatCompletionCreateParams.class)))
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
        .hasMessageContaining("OpenAI Chat Completions call failed");
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

  private static ChatCompletion textOnlyResponse(String text) {
    return ChatCompletion.builder()
        .id("chatcmpl_1")
        .model(MODEL_ID)
        .created(1700000000L)
        .addChoice(
            ChatCompletion.Choice.builder()
                .index(0L)
                .finishReason(ChatCompletion.Choice.FinishReason.STOP)
                .message(
                    ChatCompletionMessage.builder().content(text).refusal(Optional.empty()).build())
                .logprobs(Optional.empty())
                .build())
        .usage(usage(10, 5))
        .build();
  }

  private static ChatCompletion toolCallResponse(String name, String id, String argumentsJson) {
    var toolCall =
        ChatCompletionMessageToolCall.ofFunction(
            ChatCompletionMessageFunctionToolCall.builder()
                .id(id)
                .function(
                    ChatCompletionMessageFunctionToolCall.Function.builder()
                        .name(name)
                        .arguments(argumentsJson)
                        .build())
                .build());

    return ChatCompletion.builder()
        .id("chatcmpl_2")
        .model(MODEL_ID)
        .created(1700000000L)
        .addChoice(
            ChatCompletion.Choice.builder()
                .index(0L)
                .finishReason(ChatCompletion.Choice.FinishReason.TOOL_CALLS)
                .message(
                    ChatCompletionMessage.builder()
                        .content(Optional.empty())
                        .refusal(Optional.empty())
                        .addToolCall(toolCall)
                        .build())
                .logprobs(Optional.empty())
                .build())
        .usage(usage(15, 8))
        .build();
  }

  private static CompletionUsage usage(long prompt, long completion) {
    return CompletionUsage.builder()
        .promptTokens(prompt)
        .completionTokens(completion)
        .totalTokens(prompt + completion)
        .build();
  }
}
