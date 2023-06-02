/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorResult;
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
  @Mock private InboundConnectorResult result;
  @Captor private ArgumentCaptor<ReceiveMessageRequest> requestArgumentCaptor;
  private List<Message> emptyMessageList;

  private SqsQueueConsumer consumer;

  @BeforeEach
  void setUp() {
    properties = new SqsInboundProperties();

    message = new Message().withMessageId("message id").withBody("body msg");

    queue = new SqsInboundQueueProperties();
    queue.setUrl("my-queue");
    queue.setAttributeNames(null);
    queue.setMessageAttributeNames(null);
    queue.setPollingWaitTime("1");

    properties.setQueue(queue);

    consumer = new SqsQueueConsumer(sqsClient, properties, context);
    emptyMessageList = Collections.emptyList();
  }

  @Test
  void run_shouldActivate() throws InterruptedException {
    // given
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(receiveMessageResult);
    when(receiveMessageResult.getMessages()).thenReturn(messages);
    when(messages.iterator())
        .thenReturn(Collections.singletonList(message).iterator())
        .thenReturn(emptyMessageList.iterator());
    when(context.correlate(MessageMapper.toSqsInboundMessage(message))).thenReturn(result);
    when(result.isActivated()).thenReturn(true);
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
    verify(context).correlate(any(SqsInboundMessage.class));
  }

  @Test
  void run_shouldActivateWithAttributes() throws InterruptedException {
    // given
    List<String> attributeNames = Collections.singletonList("attribute");
    List<String> messageAttributeNames = Collections.singletonList("attribute");
    queue.setAttributeNames(attributeNames);
    queue.setMessageAttributeNames(messageAttributeNames);
    when(sqsClient.receiveMessage(requestArgumentCaptor.capture()))
        .thenReturn(receiveMessageResult);
    when(receiveMessageResult.getMessages()).thenReturn(messages);
    when(messages.iterator())
        .thenReturn(Collections.singletonList(message).iterator())
        .thenReturn(emptyMessageList.iterator());
    when(context.correlate(MessageMapper.toSqsInboundMessage(message))).thenReturn(result);
    when(result.isActivated()).thenReturn(true);
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
    verify(context).correlate(MessageMapper.toSqsInboundMessage(message));
    ReceiveMessageRequest receiveMessageRequest = requestArgumentCaptor.getValue();
    assertThat(receiveMessageRequest.getAttributeNames()).isEqualTo(attributeNames);
    assertThat(receiveMessageRequest.getMessageAttributeNames()).isEqualTo(messageAttributeNames);
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
    verifyNoInteractions(context);
  }
}
