/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static io.camunda.connector.agenticai.aiagent.ChatModelTranslationFixture.translate;
import static io.camunda.connector.agenticai.aiagent.chatmodel.LogEventsTestSupport.logsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.services.blocking.MessageService;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicModel;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnthropicChatModelTest {

  @Mock private AnthropicClient client;
  @Mock private MessageService messageService;
  @Mock private StreamResponse<RawMessageStreamEvent> streamResponse;
  @Mock private AnthropicMessageRequestConverter requestConverter;
  @Mock private AnthropicMessageResponseConverter responseConverter;
  @Mock private AnthropicMessageStreamAssembler streamAssembler;
  @Mock private Message assembledMessage;

  private final AnthropicConfiguration configuration =
      translate(
          new AnthropicChatModelConfiguration(
              new AnthropicConnection(
                  new AnthropicApiBackend(
                      new AnthropicApiBackend.AnthropicApi("sk-ant-test", null, null, null, null)),
                  new AnthropicModel("claude-sonnet-4-6", null),
                  null)),
          AnthropicConfiguration.class);

  private final AgentExecutionContext executionContext = mock(AgentExecutionContext.class);

  private final ChatRequest request =
      new ChatRequest(executionContext, new ConversationSnapshot(List.of(), List.of()));

  private AnthropicChatModel api;

  @BeforeEach
  void setUp() {
    when(executionContext.configuration()).thenReturn(mock(AgentConfiguration.class));
    api =
        new AnthropicChatModel(
            client, configuration, requestConverter, responseConverter, streamAssembler);
  }

  @Test
  void drivesStreamingAccumulatesAndDelegatesToConverters() {
    final var params = mock(MessageCreateParams.class);
    final var expected =
        new ChatResult.Completed(
            AssistantMessage.builder().build(), AgentMetrics.builder().build());

    when(requestConverter.toMessageCreateParams(any(), any(), any())).thenReturn(params);
    when(client.messages()).thenReturn(messageService);
    when(messageService.createStreaming(params)).thenReturn(streamResponse);
    when(streamAssembler.assemble(streamResponse)).thenReturn(assembledMessage);
    when(responseConverter.toResult(eq(assembledMessage))).thenReturn(expected);

    final var result = api.execute(request);

    assertThat(result).isSameAs(expected);
    verify(requestConverter)
        .toMessageCreateParams(
            configuration, executionContext.configuration().response(), request.snapshot());
    verify(messageService).createStreaming(params);
    verify(streamAssembler).assemble(streamResponse);
    verify(streamResponse).close();
    verify(client, never()).close();
  }

  @Test
  void closesUnderlyingClient() {
    api.close();

    verify(client).close();
  }

  @Test
  void closeLogsErrorInsteadOfThrowingWhenClientCloseFails() {
    doThrow(new RuntimeException("boom")).when(client).close();

    var events = logsOf(AnthropicChatModel.class, api::close);

    verify(client).close();
    assertThat(events)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.ERROR);
              assertThat(event.getFormattedMessage()).isEqualTo("Failed to close AnthropicClient");
            });
  }
}
