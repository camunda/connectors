/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai.family.responses;

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
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.services.blocking.ResponseService;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.framework.openai.OpenAiModelCapabilities;
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
 * Unit tests for the {@code Responses} family strategy's wiring: that it drives {@code
 * client.responses().createStreaming(...)} (rather than the Chat Completions accessor), assembles
 * and closes the stream, and threads the assembled response plus an execution {@link Duration} into
 * the response converter. The vendor request/response objects returned by the mocked converters and
 * assembler are opaque canned instances here -- their own conversion logic is covered by {@code
 * OpenAiResponsesRequestConverterTest} / {@code OpenAiResponsesResponseConverterTest}.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiResponsesStrategyTest {

  @Mock private OpenAiResponsesRequestConverter requestConverter;
  @Mock private OpenAiResponsesResponseConverter responseConverter;
  @Mock private OpenAiResponsesStreamAssembler streamAssembler;

  @Mock private OpenAIClient client;
  @Mock private ResponseService responseService;

  @SuppressWarnings("unchecked")
  private final StreamResponse<ResponseStreamEvent> stream = mock(StreamResponse.class);

  private final OpenAiModelCapabilities capabilities = openAiCaps();
  private final ChatModelRequest request =
      new ChatModelRequest(
          mock(AgentExecutionContext.class), new ConversationSnapshot(List.of(), List.of()));

  private OpenAiResponsesStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new OpenAiResponsesStrategy(requestConverter, responseConverter, streamAssembler);
  }

  @Test
  void callsResponsesCreateStreamingAndReturnsConvertedResult() {
    final ResponseCreateParams params =
        ResponseCreateParams.builder().model("gpt-5").input("test").build();
    final Response response = canningResponse();
    final var expected =
        new ChatModelResult.Completed(
            AssistantMessage.builder().build(), AgentMetrics.builder().build());

    when(requestConverter.toResponseCreateParams(
            request.executionContext(), request.snapshot(), capabilities, true))
        .thenReturn(params);
    when(client.responses()).thenReturn(responseService);
    when(responseService.createStreaming(params)).thenReturn(stream);
    when(streamAssembler.assemble(stream)).thenReturn(response);
    when(responseConverter.toResult(eq(response), any(Duration.class))).thenReturn(expected);

    final ChatModelResult result = strategy.call(client, request, capabilities, true);

    assertThat(result).isSameAs(expected);
    verify(responseService).createStreaming(params);
    verify(stream).close();

    final ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(responseConverter).toResult(eq(response), durationCaptor.capture());
    assertThat(durationCaptor.getValue()).isNotNull();
  }

  private static Response canningResponse() {
    final String json =
        """
        {
          "id": "resp_123",
          "object": "response",
          "created_at": 0,
          "model": "gpt-5",
          "output": [],
          "parallel_tool_calls": true,
          "tool_choice": "auto",
          "tools": []
        }
        """;
    try {
      return ObjectMappers.jsonMapper().readValue(json, Response.class);
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
