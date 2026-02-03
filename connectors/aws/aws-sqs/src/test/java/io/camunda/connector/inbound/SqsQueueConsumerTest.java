/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.inbound.CorrelationRequest;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.ActivationConditionNotMet;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.Other;
import io.camunda.connector.api.inbound.CorrelationResult.Success.MessagePublished;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.inbound.model.SqsInboundProperties;
import io.camunda.connector.inbound.model.SqsInboundQueueProperties;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

@ExtendWith(MockitoExtension.class)
public class SqsQueueConsumerTest {

  @Mock private SqsClient sqsClient;
  private SqsInboundProperties properties;
  private SqsInboundQueueProperties queue;
  @Mock private InboundConnectorContext context;
  @Mock private ReceiveMessageResponse receiveMessageResult;
  @Mock private List<Message> messages;
  private Message message;
  @Captor private ArgumentCaptor<ReceiveMessageRequest> requestArgumentCaptor;
  private List<Message> emptyMessageList;

  private SqsQueueConsumer consumer;

  @BeforeEach
  void setUp() {
    properties = new SqsInboundProperties();

    message = Message.builder().messageId("message id").body("body msg")
        .build();

    queue = new SqsInboundQueueProperties("us-east-1", "my-queue", null, null, "1");

    properties.setQueue(queue);

    consumer = new SqsQueueConsumer(sqsClient, properties, context);
    emptyMessageList = Collections.emptyList();
  }

  @Test
  void run_shouldActivate() throws InterruptedException {
    // given
    when(sqsClient.receiveMessage(requestArgumentCaptor.capture()))
        .thenReturn(receiveMessageResult);
    when(receiveMessageResult.messages()).thenReturn(messages);
    when(messages.iterator())
        .thenReturn(Collections.singletonList(message).iterator())
        .thenReturn(emptyMessageList.iterator());
    when(context.correlate(any(CorrelationRequest.class)))
        .thenReturn(new MessagePublished(null, 1L, null));
    // when
    Thread thread = new Thread(() -> consumer.run());
    consumer.setQueueConsumerActive(false);
    thread.start();
    thread.join();
    // then
    verify(sqsClient, atLeast(1)).receiveMessage(any(ReceiveMessageRequest.class));
    verify(context).correlate(any(CorrelationRequest.class));
    verify(sqsClient).deleteMessage(queue.url(), message.receiptHandle());

    ReceiveMessageRequest receiveMessageRequest = requestArgumentCaptor.getValue();
    assertThat(receiveMessageRequest.attributeNamesAsStrings()).isEqualTo(List.of("All"));
    assertThat(receiveMessageRequest.messageAttributeNames()).isEqualTo(List.of("All"));
  }

  @Test
  void run_shouldActivateWithAttributes() throws InterruptedException {
    // given
    List<String> attributeNames = Collections.singletonList("attribute");
    List<String> messageAttributeNames = Collections.singletonList("attribute");
    queue =
        new SqsInboundQueueProperties(
            "us-east-1", "my-queue", attributeNames, messageAttributeNames, "1");
    properties.setQueue(queue);
    when(sqsClient.receiveMessage(requestArgumentCaptor.capture()))
        .thenReturn(receiveMessageResult);
    when(receiveMessageResult.messages()).thenReturn(messages);
    when(messages.iterator())
        .thenReturn(Collections.singletonList(message).iterator())
        .thenReturn(emptyMessageList.iterator());
    when(context.correlate(any(CorrelationRequest.class)))
        .thenReturn(new MessagePublished(null, 1L, null));

    // when
    Thread thread =
        new Thread(
            () -> {
              consumer.run();
            });
    consumer.setQueueConsumerActive(false);
    thread.start();
    thread.join();
    // then
    verify(sqsClient, atLeast(1)).receiveMessage(any(ReceiveMessageRequest.class));
    verify(context)
        .correlate(
            CorrelationRequest.builder()
                .variables(MessageMapper.toSqsInboundMessage(message))
                .messageId(message.messageId())
                .build());
    ReceiveMessageRequest receiveMessageRequest = requestArgumentCaptor.getValue();
    assertThat(receiveMessageRequest.attributeNamesAsStrings()).isEqualTo(attributeNames);
    assertThat(receiveMessageRequest.messageAttributeNames()).isEqualTo(messageAttributeNames);
    verify(sqsClient).deleteMessage(queue.url(), message.receiptHandle());
  }

  @Test
  void correlationFailure_ForwardToUpstream() throws InterruptedException {
    // given
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(receiveMessageResult);
    when(receiveMessageResult.messages()).thenReturn(messages);
    when(messages.iterator())
        .thenReturn(Collections.singletonList(message).iterator())
        .thenReturn(emptyMessageList.iterator());
    when(context.correlate(any(CorrelationRequest.class)))
        .thenReturn(new Other(new RuntimeException()));
    // when
    Thread thread =
        new Thread(
            () -> {
              consumer.run();
            });
    consumer.setQueueConsumerActive(false);
    thread.start();
    thread.join();
    // then
    verify(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));
    verify(context).correlate(any(CorrelationRequest.class));
    verifyNoMoreInteractions(sqsClient);
  }

  @Test
  void correlationFailure_Ignored() throws InterruptedException {
    // given
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(receiveMessageResult);
    when(receiveMessageResult.messages()).thenReturn(messages);
    when(messages.iterator())
        .thenReturn(Collections.singletonList(message).iterator())
        .thenReturn(emptyMessageList.iterator());
    when(context.correlate(any(CorrelationRequest.class)))
        .thenReturn(new ActivationConditionNotMet(true));
    // when
    Thread thread =
        new Thread(
            () -> {
              consumer.run();
            });
    consumer.setQueueConsumerActive(false);
    thread.start();
    thread.join();
    // then
    verify(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));
    verify(context).correlate(any(CorrelationRequest.class));
    verify(sqsClient).deleteMessage(queue.url(), message.receiptHandle());
  }

  @Test
  void consumeRun_withNoResults() throws InterruptedException {
    // given
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(receiveMessageResult);
    when(receiveMessageResult.messages()).thenReturn(Collections.emptyList());
    // when
    Thread thread =
        new Thread(
            () -> {
              consumer.run();
              verify(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));
            });
    consumer.setQueueConsumerActive(false);
    thread.start();
    thread.join();
    // then
    verify(context).reportHealth(Health.down());
    verifyNoMoreInteractions(context);
  }
}
