/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.openai.client.OpenAIClient;
import com.openai.core.ObjectMappers;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.services.blocking.ChatService;
import com.openai.services.blocking.chat.ChatCompletionService;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.RequestCustomizations;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiConfiguration.Api;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiConfiguration.Backend;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiConfiguration.Effort;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the {@code Chat Completions} family strategy's wiring: that it drives {@code
 * client.chat().completions().createStreaming(...)} (rather than the Responses accessor), assembles
 * and closes the stream, and passes the assembled completion to the response converter. The vendor
 * request/response objects returned by the mocked converters and assembler are opaque canned
 * instances here -- their own conversion logic is covered by {@code
 * OpenAiCompletionsRequestConverterTest} / {@code OpenAiCompletionsResponseConverterTest}.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiCompletionsStrategyTest {

  @Mock private OpenAiCompletionsRequestConverter requestConverter;
  @Mock private OpenAiCompletionsResponseConverter responseConverter;
  @Mock private OpenAiCompletionsStreamAssembler streamAssembler;

  @Mock private OpenAIClient client;
  @Mock private ChatService chatService;
  @Mock private ChatCompletionService chatCompletionService;

  @SuppressWarnings("unchecked")
  private final StreamResponse<ChatCompletionChunk> stream = mock(StreamResponse.class);

  private final OpenAiConfiguration configuration =
      new OpenAiConfiguration(
          new Api.Completions(null, null, null, null),
          new Backend.Api("sk-test", null, null, null, RequestCustomizations.of(null, null, null)),
          "gpt-4o",
          Duration.ofMinutes(3));

  private final AgentExecutionContext executionContext = mock(AgentExecutionContext.class);
  private final ChatRequest request =
      new ChatRequest(executionContext, new ConversationSnapshot(List.of(), List.of()));

  private OpenAiCompletionsStrategy strategy;

  @BeforeEach
  void setUp() {
    when(executionContext.configuration()).thenReturn(mock(AgentConfiguration.class));
    strategy = new OpenAiCompletionsStrategy(requestConverter, responseConverter, streamAssembler);
  }

  @Test
  void callsChatCompletionsCreateStreamingAndReturnsConvertedResult() {
    final ChatCompletionCreateParams params =
        ChatCompletionCreateParams.builder().model("gpt-4o").messages(List.of()).build();
    final ChatCompletion completion = canningCompletion();
    final var expected =
        new ChatResult.Completed(
            AssistantMessage.builder().build(), AgentMetrics.builder().build());
    final var expectedSpec =
        new CompletionsRequestSpec(
            "gpt-4o", null, null, null, null, null, RequestCustomizations.of(null, null, null));

    when(requestConverter.toRequest(eq(expectedSpec), any(), eq(request.snapshot())))
        .thenReturn(params);
    when(client.chat()).thenReturn(chatService);
    when(chatService.completions()).thenReturn(chatCompletionService);
    when(chatCompletionService.createStreaming(params)).thenReturn(stream);
    when(streamAssembler.assemble(stream)).thenReturn(completion);
    when(responseConverter.toResult(eq(completion))).thenReturn(expected);

    final ChatResult result = strategy.call(client, configuration, request);

    assertThat(result).isSameAs(expected);
    verify(chatCompletionService).createStreaming(params);
    verify(stream).close();
    verify(responseConverter).toResult(completion);
  }

  // --- Configuration -> CompletionsRequestSpec mapping ----------------------------------------

  @Test
  void mapsModelParametersToSpec() {
    final var config =
        new OpenAiConfiguration(
            new Api.Completions(512, Effort.HIGH, 0.5, 0.9),
            new Backend.Api(
                "sk-test", null, null, null, RequestCustomizations.of(null, null, null)),
            "gpt-4o",
            Duration.ofMinutes(3));
    stubHappyPath();

    strategy.call(client, config, request);

    final ArgumentCaptor<CompletionsRequestSpec> specCaptor =
        ArgumentCaptor.forClass(CompletionsRequestSpec.class);
    verify(requestConverter).toRequest(specCaptor.capture(), any(), any());
    final var spec = specCaptor.getValue();
    assertThat(spec.model()).isEqualTo("gpt-4o");
    assertThat(spec.maxCompletionTokens()).isEqualTo(512L);
    assertThat(spec.maxTokens()).isNull();
    assertThat(spec.temperature()).isEqualTo(0.5);
    assertThat(spec.topP()).isEqualTo(0.9);
    assertThat(spec.reasoningEffort()).isEqualTo("high");
  }

  @Test
  void mapsEachEffortLevelToItsLowercaseWireValue() {
    for (final var entry :
        Map.of(
                Effort.MINIMAL, "minimal",
                Effort.LOW, "low",
                Effort.MEDIUM, "medium",
                Effort.HIGH, "high",
                Effort.XHIGH, "xhigh",
                Effort.MAX, "max")
            .entrySet()) {
      final var config =
          new OpenAiConfiguration(
              new Api.Completions(null, entry.getKey(), null, null),
              new Backend.Api(
                  "sk-test", null, null, null, RequestCustomizations.of(null, null, null)),
              "gpt-4o",
              Duration.ofMinutes(3));
      stubHappyPath();
      clearInvocations(requestConverter);

      strategy.call(client, config, request);

      final ArgumentCaptor<CompletionsRequestSpec> specCaptor =
          ArgumentCaptor.forClass(CompletionsRequestSpec.class);
      verify(requestConverter).toRequest(specCaptor.capture(), any(), any());
      assertThat(specCaptor.getValue().reasoningEffort()).isEqualTo(entry.getValue());
    }
  }

  @Test
  void omitsUnsetParametersFromSpec() {
    final var config =
        new OpenAiConfiguration(
            new Api.Completions(null, null, null, null),
            new Backend.Api(
                "sk-test", null, null, null, RequestCustomizations.of(null, null, null)),
            "gpt-4o",
            Duration.ofMinutes(3));
    stubHappyPath();

    strategy.call(client, config, request);

    final ArgumentCaptor<CompletionsRequestSpec> specCaptor =
        ArgumentCaptor.forClass(CompletionsRequestSpec.class);
    verify(requestConverter).toRequest(specCaptor.capture(), any(), any());
    final var spec = specCaptor.getValue();
    assertThat(spec.reasoningEffort()).isNull();
    assertThat(spec.maxCompletionTokens()).isNull();
    assertThat(spec.temperature()).isNull();
    assertThat(spec.topP()).isNull();
  }

  @Test
  void throwsWhenConfiguredWithResponsesApiFamily() {
    final var config =
        new OpenAiConfiguration(
            new Api.Responses(null, null, null, null),
            new Backend.Api(
                "sk-test", null, null, null, RequestCustomizations.of(null, null, null)),
            "gpt-5",
            Duration.ofMinutes(3));

    assertThatThrownBy(() -> strategy.call(client, config, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "OpenAiCompletionsStrategy requires the 'completions' API family, but was configured"
                + " with 'responses'");
  }

  private void stubHappyPath() {
    final ChatCompletionCreateParams params =
        ChatCompletionCreateParams.builder().model("gpt-4o").messages(List.of()).build();
    when(requestConverter.toRequest(any(), any(), any())).thenReturn(params);
    when(client.chat()).thenReturn(chatService);
    when(chatService.completions()).thenReturn(chatCompletionService);
    when(chatCompletionService.createStreaming(params)).thenReturn(stream);
    when(streamAssembler.assemble(stream)).thenReturn(canningCompletion());
    when(responseConverter.toResult(any()))
        .thenReturn(
            new ChatResult.Completed(
                AssistantMessage.builder().build(), AgentMetrics.builder().build()));
  }

  private static ChatCompletion canningCompletion() {
    final String json =
        """
        {
          "id": "chatcmpl_123",
          "object": "chat.completion",
          "created": 0,
          "model": "gpt-4o",
          "choices": [
            {
              "index": 0,
              "finish_reason": "stop",
              "message": {"role": "assistant", "content": "Hello there"}
            }
          ]
        }
        """;
    try {
      return ObjectMappers.jsonMapper().readValue(json, ChatCompletion.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse test fixture JSON", e);
    }
  }
}
