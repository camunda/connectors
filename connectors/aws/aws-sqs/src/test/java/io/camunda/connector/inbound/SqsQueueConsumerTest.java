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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.ActivityBuilder;
import io.camunda.connector.api.inbound.CorrelationRequest;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.ActivationConditionNotMet;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.Other;
import io.camunda.connector.api.inbound.CorrelationResult.Success.MessagePublished;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.inbound.model.SqsInboundProperties;
import io.camunda.connector.inbound.model.SqsInboundQueueProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

@ExtendWith(MockitoExtension.class)
public class SqsQueueConsumerTest {

  @Mock private SqsClient sqsClient;
  private SqsInboundProperties properties;
  private SqsInboundQueueProperties queue;
  @Mock private InboundConnectorContext context;
  private Message message;
  @Captor private ArgumentCaptor<ReceiveMessageRequest> requestArgumentCaptor;

  private SqsQueueConsumer consumer;

  @BeforeEach
  void setUp() {
    properties = new SqsInboundProperties();

    message = Message.builder().messageId("message id").body("body msg").build();

    queue = new SqsInboundQueueProperties("us-east-1", "my-queue", null, null, "1");

    properties.setQueue(queue);

    consumer = new SqsQueueConsumer(sqsClient, properties, context);
  }

  @Test
  void run_shouldActivate() throws InterruptedException {
    // given
    when(sqsClient.receiveMessage(requestArgumentCaptor.capture()))
        .thenReturn(ReceiveMessageResponse.builder().messages(message).build())
        .thenReturn(
            ReceiveMessageResponse.builder().messages(Collections.<Message>emptyList()).build());
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
    verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));

    ReceiveMessageRequest receiveMessageRequest = requestArgumentCaptor.getValue();
    assertThat(receiveMessageRequest.messageSystemAttributeNames())
        .isEqualTo(List.of(MessageSystemAttributeName.ALL));
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
    consumer = new SqsQueueConsumer(sqsClient, properties, context);
    when(sqsClient.receiveMessage(requestArgumentCaptor.capture()))
        .thenReturn(ReceiveMessageResponse.builder().messages(message).build())
        .thenReturn(
            ReceiveMessageResponse.builder().messages(Collections.<Message>emptyList()).build());
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
    assertThat(receiveMessageRequest.messageSystemAttributeNames())
        .isEqualTo(attributeNames.stream().map(MessageSystemAttributeName::fromValue).toList());
    assertThat(receiveMessageRequest.messageAttributeNames()).isEqualTo(messageAttributeNames);
    verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  void correlationFailure_ForwardToUpstream() throws InterruptedException {
    // given
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(ReceiveMessageResponse.builder().messages(message).build())
        .thenReturn(
            ReceiveMessageResponse.builder().messages(Collections.<Message>emptyList()).build());
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
        .thenReturn(ReceiveMessageResponse.builder().messages(message).build())
        .thenReturn(
            ReceiveMessageResponse.builder().messages(Collections.<Message>emptyList()).build());
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
    verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  void consumeRun_withNoResults() throws InterruptedException {
    // given
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(
            ReceiveMessageResponse.builder().messages(Collections.<Message>emptyList()).build());
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

  @Test
  void receiveFailure_shouldBackOffAndReportHealth() {
    // given
    List<Duration> sleeps = new ArrayList<>();
    consumer = new SqsQueueConsumer(sqsClient, properties, context, sleeps::add);
    var queueMissing = QueueDoesNotExistException.builder().message("queue missing").build();
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenThrow(queueMissing, queueMissing, queueMissing)
        .thenAnswer(
            invocation -> {
              consumer.setQueueConsumerActive(false);
              return ReceiveMessageResponse.builder().messages(List.<Message>of()).build();
            });

    // when
    consumer.run();

    // then
    assertThat(sleeps)
        .containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
    ArgumentCaptor<Consumer<ActivityBuilder>> activityCaptor = ArgumentCaptor.captor();
    verify(context, times(2)).log(activityCaptor.capture());
    List<Activity> activities =
        activityCaptor.getAllValues().stream()
            .map(
                builderConsumer -> {
                  ActivityBuilder builder = Activity.newBuilder();
                  builderConsumer.accept(builder);
                  return builder.build();
                })
            .toList();
    assertThat(activities.get(0).healthChange().getStatus()).isEqualTo(Health.Status.DOWN);
    assertThat(activities.get(1).healthChange()).isEqualTo(Health.up());
    verify(context).reportHealth(Health.down());
  }

  @Test
  void receiveFailure_backoffIsCapped() {
    // given
    List<Duration> sleeps = new ArrayList<>();
    consumer =
        new SqsQueueConsumer(
            sqsClient,
            properties,
            context,
            duration -> {
              sleeps.add(duration);
              if (sleeps.size() == 8) {
                consumer.setQueueConsumerActive(false);
              }
            });
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenThrow(QueueDoesNotExistException.builder().message("queue missing").build());

    // when
    consumer.run();

    // then
    assertThat(sleeps)
        .containsExactly(
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(4),
            Duration.ofSeconds(8),
            Duration.ofSeconds(16),
            Duration.ofSeconds(32),
            Duration.ofSeconds(60),
            Duration.ofSeconds(60));
  }

  @Test
  void receiveFailure_interruptedDuringBackoff_shouldStop() {
    // given
    consumer =
        new SqsQueueConsumer(
            sqsClient,
            properties,
            context,
            duration -> {
              throw new InterruptedException();
            });
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenThrow(QueueDoesNotExistException.builder().message("queue missing").build());

    // when
    consumer.run();

    // then
    verify(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));
    assertThat(Thread.interrupted()).isTrue();
    verify(context).reportHealth(Health.down());
  }
}
