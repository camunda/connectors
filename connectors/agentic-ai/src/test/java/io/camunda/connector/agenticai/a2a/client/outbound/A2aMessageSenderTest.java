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

import io.a2a.client.ClientEvent;
import io.a2a.client.MessageEvent;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClient;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.outbound.convert.A2aDocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aCommonSendMessageConfiguration;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aCommonSendMessageConfiguration.A2aResponseRetrievalMode;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aSendMessageOperationParametersBuilder;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aStandaloneOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class A2aMessageSenderTest {
  private static final String MESSAGE_ID = "message-1";
  @Mock private A2aDocumentToPartConverter documentToPartConverter;
  @Mock private A2aSendMessageResponseHandler sendMessageResponseHandler;
  @Mock private A2aSdkClientFactory clientFactory;
  @Mock private A2aSdkClient client;
  @Mock private AgentCard agentCard;
  @InjectMocks private A2aMessageSenderImpl messageSender;
  private final AtomicReference<BiConsumer<ClientEvent, AgentCard>> consumerRef =
      new AtomicReference<>();

  @BeforeEach
  void setUp() {
    when(clientFactory.buildClient(eq(agentCard), any(), any()))
        .thenAnswer(
            inv -> {
              consumerRef.set(inv.getArgument(1));
              return client;
            });
  }

  @Test
  void shouldReturnImmediateResultWhenCompleted() {
    var operation = newSendMessageOperation(Duration.ofSeconds(1));
    MessageEvent clientEvent = newMessageEvent();
    var expectedResult = messageResult(MESSAGE_ID);
    when(sendMessageResponseHandler.handleClientEvent(clientEvent)).thenReturn(expectedResult);
    mockClientSendMessage(clientEvent);

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

    MessageEvent clientEvent = newMessageEvent();
    var expectedResult = messageResult(MESSAGE_ID);
    when(sendMessageResponseHandler.handleClientEvent(clientEvent)).thenReturn(expectedResult);
    mockClientSendMessage(clientEvent);

    messageSender.sendMessage(agentCard, operation);

    //noinspection resource
    verify(clientFactory)
        .buildClient(
            eq(agentCard),
            any(),
            assertArg(
                config -> {
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

    MessageEvent clientEvent = newMessageEvent();
    var expectedResult = messageResult(MESSAGE_ID);
    when(sendMessageResponseHandler.handleClientEvent(clientEvent)).thenReturn(expectedResult);
    mockClientSendMessage(clientEvent);

    messageSender.sendMessage(agentCard, operation);

    //noinspection resource
    verify(clientFactory)
        .buildClient(
            eq(agentCard),
            any(),
            assertArg(
                config -> {
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
    // Do not trigger consumer -> future never completes
    doAnswer(inv -> null).when(client).sendMessage(any());
    assertThatThrownBy(() -> messageSender.sendMessage(agentCard, operation))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Timed out waiting for response from agent");
    verify(client).close();
  }

  @Test
  void shouldWrapExceptionFromHandler() {
    var operation = newSendMessageOperation(Duration.ofSeconds(1));
    MessageEvent clientEvent = newMessageEvent();
    when(sendMessageResponseHandler.handleClientEvent(clientEvent))
        .thenThrow(new IllegalStateException("boom"));
    mockClientSendMessage(clientEvent);
    assertThatThrownBy(() -> messageSender.sendMessage(agentCard, operation))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("boom");
    verify(client).close();
  }

  @Test
  void shouldConvertDocumentsAndIncludePartsInMessage() {
    Document document = mock(Document.class);
    var operation =
        new SendMessageOperationConfiguration(
            A2aSendMessageOperationParametersBuilder.builder()
                .text("hello")
                .documents(List.of(document))
                .build(),
            sendMessageSettings());

    MessageEvent clientEvent = newMessageEvent();
    var expectedResult = messageResult(MESSAGE_ID);
    when(sendMessageResponseHandler.handleClientEvent(clientEvent)).thenReturn(expectedResult);
    // convert document -> part
    var partFromDocument = new TextPart("document-part");
    when(documentToPartConverter.convert(List.of(document)))
        .thenAnswer(invocation -> List.of(partFromDocument));
    ArgumentCaptor<Message> sentMessageCaptor = ArgumentCaptor.forClass(Message.class);
    mockClientSendMessage(clientEvent, sentMessageCaptor);

    var actualResult = messageSender.sendMessage(agentCard, operation);
    assertThat(actualResult).isSameAs(expectedResult);
    Message sentMessage = sentMessageCaptor.getValue();
    assertThat(sentMessage.getParts())
        .satisfiesExactly(
            p -> assertThat(((TextPart) p).getText()).isEqualTo("hello"),
            p -> assertThat(p).isSameAs(partFromDocument));
    verify(client).close();
  }

  @Test
  void shouldIncludeContextIdWhenProvided() {
    var operation =
        new SendMessageOperationConfiguration(
            A2aSendMessageOperationParametersBuilder.builder()
                .text("hello")
                .contextId("ctx-456")
                .build(),
            sendMessageSettings());

    MessageEvent clientEvent = newMessageEvent();
    var expectedResult = messageResult(MESSAGE_ID);
    when(sendMessageResponseHandler.handleClientEvent(clientEvent)).thenReturn(expectedResult);
    ArgumentCaptor<Message> sentMessageCaptor = ArgumentCaptor.forClass(Message.class);
    mockClientSendMessage(clientEvent, sentMessageCaptor);

    messageSender.sendMessage(agentCard, operation);

    Message sentMessage = sentMessageCaptor.getValue();
    assertThat(sentMessage.getContextId()).isEqualTo("ctx-456");
    verify(client).close();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  ", "\t"})
  void shouldNotIncludeContextIdWhenNullOrBlank(String contextId) {
    var operation =
        new SendMessageOperationConfiguration(
            A2aSendMessageOperationParametersBuilder.builder()
                .text("hello")
                .contextId(contextId)
                .build(),
            sendMessageSettings());

    MessageEvent clientEvent = newMessageEvent();
    var expectedResult = messageResult(MESSAGE_ID);
    when(sendMessageResponseHandler.handleClientEvent(clientEvent)).thenReturn(expectedResult);
    ArgumentCaptor<Message> sentMessageCaptor = ArgumentCaptor.forClass(Message.class);
    mockClientSendMessage(clientEvent, sentMessageCaptor);

    messageSender.sendMessage(agentCard, operation);

    Message sentMessage = sentMessageCaptor.getValue();
    assertThat(sentMessage.getContextId()).isNull();
    verify(client).close();
  }

  @Test
  void shouldIncludeTaskIdWhenProvided() {
    var operation =
        new SendMessageOperationConfiguration(
            A2aSendMessageOperationParametersBuilder.builder()
                .text("hello")
                .taskId("task-789")
                .build(),
            sendMessageSettings());

    MessageEvent clientEvent = newMessageEvent();
    var expectedResult = messageResult(MESSAGE_ID);
    when(sendMessageResponseHandler.handleClientEvent(clientEvent)).thenReturn(expectedResult);
    ArgumentCaptor<Message> sentMessageCaptor = ArgumentCaptor.forClass(Message.class);
    mockClientSendMessage(clientEvent, sentMessageCaptor);

    messageSender.sendMessage(agentCard, operation);

    Message sentMessage = sentMessageCaptor.getValue();
    assertThat(sentMessage.getTaskId()).isEqualTo("task-789");
    verify(client).close();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "\t"})
  void shouldNotIncludeTaskIdWhenNullOrBlank(String taskId) {
    var operation =
        new SendMessageOperationConfiguration(
            A2aSendMessageOperationParametersBuilder.builder().text("hello").taskId(taskId).build(),
            sendMessageSettings());

    MessageEvent clientEvent = newMessageEvent();
    var expectedResult = messageResult(MESSAGE_ID);
    when(sendMessageResponseHandler.handleClientEvent(clientEvent)).thenReturn(expectedResult);
    ArgumentCaptor<Message> sentMessageCaptor = ArgumentCaptor.forClass(Message.class);
    mockClientSendMessage(clientEvent, sentMessageCaptor);

    messageSender.sendMessage(agentCard, operation);

    Message sentMessage = sentMessageCaptor.getValue();
    assertThat(sentMessage.getTaskId()).isNull();
    verify(client).close();
  }

  @Test
  void shouldIncludeReferenceTaskIdsWhenProvided() {
    var operation =
        new SendMessageOperationConfiguration(
            A2aSendMessageOperationParametersBuilder.builder()
                .text("hello")
                .referenceTaskIds(List.of("ref-task-1", "ref-task-2"))
                .build(),
            sendMessageSettings());

    MessageEvent clientEvent = newMessageEvent();
    var expectedResult = messageResult(MESSAGE_ID);
    when(sendMessageResponseHandler.handleClientEvent(clientEvent)).thenReturn(expectedResult);
    ArgumentCaptor<Message> sentMessageCaptor = ArgumentCaptor.forClass(Message.class);
    mockClientSendMessage(clientEvent, sentMessageCaptor);

    messageSender.sendMessage(agentCard, operation);

    Message sentMessage = sentMessageCaptor.getValue();
    assertThat(sentMessage.getReferenceTaskIds()).containsExactly("ref-task-1", "ref-task-2");
    verify(client).close();
  }

  @ParameterizedTest
  @NullSource
  @EmptySource
  void shouldNotIncludeReferenceTaskIdsWhenNullOrEmpty(List<String> referenceTaskIds) {
    var operation =
        new SendMessageOperationConfiguration(
            A2aSendMessageOperationParametersBuilder.builder()
                .text("hello")
                .referenceTaskIds(referenceTaskIds)
                .build(),
            sendMessageSettings());

    MessageEvent clientEvent = newMessageEvent();
    var expectedResult = messageResult(MESSAGE_ID);
    when(sendMessageResponseHandler.handleClientEvent(clientEvent)).thenReturn(expectedResult);
    ArgumentCaptor<Message> sentMessageCaptor = ArgumentCaptor.forClass(Message.class);
    mockClientSendMessage(clientEvent, sentMessageCaptor);

    messageSender.sendMessage(agentCard, operation);

    Message sentMessage = sentMessageCaptor.getValue();
    assertThat(sentMessage.getReferenceTaskIds()).isNull();
    verify(client).close();
  }

  @Test
  void shouldIncludeAllOptionalFieldsWhenProvided() {
    var operation =
        new SendMessageOperationConfiguration(
            A2aSendMessageOperationParametersBuilder.builder()
                .text("hello")
                .contextId("ctx-999")
                .taskId("task-888")
                .referenceTaskIds(List.of("ref-1", "ref-2", "ref-3"))
                .build(),
            sendMessageSettings());

    MessageEvent clientEvent = newMessageEvent();
    var expectedResult = messageResult(MESSAGE_ID);
    when(sendMessageResponseHandler.handleClientEvent(clientEvent)).thenReturn(expectedResult);
    ArgumentCaptor<Message> sentMessageCaptor = ArgumentCaptor.forClass(Message.class);
    mockClientSendMessage(clientEvent, sentMessageCaptor);

    messageSender.sendMessage(agentCard, operation);

    Message sentMessage = sentMessageCaptor.getValue();
    assertThat(sentMessage.getContextId()).isEqualTo("ctx-999");
    assertThat(sentMessage.getTaskId()).isEqualTo("task-888");
    assertThat(sentMessage.getReferenceTaskIds()).containsExactly("ref-1", "ref-2", "ref-3");
    verify(client).close();
  }

  private static A2aCommonSendMessageConfiguration sendMessageSettings() {
    return new A2aCommonSendMessageConfiguration(null, 1, Duration.ofSeconds(1));
  }

  private SendMessageOperationConfiguration newSendMessageOperation(Duration timeout) {
    return new SendMessageOperationConfiguration(
        A2aSendMessageOperationParametersBuilder.builder().text("hello").build(),
        new A2aCommonSendMessageConfiguration(null, 0, timeout));
  }

  private MessageEvent newMessageEvent() {
    Message message =
        new Message.Builder()
            .messageId(MESSAGE_ID)
            .role(Message.Role.AGENT)
            .parts(List.of(new TextPart("Hi")))
            .build();
    return new MessageEvent(message);
  }

  private A2aMessage messageResult(String messageId) {
    return A2aMessage.builder()
        .messageId(messageId)
        .role(A2aMessage.Role.AGENT)
        .contextId("ctx-123")
        .contents(List.of(new TextContent("content", null)))
        .build();
  }

  private void mockClientSendMessage(
      ClientEvent clientEvent, ArgumentCaptor<Message> sentMessageCaptor) {
    doAnswer(
            inv -> {
              consumerRef.get().accept(clientEvent, agentCard);
              return null;
            })
        .when(client)
        .sendMessage(sentMessageCaptor != null ? sentMessageCaptor.capture() : any());
  }

  private void mockClientSendMessage(ClientEvent clientEvent) {
    mockClientSendMessage(clientEvent, null);
  }
}
