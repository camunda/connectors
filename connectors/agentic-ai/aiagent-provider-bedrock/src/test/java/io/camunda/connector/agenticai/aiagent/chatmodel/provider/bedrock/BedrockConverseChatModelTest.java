/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import static io.camunda.connector.agenticai.aiagent.testsupport.LogEventsTestSupport.logsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.RequestCustomizations;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.aws.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;

@ExtendWith(MockitoExtension.class)
class BedrockConverseChatModelTest {

  private static final String MODEL_ID = "us.amazon.nova-2-lite-v1:0";

  @Mock private BedrockRuntimeAsyncClient client;
  @Mock private BedrockConverseRequestConverter requestConverter;
  @Mock private BedrockConverseResponseConverter responseConverter;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final BedrockConfiguration configuration =
      new BedrockConfiguration(
          "eu-central-1",
          null,
          new AwsAuthentication.DefaultCredentialsChain(),
          RequestCustomizations.NONE,
          Duration.ofMinutes(3),
          MODEL_ID,
          false,
          null,
          null,
          null);

  private final AgentExecutionContext executionContext = mock(AgentExecutionContext.class);

  private final ChatRequest request =
      new ChatRequest(executionContext, new ConversationSnapshot(List.of(), List.of()));

  private BedrockConverseChatModel api;

  @BeforeEach
  void setUp() {
    when(executionContext.configuration()).thenReturn(mock(AgentConfiguration.class));
    api =
        new BedrockConverseChatModel(
            client, configuration, requestConverter, responseConverter, objectMapper);
  }

  @Test
  void drivesConverseStreamAssemblesAndDelegatesToConverters() {
    final var converseStreamRequest = mock(ConverseStreamRequest.class);
    final var expected =
        new ChatResult.Completed(
            AssistantMessage.builder().build(), AgentMetrics.builder().build());

    when(requestConverter.toConverseStreamRequest(any(), any(), any()))
        .thenReturn(converseStreamRequest);
    // Drives a minimal messageStart/messageStop pair through the real handler built inside
    // execute(), matching what the SDK delivers on a genuine call, so the assembler it feeds is
    // actually complete by the time execute() reads converseResponse() off of it.
    when(client.converseStream(eq(converseStreamRequest), any(ConverseStreamResponseHandler.class)))
        .thenAnswer(
            invocation -> {
              final ConverseStreamResponseHandler handler = invocation.getArgument(1);
              handler.onEventStream(
                  SdkPublisher.fromIterable(
                      List.of(
                          ConverseStreamOutput.messageStartBuilder().role("assistant").build(),
                          ConverseStreamOutput.messageStopBuilder()
                              .stopReason("end_turn")
                              .build())));
              return CompletableFuture.completedFuture(null);
            });
    when(responseConverter.toResult(any(ConverseResponse.class))).thenReturn(expected);

    final var result = api.execute(request);

    assertThat(result).isSameAs(expected);
    verify(requestConverter)
        .toConverseStreamRequest(
            configuration, executionContext.configuration().response(), request.snapshot());
    verify(client)
        .converseStream(eq(converseStreamRequest), any(ConverseStreamResponseHandler.class));

    final ArgumentCaptor<ConverseResponse> responseCaptor =
        ArgumentCaptor.forClass(ConverseResponse.class);
    verify(responseConverter).toResult(responseCaptor.capture());
    assertThat(responseCaptor.getValue().stopReasonAsString()).isEqualTo("end_turn");
    assertThat(responseCaptor.getValue().output().message().roleAsString()).isEqualTo("assistant");
    assertThat(responseCaptor.getValue().output().message().content()).isEmpty();

    verify(client, never()).close();
  }

  @Test
  void closesUnderlyingClient() {
    api.close();

    verify(client).close();
  }

  @Test
  void closeIsIdempotentAndLogsErrorInsteadOfThrowingWhenClientCloseFails() {
    doThrow(new RuntimeException("boom")).when(client).close();

    var events =
        logsOf(
            BedrockConverseChatModel.class,
            () -> {
              api.close();
              api.close();
            });

    verify(client, times(2)).close();
    assertThat(events).hasSize(2);
    assertThat(events)
        .allSatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.ERROR);
              assertThat(event.getFormattedMessage())
                  .isEqualTo("Failed to close BedrockRuntimeAsyncClient");
            });
  }
}
