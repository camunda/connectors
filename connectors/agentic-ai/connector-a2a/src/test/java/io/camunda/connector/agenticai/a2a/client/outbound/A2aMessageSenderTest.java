/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.a2a.client.common.model.part.A2aTextPart;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aOutgoingMessage;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkAgentCard;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClient;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClientListener;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aCommonSendMessageConfiguration;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aCommonSendMessageConfiguration.A2aResponseRetrievalMode;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aSendMessageOperationParametersBuilder;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aStandaloneOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class A2aMessageSenderTest {
  private static final String MESSAGE_ID = "message-1";
  @Mock private A2aSdkClientFactory clientFactory;
  @Mock private A2aSdkClient client;
  @Mock private A2aSdkAgentCard agentCard;
  @InjectMocks private A2aMessageSenderImpl messageSender;
  private final AtomicReference<A2aSdkClientListener> listenerRef = new AtomicReference<>();

  @BeforeEach
  void setUp() {
    when(clientFactory.buildClient(eq(agentCard), any(), any()))
        .thenAnswer(inv -> {
          listenerRef.set(inv.getArgument(1));
          return client;
        });
  }

  @Test
  void shouldReturnImmediateResultWhenCompleted() {
    var operation = newSendMessageOperation(Duration.ofSeconds(1));
    var expectedResult = messageResult(MESSAGE_ID);
    mockClientSendMessage(expectedResult);

    var actualResult = messageSender.sendMessage(agentCard, operation);
    assertThat(actualResult).isSameAs(expectedResult);
    verify(client).close();
  }

  @ParameterizedTest
  @MethodSource("provideBlockingAndPollingModes")
  void shouldPassBlockingAndPollingModesToClientFactory(A2aResponseRetrievalMode retrievalMode) {
    var operation =
        new SendMessageOperationConfiguration(
            A2aSendMessageOperationParametersBuilder.builder().text("hello").build(),
            new A2aCommonSendMessageConfiguration(retrievalMode, 0, Duration.ofSeconds(1)));
    mockClientSendMessage(messageResult(MESSAGE_ID));

    messageSender.sendMessage(agentCard, operation);

    //noinspection resource
    verify(clientFactory)
        .buildClient(
            eq(agentCard),
            any(),
            assertArg(config -> {
              assertThat(config.blocking())
                  .isEqualTo(retrievalMode instanceof A2aResponseRetrievalMode.Blocking);
              assertThat(config.pushNotificationConfig()).isNull();
            }));
    verify(client).close();
  }

  private static Stream<A2aResponseRetrievalMode> provideBlockingAndPollingModes() {
    return Stream.of(
        new A2aResponseRetrievalMode.Blocking(), new A2aResponseRetrievalMode.Polling());
  }

  @ParameterizedTest
  @NullSource
  @MethodSource("provideAuthenticationSchemes")
  void shouldPassNotificationModeWithAuthSchemeToClientFactory(List<String> authenticationSchemes) {
    var webhookUrl = "https://example.com/webhook";
    var credentials = "bXl1c2VyOm15cGFzc3dvcmQ=";
    var token = "a-token";
    var notification =
        new A2aResponseRetrievalMode.Notification(
            webhookUrl, token, authenticationSchemes, credentials);

    var operation =
        new SendMessageOperationConfiguration(
            A2aSendMessageOperationParametersBuilder.builder().text("hello").build(),
            new A2aCommonSendMessageConfiguration(notification, 0, Duration.ofSeconds(1)));
    mockClientSendMessage(messageResult(MESSAGE_ID));

    messageSender.sendMessage(agentCard, operation);

    //noinspection resource
    verify(clientFactory)
        .buildClient(
            eq(agentCard),
            any(),
            assertArg(config -> {
              assertThat(config.blocking()).isFalse();
              assertThat(config.pushNotificationConfig()).isNotNull();
              assertThat(config.pushNotificationConfig().url()).isEqualTo(webhookUrl);
              assertThat(config.pushNotificationConfig().token()).isEqualTo(token);
              assertThat(config.pushNotificationConfig().authSchemes())
                  .containsExactlyElementsOf(notification.authenticationSchemes());
              assertThat(config.pushNotificationConfig().credentials()).isEqualTo(credentials);
            }));
    verify(client).close();
  }

  public static Stream<Arguments> provideAuthenticationSchemes() {
    return Stream.of(
        Arguments.of(List.of()),
        Arguments.of(List.of("Basic")),
        Arguments.of(List.of("Bearer", "CustomScheme1")),
        Arguments.of(List.of("CustomSchemeA", "CustomSchemeB", "CustomSchemeC")));
  }

  @Test
  void shouldTimeoutWaitingForResponse() {
    // very short timeout to keep test fast
    var operation = newSendMessageOperation(Duration.ofMillis(10));
    // Do not trigger the listener -> future never completes
    doAnswer(inv -> null).when(client).sendMessage(any());
    assertThatThrownBy(() -> messageSender.sendMessage(agentCard, operation))
        .isInstanceOf(ConnectorException.class)
        .hasFieldOrPropertyWithValue("errorCode", "A2A_CLIENT_SEND_MESSAGE_RESPONSE_TIMEOUT")
        .hasMessageContaining("Timed out waiting for response from agent");
    verify(client).close();
  }

  @Test
  void shouldWrapErrorFromListener() {
    var operation = newSendMessageOperation(Duration.ofSeconds(1));
    doAnswer(
            inv -> {
              listenerRef.get().onError(new IllegalStateException("boom"));
              return null;
            })
        .when(client)
        .sendMessage(any());
    assertThatThrownBy(() -> messageSender.sendMessage(agentCard, operation))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("boom");
    verify(client).close();
  }

  @Test
  void shouldPassAllMessageFieldsToClient() {
    Document document = mock(Document.class);
    var operation =
        new SendMessageOperationConfiguration(
            A2aSendMessageOperationParametersBuilder.builder()
                .text("hello")
                .documents(List.of(document))
                .contextId("ctx-999")
                .taskId("task-888")
                .referenceTaskIds(List.of("ref-1", "ref-2"))
                .build(),
            new A2aCommonSendMessageConfiguration(null, 1, Duration.ofSeconds(1)));
    var expectedResult = messageResult(MESSAGE_ID);
    ArgumentCaptor<A2aOutgoingMessage> sentMessageCaptor =
        ArgumentCaptor.forClass(A2aOutgoingMessage.class);
    doAnswer(
            inv -> {
              listenerRef.get().onResult(expectedResult);
              return null;
            })
        .when(client)
        .sendMessage(sentMessageCaptor.capture());

    var actualResult = messageSender.sendMessage(agentCard, operation);

    assertThat(actualResult).isSameAs(expectedResult);
    assertThat(sentMessageCaptor.getValue())
        .isEqualTo(
            new A2aOutgoingMessage(
                "hello", List.of(document), "ctx-999", "task-888", List.of("ref-1", "ref-2")));
    verify(client).close();
  }

  private SendMessageOperationConfiguration newSendMessageOperation(Duration timeout) {
    return new SendMessageOperationConfiguration(
        A2aSendMessageOperationParametersBuilder.builder().text("hello").build(),
        new A2aCommonSendMessageConfiguration(null, 0, timeout));
  }

  private A2aMessage messageResult(String messageId) {
    return A2aMessage.builder()
        .messageId(messageId)
        .role(A2aMessage.Role.AGENT)
        .contextId("ctx-123")
        .contents(List.of(new A2aTextPart("content", null)))
        .build();
  }

  private void mockClientSendMessage(A2aSendMessageResult result) {
    doAnswer(
            inv -> {
              listenerRef.get().onResult(result);
              return null;
            })
        .when(client)
        .sendMessage(any());
  }
}
