/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.ResponseStream;
import com.google.genai.errors.ClientException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContentFilteredException;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContextWindowExceededException;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend.GoogleGeminiApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GeminiChatModelTest {

  @Mock private Client client;
  @Mock private Models models;
  @Mock private ResponseStream<GenerateContentResponse> responseStream;
  @Mock private GeminiContentRequestConverter requestConverter;
  @Mock private GeminiContentResponseConverter responseConverter;
  @Mock private GeminiContentStreamAssembler streamAssembler;

  private final GeminiChatModelConfiguration configuration =
      new GeminiChatModelConfiguration(
          new GeminiConnection(
              new GeminiApiBackend(new GoogleGeminiApi("gm-test", null)),
              new GeminiModel("gemini-3-pro-preview", null),
              null));

  private final AgentExecutionContext executionContext = mock(AgentExecutionContext.class);
  private final AgentConfiguration agentConfiguration = mock(AgentConfiguration.class);

  private final ChatRequest request =
      new ChatRequest(executionContext, new ConversationSnapshot(List.of(), List.of()));

  private GeminiChatModel api;

  @BeforeEach
  void setUp() {
    when(executionContext.configuration()).thenReturn(agentConfiguration);
    // Client#models is a plain public final field populated by the SDK's real constructor, which a
    // Mockito mock never runs -- inject the mocked Models directly onto the mocked Client instance.
    ReflectionTestUtils.setField(client, "models", models);
    api = new GeminiChatModel(client, configuration, requestConverter, responseConverter);
    ReflectionTestUtils.setField(api, "streamAssembler", streamAssembler);
  }

  @Test
  void drivesStreamingAccumulatesAndDelegatesToConverters() {
    final var responseConfiguration = mock(ResponseConfiguration.class);
    when(agentConfiguration.response()).thenReturn(responseConfiguration);

    final var config = GenerateContentConfig.builder().build();
    final List<Content> contents = List.of();
    final var assembledResponse = GenerateContentResponse.builder().build();
    final var expected =
        new ChatResult.Completed(
            AssistantMessage.builder().build(), AgentMetrics.builder().build());

    when(requestConverter.toGenerateContentConfig(any(), any(), any())).thenReturn(config);
    when(requestConverter.toContents(any())).thenReturn(contents);
    when(models.generateContentStream(configuration.model(), contents, config))
        .thenReturn(responseStream);
    when(streamAssembler.assemble(responseStream)).thenReturn(assembledResponse);
    when(responseConverter.toResult(eq(assembledResponse), any())).thenReturn(expected);

    final var result = api.execute(request);

    assertThat(result).isSameAs(expected);
    verify(requestConverter)
        .toGenerateContentConfig(configuration, responseConfiguration, request.snapshot());
    verify(requestConverter).toContents(request.snapshot());
    verify(models).generateContentStream(configuration.model(), contents, config);
    verify(streamAssembler).assemble(responseStream);
    verify(responseStream).close();
    verify(client, never()).close();
  }

  @Test
  void propagatesContentFilteredExceptionFromResponseConverterUnwrapped() {
    final var responseConfiguration = mock(ResponseConfiguration.class);
    when(agentConfiguration.response()).thenReturn(responseConfiguration);

    final var config = GenerateContentConfig.builder().build();
    final List<Content> contents = List.of();
    final var assembledResponse = GenerateContentResponse.builder().build();
    final var rejection =
        new ContentFilteredException(
            "Model response was blocked by provider content filtering.", null);

    when(requestConverter.toGenerateContentConfig(any(), any(), any())).thenReturn(config);
    when(requestConverter.toContents(any())).thenReturn(contents);
    when(models.generateContentStream(configuration.model(), contents, config))
        .thenReturn(responseStream);
    when(streamAssembler.assemble(responseStream)).thenReturn(assembledResponse);
    when(responseConverter.toResult(eq(assembledResponse), any())).thenThrow(rejection);

    assertThatThrownBy(() -> api.execute(request)).isSameAs(rejection);

    verify(responseStream).close();
  }

  @Test
  void wrapsSdkFailureAsConnectorException() {
    when(requestConverter.toGenerateContentConfig(any(), any(), any()))
        .thenReturn(GenerateContentConfig.builder().build());
    when(requestConverter.toContents(any())).thenReturn(List.of());
    when(models.generateContentStream(any(), anyList(), any()))
        .thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> api.execute(request))
        .isInstanceOf(ConnectorException.class)
        .extracting(e -> ((ConnectorException) e).getErrorCode())
        .isEqualTo(ERROR_CODE_FAILED_MODEL_CALL);
  }

  @Test
  void wrapsApiExceptionWithStatusCodeAndStatus() {
    when(requestConverter.toGenerateContentConfig(any(), any(), any()))
        .thenReturn(GenerateContentConfig.builder().build());
    when(requestConverter.toContents(any())).thenReturn(List.of());
    when(models.generateContentStream(any(), anyList(), any()))
        .thenThrow(new ClientException(429, "RESOURCE_EXHAUSTED", "slow down"));

    assertThatThrownBy(() -> api.execute(request))
        .isInstanceOf(ConnectorException.class)
        .satisfies(
            e -> {
              final var connectorException = (ConnectorException) e;
              assertThat(connectorException.getErrorCode()).isEqualTo(ERROR_CODE_FAILED_MODEL_CALL);
              assertThat(connectorException.getMessage())
                  .contains("429")
                  .contains("RESOURCE_EXHAUSTED")
                  .contains("slow down");
            });
  }

  @Test
  void throwsContextWindowExceededExceptionForOverLengthPrompt() {
    when(requestConverter.toGenerateContentConfig(any(), any(), any()))
        .thenReturn(GenerateContentConfig.builder().build());
    when(requestConverter.toContents(any())).thenReturn(List.of());
    when(models.generateContentStream(any(), anyList(), any()))
        .thenThrow(
            new ClientException(
                400,
                "INVALID_ARGUMENT",
                "The input token count (1236488) exceeds the maximum number of tokens allowed"
                    + " (1048576)."));

    assertThatThrownBy(() -> api.execute(request))
        .isInstanceOf(ContextWindowExceededException.class)
        .satisfies(
            e -> {
              final var cwe = (ContextWindowExceededException) e;
              assertThat(cwe.partialResult()).isNull();
              assertThat(cwe.getCause()).isInstanceOf(ClientException.class);
            });
  }

  @Test
  void wrapsAssemblerIllegalStateExceptionAsConnectorExceptionRatherThanSpecialCasingIt() {
    when(requestConverter.toGenerateContentConfig(any(), any(), any()))
        .thenReturn(GenerateContentConfig.builder().build());
    when(requestConverter.toContents(any())).thenReturn(List.of());
    when(models.generateContentStream(any(), anyList(), any())).thenReturn(responseStream);
    when(streamAssembler.assemble(responseStream))
        .thenThrow(new IllegalStateException("Gemini streaming response contained no chunks"));

    assertThatThrownBy(() -> api.execute(request))
        .isInstanceOf(ConnectorException.class)
        .extracting(e -> ((ConnectorException) e).getErrorCode())
        .isEqualTo(ERROR_CODE_FAILED_MODEL_CALL);

    verify(responseStream).close();
  }

  @Test
  void closesUnderlyingClient() {
    api.close();

    verify(client).close();
  }

  @Test
  void closeLogsWarningInsteadOfThrowingWhenClientCloseFails() {
    doThrow(new RuntimeException("boom")).when(client).close();

    api.close();

    verify(client).close();
  }
}
