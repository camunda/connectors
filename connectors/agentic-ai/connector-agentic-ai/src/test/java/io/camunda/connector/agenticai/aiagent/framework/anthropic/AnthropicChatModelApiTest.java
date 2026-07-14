/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.services.blocking.BetaService;
import com.anthropic.services.blocking.beta.MessageService;
import io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnthropicChatModelApiTest {

  @Mock private AnthropicClientFactory clientFactory;
  @Mock private AnthropicClient client;
  @Mock private BetaService betaService;
  @Mock private MessageService messageService;
  @Mock private StreamResponse<BetaRawMessageStreamEvent> streamResponse;
  @Mock private AnthropicMessageRequestConverter requestConverter;
  @Mock private AnthropicMessageResponseConverter responseConverter;
  @Mock private AnthropicMessageStreamAssembler streamAssembler;
  @Mock private BetaMessage assembledMessage;

  private final AnthropicModelCapabilities capabilities = anthropicCaps();
  private final ChatModelRequest request =
      new ChatModelRequest(
          mock(AgentExecutionContext.class), new ConversationSnapshot(List.of(), List.of()));

  private AnthropicChatModelApi api;

  @BeforeEach
  void setUp() {
    api =
        new AnthropicChatModelApi(
            clientFactory,
            requestConverter,
            responseConverter,
            capabilities,
            true,
            streamAssembler);
  }

  @Test
  void drivesStreamingAccumulatesAndDelegatesToConverters() {
    final var params = mock(MessageCreateParams.class);
    final var expected =
        new ChatModelResult.Completed(
            AssistantMessage.builder().build(), AgentMetrics.builder().build());

    when(requestConverter.toMessageCreateParams(any(), any(), eq(capabilities), eq(true)))
        .thenReturn(params);
    when(clientFactory.create()).thenReturn(client);
    when(client.beta()).thenReturn(betaService);
    when(betaService.messages()).thenReturn(messageService);
    when(messageService.createStreaming(params)).thenReturn(streamResponse);
    when(streamAssembler.assemble(streamResponse)).thenReturn(assembledMessage);
    when(responseConverter.toResult(eq(assembledMessage), any())).thenReturn(expected);

    final var result = api.call(request);

    assertThat(result).isSameAs(expected);
    verify(requestConverter)
        .toMessageCreateParams(request.executionContext(), request.snapshot(), capabilities, true);
    verify(messageService).createStreaming(params);
    verify(streamAssembler).assemble(streamResponse);
    verify(client).close();
    verify(streamResponse).close();
  }

  @Test
  void threadsModelMatchedSignalFromConstructorIntoRequestConverter() {
    final var unmatchedApi =
        new AnthropicChatModelApi(
            clientFactory,
            requestConverter,
            responseConverter,
            capabilities,
            false,
            streamAssembler);
    final var params = mock(MessageCreateParams.class);
    final var expected =
        new ChatModelResult.Completed(
            AssistantMessage.builder().build(), AgentMetrics.builder().build());

    when(requestConverter.toMessageCreateParams(any(), any(), eq(capabilities), eq(false)))
        .thenReturn(params);
    when(clientFactory.create()).thenReturn(client);
    when(client.beta()).thenReturn(betaService);
    when(betaService.messages()).thenReturn(messageService);
    when(messageService.createStreaming(params)).thenReturn(streamResponse);
    when(streamAssembler.assemble(streamResponse)).thenReturn(assembledMessage);
    when(responseConverter.toResult(eq(assembledMessage), any())).thenReturn(expected);

    unmatchedApi.call(request);

    verify(requestConverter)
        .toMessageCreateParams(request.executionContext(), request.snapshot(), capabilities, false);
  }

  @Test
  void wrapsSdkFailureAsConnectorException() {
    when(requestConverter.toMessageCreateParams(any(), any(), eq(capabilities), eq(true)))
        .thenReturn(mock(MessageCreateParams.class));
    when(clientFactory.create()).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> api.call(request))
        .isInstanceOf(ConnectorException.class)
        .extracting(e -> ((ConnectorException) e).getErrorCode())
        .isEqualTo(AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL);
  }

  @Test
  void closesClientWhenSdkCallThrows() {
    final var params = mock(MessageCreateParams.class);

    when(requestConverter.toMessageCreateParams(any(), any(), eq(capabilities), eq(true)))
        .thenReturn(params);
    when(clientFactory.create()).thenReturn(client);
    when(client.beta()).thenReturn(betaService);
    when(betaService.messages()).thenReturn(messageService);
    when(messageService.createStreaming(params)).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> api.call(request))
        .isInstanceOf(ConnectorException.class)
        .extracting(e -> ((ConnectorException) e).getErrorCode())
        .isEqualTo(AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL);

    verify(client).close();
  }

  @Test
  void capabilitiesReturnsInjectedValue() {
    assertThat(api.capabilities()).isSameAs(capabilities);
  }

  private static AnthropicModelCapabilities anthropicCaps() {
    return new AnthropicModelCapabilities(
        new CoreModelCapabilities(
            List.of(Modality.TEXT), List.of(Modality.TEXT), List.of(Modality.TEXT), null, null),
        null);
  }
}
