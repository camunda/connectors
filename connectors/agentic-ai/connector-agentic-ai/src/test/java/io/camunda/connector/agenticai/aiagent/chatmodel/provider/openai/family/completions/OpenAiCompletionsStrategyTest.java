/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import io.camunda.connector.agenticai.aiagent.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiModelCapabilities;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the {@code Chat Completions} family strategy's wiring: that it drives {@code
 * client.chat().completions().createStreaming(...)} (rather than the Responses accessor), assembles
 * and closes the stream, and threads the assembled completion plus an execution {@link Duration}
 * into the response converter. The vendor request/response objects returned by the mocked
 * converters and assembler are opaque canned instances here -- their own conversion logic is
 * covered by {@code OpenAiCompletionsRequestConverterTest} / {@code
 * OpenAiCompletionsResponseConverterTest}.
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

  private final OpenAiModelCapabilities capabilities = openAiCaps();
  private final ChatModelRequest request =
      new ChatModelRequest(
          mock(AgentExecutionContext.class), new ConversationSnapshot(List.of(), List.of()));

  private OpenAiCompletionsStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new OpenAiCompletionsStrategy(requestConverter, responseConverter, streamAssembler);
  }

  @Test
  void callsChatCompletionsCreateStreamingAndReturnsConvertedResult() {
    final ChatCompletionCreateParams params =
        ChatCompletionCreateParams.builder().model("gpt-4o").messages(List.of()).build();
    final ChatCompletion completion = canningCompletion();
    final var expected =
        new ChatModelResult.Completed(
            AssistantMessage.builder().build(), AgentMetrics.builder().build());

    when(requestConverter.toChatCompletionCreateParams(
            request.executionContext(), request.snapshot(), capabilities, true))
        .thenReturn(params);
    when(client.chat()).thenReturn(chatService);
    when(chatService.completions()).thenReturn(chatCompletionService);
    when(chatCompletionService.createStreaming(params)).thenReturn(stream);
    when(streamAssembler.assemble(stream)).thenReturn(completion);
    when(responseConverter.toResult(eq(completion), any(Duration.class))).thenReturn(expected);

    final ChatModelResult result = strategy.call(client, request, capabilities, true);

    assertThat(result).isSameAs(expected);
    verify(chatCompletionService).createStreaming(params);
    verify(stream).close();

    final ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(responseConverter).toResult(eq(completion), durationCaptor.capture());
    assertThat(durationCaptor.getValue()).isNotNull();
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

  private static OpenAiModelCapabilities openAiCaps() {
    return new OpenAiModelCapabilities(
        new CoreModelCapabilities(
            List.of(Modality.TEXT), List.of(Modality.TEXT), List.of(Modality.TEXT), null, null),
        null);
  }
}
