/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.services.blocking.MessageService;
import io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
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

  @Mock private AnthropicClient client;
  @Mock private MessageService messageService;
  @Mock private StreamResponse<RawMessageStreamEvent> streamResponse;
  @Mock private AnthropicMessageRequestConverter requestConverter;
  @Mock private AnthropicMessageResponseConverter responseConverter;
  @Mock private AnthropicMessageStreamAssembler streamAssembler;
  @Mock private Message assembledMessage;

  private final ChatRequest request =
      new ChatRequest(
          mock(AgentExecutionContext.class), new ConversationSnapshot(List.of(), List.of()));

  private AnthropicChatModelApi api;

  @BeforeEach
  void setUp() {
    api = new AnthropicChatModelApi(client, requestConverter, responseConverter, streamAssembler);
  }

  @Test
  void drivesStreamingAccumulatesAndDelegatesToConverters() {
    final var params = mock(MessageCreateParams.class);
    final var expected =
        new ChatResult.Completed(
            AssistantMessage.builder().build(), AgentMetrics.builder().build());

    when(requestConverter.toMessageCreateParams(any(), any())).thenReturn(params);
    when(client.messages()).thenReturn(messageService);
    when(messageService.createStreaming(params)).thenReturn(streamResponse);
    when(streamAssembler.assemble(streamResponse)).thenReturn(assembledMessage);
    when(responseConverter.toResult(eq(assembledMessage), any())).thenReturn(expected);

    final var result = api.execute(request);

    assertThat(result).isSameAs(expected);
    verify(requestConverter).toMessageCreateParams(request.executionContext(), request.snapshot());
    verify(messageService).createStreaming(params);
    verify(streamAssembler).assemble(streamResponse);
    verify(streamResponse).close();
    verify(client, never()).close();
  }

  @Test
  void wrapsSdkFailureAsConnectorException() {
    when(requestConverter.toMessageCreateParams(any(), any()))
        .thenReturn(mock(MessageCreateParams.class));
    when(client.messages()).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> api.execute(request))
        .isInstanceOf(ConnectorException.class)
        .extracting(e -> ((ConnectorException) e).getErrorCode())
        .isEqualTo(AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL);
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
