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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.ActivationConditionNotMet;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.Other;
import io.camunda.connector.api.inbound.CorrelationResult.Success.MessagePublished;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.inbound.model.SqsInboundProperties;
import io.camunda.connector.inbound.model.SqsInboundQueueProperties;
import io.camunda.connector.inbound.model.message.SqsInboundMessage;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SqsQueueConsumerTest {

  @Mock private AmazonSQS sqsClient;
  private SqsInboundProperties properties;
  private SqsInboundQueueProperties queue;
  @Mock private InboundConnectorContext context;
  @Mock private ReceiveMessageResult receiveMessageResult;
  @Mock private List<Message> messages;
  private Message message;
  @Captor private ArgumentCaptor<ReceiveMessageRequest> requestArgumentCaptor;
  private List<Message> emptyMessageList;

  private SqsQueueConsumer consumer;

  @BeforeEach
  void setUp() {
    properties = new SqsInboundProperties();

    message = new Message().withMessageId("message id").withBody("body msg");

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
    when(receiveMessageResult.getMessages()).thenReturn(messages);
    when(messages.iterator())
        .thenReturn(Collections.singletonList(message).iterator())
        .thenReturn(emptyMessageList.iterator());
    when(context.correlateWithResult(any())).thenReturn(new MessagePublished(null, 1L, null));
    // when
    Thread thread =
        new Thread(
            () -> {
              consumer.run();
            });
    thread.start();
    consumer.setQueueConsumerActive(false);
    thread.join();
    // then
    verify(sqsClient, atLeast(1)).receiveMessage(any(ReceiveMessageRequest.class));
    verify(context).correlateWithResult(any(SqsInboundMessage.class));
    verify(sqsClient).deleteMessage(queue.url(), message.getReceiptHandle());

    ReceiveMessageRequest receiveMessageRequest = requestArgumentCaptor.getValue();
    assertThat(receiveMessageRequest.getAttributeNames()).isEqualTo(List.of("All"));
    assertThat(receiveMessageRequest.getMessageAttributeNames()).isEqualTo(List.of("All"));
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
    when(receiveMessageResult.getMessages()).thenReturn(messages);
    when(messages.iterator())
        .thenReturn(Collections.singletonList(message).iterator())
        .thenReturn(emptyMessageList.iterator());
    when(context.correlateWithResult(any())).thenReturn(new MessagePublished(null, 1L, null));

    // when
    Thread thread =
        new Thread(
            () -> {
              consumer.run();
            });
    thread.start();
    consumer.setQueueConsumerActive(false);
    thread.join();
    // then
    verify(sqsClient, atLeast(1)).receiveMessage(any(ReceiveMessageRequest.class));
    verify(context).correlateWithResult(MessageMapper.toSqsInboundMessage(message));
    ReceiveMessageRequest receiveMessageRequest = requestArgumentCaptor.getValue();
    assertThat(receiveMessageRequest.getAttributeNames()).isEqualTo(attributeNames);
    assertThat(receiveMessageRequest.getMessageAttributeNames()).isEqualTo(messageAttributeNames);
    verify(sqsClient).deleteMessage(queue.url(), message.getReceiptHandle());
  }

  @Test
  void correlationFailure_ForwardToUpstream() throws InterruptedException {
    // given
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(receiveMessageResult);
    when(receiveMessageResult.getMessages()).thenReturn(messages);
    when(messages.iterator())
        .thenReturn(Collections.singletonList(message).iterator())
        .thenReturn(emptyMessageList.iterator());
    when(context.correlateWithResult(any())).thenReturn(new Other(new RuntimeException()));
    // when
    Thread thread =
        new Thread(
            () -> {
              consumer.run();
            });
    thread.start();
    consumer.setQueueConsumerActive(false);
    thread.join();
    // then
    verify(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));
    verify(context).correlateWithResult(any(SqsInboundMessage.class));
    verifyNoMoreInteractions(sqsClient);
  }

  @Test
  void correlationFailure_Ignored() throws InterruptedException {
    // given
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(receiveMessageResult);
    when(receiveMessageResult.getMessages()).thenReturn(messages);
    when(messages.iterator())
        .thenReturn(Collections.singletonList(message).iterator())
        .thenReturn(emptyMessageList.iterator());
    when(context.correlateWithResult(any())).thenReturn(new ActivationConditionNotMet(true));
    // when
    Thread thread =
        new Thread(
            () -> {
              consumer.run();
            });
    thread.start();
    consumer.setQueueConsumerActive(false);
    thread.join();
    // then
    verify(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));
    verify(context).correlateWithResult(any(SqsInboundMessage.class));
    verify(sqsClient).deleteMessage(queue.url(), message.getReceiptHandle());
  }

  @Test
  void consumeRun_withNoResults() throws InterruptedException {
    // given
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(receiveMessageResult);
    when(receiveMessageResult.getMessages()).thenReturn(Collections.emptyList());
    // when
    Thread thread =
        new Thread(
            () -> {
              consumer.run();
              verify(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));
            });
    thread.start();
    consumer.setQueueConsumerActive(false);
    thread.join();
    // then
    verify(context).reportHealth(Health.down());
    verifyNoMoreInteractions(context);
  }
}
