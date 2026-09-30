/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral;

import static io.camunda.connector.agenticai.aiagent.chatmodel.LogEventsTestSupport.logsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.openai.client.OpenAIClient;
import com.openai.core.ObjectMappers;
import com.openai.core.http.Headers;
import com.openai.core.http.StreamResponse;
import com.openai.errors.BadRequestException;
import com.openai.models.ErrorObject;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.services.blocking.ChatService;
import com.openai.services.blocking.chat.ChatCompletionService;
import io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContentFilteredException;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContextWindowExceededException;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.CompletionsRequestSpec;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStreamAssembler;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralBackend.MistralApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralBackend.MistralApiBackend.MistralApiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralEffort;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiRequestCustomizations;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MistralChatModelTest {

  @Mock private OpenAIClient client;
  @Mock private ChatService chatService;
  @Mock private ChatCompletionService chatCompletionService;
  @Mock private OpenAiCompletionsRequestConverter requestConverter;
  @Mock private OpenAiCompletionsResponseConverter responseConverter;
  @Mock private OpenAiCompletionsStreamAssembler streamAssembler;

  @SuppressWarnings("unchecked")
  private final StreamResponse<ChatCompletionChunk> stream = mock(StreamResponse.class);

  private final MistralChatModelConfiguration configuration =
      new MistralChatModelConfiguration(
          new MistralConnection(
              new MistralApiBackend(
                  new MistralApiConnection("sk-mistral-test", null, null, null, null)),
              new MistralModel("mistral-medium-latest"),
              new MistralParameters(512, MistralEffort.HIGH, 0.5, 0.9),
              null));

  private final AgentExecutionContext executionContext = mock(AgentExecutionContext.class);
  private final ChatRequest request =
      new ChatRequest(executionContext, new ConversationSnapshot(List.of(), List.of()));

  private MistralChatModel model;

  @BeforeEach
  void setUp() {
    when(executionContext.configuration()).thenReturn(mock(AgentConfiguration.class));
    model =
        new MistralChatModel(
            client, configuration, requestConverter, responseConverter, streamAssembler);
  }

  private void stubHappyPath(ChatResult expectedResult) {
    final ChatCompletionCreateParams params =
        ChatCompletionCreateParams.builder()
            .model("mistral-medium-latest")
            .messages(List.of())
            .build();
    when(requestConverter.toRequest(any(), any(), any())).thenReturn(params);
    when(client.chat()).thenReturn(chatService);
    when(chatService.completions()).thenReturn(chatCompletionService);
    when(chatCompletionService.createStreaming(params)).thenReturn(stream);
    when(streamAssembler.assemble(stream)).thenReturn(canningCompletion());
    when(responseConverter.toResult(any(), any())).thenReturn(expectedResult);
  }

  @Test
  void mapsConfigurationToSpecAndReturnsConvertedResult() {
    final var expected =
        new ChatResult.Completed(
            AssistantMessage.builder().build(), AgentMetrics.builder().build());
    stubHappyPath(expected);

    final var result = model.execute(request);

    assertThat(result).isSameAs(expected);
    verify(chatCompletionService).createStreaming(any(ChatCompletionCreateParams.class));
    verify(stream).close();

    final ArgumentCaptor<CompletionsRequestSpec> specCaptor =
        ArgumentCaptor.forClass(CompletionsRequestSpec.class);
    verify(requestConverter).toRequest(specCaptor.capture(), any(), any());
    final var spec = specCaptor.getValue();
    assertThat(spec.model()).isEqualTo("mistral-medium-latest");
    assertThat(spec.maxCompletionTokens()).isNull();
    assertThat(spec.maxTokens()).isEqualTo(512L);
    assertThat(spec.temperature()).isEqualTo(0.5);
    assertThat(spec.topP()).isEqualTo(0.9);
    assertThat(spec.reasoningEffort()).isEqualTo("high");
  }

  @Test
  void handlesNullParametersWithoutError() {
    final var noParamsConfig =
        new MistralChatModelConfiguration(
            new MistralConnection(
                new MistralApiBackend(
                    new MistralApiConnection("sk-mistral-test", null, null, null, null)),
                new MistralModel("mistral-medium-latest"),
                null,
                null));
    model =
        new MistralChatModel(
            client, noParamsConfig, requestConverter, responseConverter, streamAssembler);
    stubHappyPath(
        new ChatResult.Completed(
            AssistantMessage.builder().build(), AgentMetrics.builder().build()));

    model.execute(request);

    final ArgumentCaptor<CompletionsRequestSpec> specCaptor =
        ArgumentCaptor.forClass(CompletionsRequestSpec.class);
    verify(requestConverter).toRequest(specCaptor.capture(), any(), any());
    final var spec = specCaptor.getValue();
    assertThat(spec.maxTokens()).isNull();
    assertThat(spec.temperature()).isNull();
    assertThat(spec.topP()).isNull();
    assertThat(spec.reasoningEffort()).isNull();
  }

  @Test
  void modelDefaultEffortOmitsReasoningEffortFromSpec() {
    final var config =
        new MistralChatModelConfiguration(
            new MistralConnection(
                new MistralApiBackend(
                    new MistralApiConnection("sk-mistral-test", null, null, null, null)),
                new MistralModel("mistral-medium-latest"),
                new MistralParameters(null, MistralEffort.MODEL_DEFAULT, null, null),
                null));
    model =
        new MistralChatModel(client, config, requestConverter, responseConverter, streamAssembler);
    stubHappyPath(
        new ChatResult.Completed(
            AssistantMessage.builder().build(), AgentMetrics.builder().build()));

    model.execute(request);

    final ArgumentCaptor<CompletionsRequestSpec> specCaptor =
        ArgumentCaptor.forClass(CompletionsRequestSpec.class);
    verify(requestConverter).toRequest(specCaptor.capture(), any(), any());
    assertThat(specCaptor.getValue().reasoningEffort()).isNull();
  }

  @Test
  void mapsRequestCustomizationsFromBackend() {
    final var config =
        new MistralChatModelConfiguration(
            new MistralConnection(
                new MistralApiBackend(
                    new MistralApiConnection(
                        "sk-mistral-test",
                        null,
                        java.util.Map.of("X-Custom", "value"),
                        null,
                        null)),
                new MistralModel("mistral-medium-latest"),
                null,
                null));
    model =
        new MistralChatModel(client, config, requestConverter, responseConverter, streamAssembler);
    stubHappyPath(
        new ChatResult.Completed(
            AssistantMessage.builder().build(), AgentMetrics.builder().build()));

    model.execute(request);

    final ArgumentCaptor<CompletionsRequestSpec> specCaptor =
        ArgumentCaptor.forClass(CompletionsRequestSpec.class);
    verify(requestConverter).toRequest(specCaptor.capture(), any(), any());
    final OpenAiRequestCustomizations customizations = specCaptor.getValue().customizations();
    assertThat(customizations.headers()).containsEntry("X-Custom", "value");
  }

  @Test
  void rethrowsConnectorExceptionFromResponseConverterVerbatim() {
    final var thrown = new ConnectorException("SOME_OTHER_ERROR_CODE", "unsupported content type");
    stubHappyPath(null);
    when(responseConverter.toResult(any(), any())).thenThrow(thrown);

    assertThatThrownBy(() -> model.execute(request)).isSameAs(thrown);
  }

  @Test
  void propagatesChatModelRejectedExceptionUnwrapped() {
    final var rejection = new ContentFilteredException("blocked by content filtering", null);
    stubHappyPath(null);
    when(responseConverter.toResult(any(), any())).thenThrow(rejection);

    assertThatThrownBy(() -> model.execute(request)).isSameAs(rejection);
  }

  @Test
  void wrapsUnexpectedSdkFailureAsConnectorException() {
    when(requestConverter.toRequest(any(), any(), any())).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> model.execute(request))
        .isInstanceOf(ConnectorException.class)
        .extracting(e -> ((ConnectorException) e).getErrorCode())
        .isEqualTo(AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL);
  }

  @Test
  void mapsContextLengthExceededBadRequestToContextWindowExceeded() {
    final var error =
        ErrorObject.builder()
            .code("context_length_exceeded")
            .message("Context length exceeded.")
            .param((String) null)
            .type("invalid_request_error")
            .build();
    final var thrown =
        BadRequestException.builder().headers(Headers.builder().build()).error(error).build();
    when(requestConverter.toRequest(any(), any(), any())).thenThrow(thrown);

    assertThatThrownBy(() -> model.execute(request))
        .isInstanceOfSatisfying(
            ContextWindowExceededException.class,
            e -> {
              assertThat(e.partialResult()).isNull();
              assertThat(e.getCause()).isSameAs(thrown);
            });
  }

  @Test
  void wrapsOtherBadRequestAsGenericFailedModelCall() {
    final var error =
        ErrorObject.builder()
            .code("invalid_api_key")
            .message("Incorrect API key provided.")
            .param((String) null)
            .type("invalid_request_error")
            .build();
    final var thrown =
        BadRequestException.builder().headers(Headers.builder().build()).error(error).build();
    when(requestConverter.toRequest(any(), any(), any())).thenThrow(thrown);

    assertThatThrownBy(() -> model.execute(request))
        .isInstanceOf(ConnectorException.class)
        .extracting(e -> ((ConnectorException) e).getErrorCode())
        .isEqualTo(AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL);
  }

  @Test
  void closesUnderlyingClient() {
    model.close();

    verify(client).close();
  }

  @Test
  void closeLogsErrorInsteadOfThrowingWhenClientCloseFails() {
    doThrow(new RuntimeException("boom")).when(client).close();

    var events = logsOf(MistralChatModel.class, model::close);

    verify(client).close();
    assertThat(events)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.ERROR);
              assertThat(event.getFormattedMessage()).isEqualTo("Failed to close OpenAIClient");
            });
  }

  private static ChatCompletion canningCompletion() {
    final String json =
        """
        {
          "id": "chatcmpl_123",
          "object": "chat.completion",
          "created": 0,
          "model": "mistral-medium-latest",
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
