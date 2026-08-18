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
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi.CompletionsParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend.OpenAiApiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiModel;
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

  private final OpenAiChatModelConfiguration configuration =
      new OpenAiChatModelConfiguration(
          new OpenAiConnection(
              new OpenAiCompletionsApi(new CompletionsParameters(null, null, null, null)),
              new OpenAiApiBackend(
                  new OpenAiApiConnection("sk-test", null, null, null, null, null, null)),
              new OpenAiModel("gpt-4o"),
              null));

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

    when(requestConverter.toRequest(eq(configuration), any(), eq(request.snapshot())))
        .thenReturn(params);
    when(client.chat()).thenReturn(chatService);
    when(chatService.completions()).thenReturn(chatCompletionService);
    when(chatCompletionService.createStreaming(params)).thenReturn(stream);
    when(streamAssembler.assemble(stream)).thenReturn(completion);
    when(responseConverter.toResult(eq(completion), any(Duration.class))).thenReturn(expected);

    final ChatResult result = strategy.call(client, configuration, request);

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
}
