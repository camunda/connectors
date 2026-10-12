/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import io.camunda.connector.api.inbound.*;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy.ForwardErrorToUpstream;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy.Ignore;
import io.camunda.connector.api.inbound.CorrelationResult.Failure;
import io.camunda.connector.api.inbound.CorrelationResult.Success;
import io.camunda.connector.inbound.model.SqsInboundProperties;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

public class SqsQueueConsumer implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(SqsQueueConsumer.class);

  private static final List<MessageSystemAttributeName> ALL_SYSTEM_ATTRIBUTES =
      List.of(MessageSystemAttributeName.ALL);
  private static final List<String> ALL_ATTRIBUTES_KEY = List.of("All");
  private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(1);
  private static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

  private final SqsClient sqsClient;
  private final SqsInboundProperties properties;
  private final InboundConnectorContext context;
  private final AtomicBoolean queueConsumerActive;
  private final Sleeper sleeper;

  public SqsQueueConsumer(
      SqsClient sqsClient, SqsInboundProperties properties, InboundConnectorContext context) {
    this(sqsClient, properties, context, duration -> Thread.sleep(duration.toMillis()));
  }

  SqsQueueConsumer(
      SqsClient sqsClient,
      SqsInboundProperties properties,
      InboundConnectorContext context,
      Sleeper sleeper) {
    this.sqsClient = sqsClient;
    this.properties = properties;
    this.context = context;
    this.queueConsumerActive = new AtomicBoolean(true);
    this.sleeper = sleeper;
  }

  @Override
  public void run() {
    LOGGER.info("Started SQS consumer for queue {}", properties.getQueue().url());

    final ReceiveMessageRequest receiveMessageRequest = createReceiveMessageRequest();
    ReceiveMessageResponse receiveMessageResponse;
    Duration backoff = null;
    do {
      try {
        receiveMessageResponse = sqsClient.receiveMessage(receiveMessageRequest);
      } catch (Exception e) {
        backoff =
            backoff == null
                ? INITIAL_BACKOFF
                : Collections.min(List.of(backoff.multipliedBy(2), MAX_BACKOFF));
        if (!awaitAfterReceiveFailure(e, backoff)) {
          break;
        }
        continue;
      }
      if (backoff != null) {
        backoff = null;
        reportRecovered();
      }
      try {
        List<Message> messages = receiveMessageResponse.messages();
        for (Message message : messages) {
          context.log(
              activity ->
                  activity
                      .withSeverity(Severity.INFO)
                      .withTag(ActivityLogTag.MESSAGE)
                      .withMessage("Received SQS Message with ID " + message.messageId()));
          var result =
              context.correlate(
                  CorrelationRequest.builder()
                      .variables(MessageMapper.toSqsInboundMessage(message))
                      .messageId(message.messageId())
                      .build());
          handleCorrelationResult(message, result);
        }
      } catch (Exception e) {
        LOGGER.debug("NACK - unhandled exception", e);
        context.log(
            activity ->
                activity
                    .withSeverity(Severity.WARNING)
                    .withTag(ActivityLogTag.MESSAGE)
                    .withMessage("NACK - failed to correlate event", e));
      }
    } while (queueConsumerActive.get());
    LOGGER.info("Stopping SQS consumer for queue {}", properties.getQueue().url());
    context.reportHealth(Health.down());
  }

  // Returns false if the consumer was interrupted while waiting and should stop.
  private boolean awaitAfterReceiveFailure(Exception e, Duration backoff) {
    if (backoff.equals(INITIAL_BACKOFF)) {
      LOGGER.error(
          "Failed to receive messages from SQS queue {}, retrying with backoff",
          properties.getQueue().url(),
          e);
      context.log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withTag(ActivityLogTag.CONSUMER)
                  .withMessage("Failed to receive messages from SQS queue", e)
                  .andReportHealth(Health.down(e)));
    } else {
      LOGGER.warn(
          "Still failing to receive messages from SQS queue {}, retrying in {}: {}",
          properties.getQueue().url(),
          backoff,
          e.getMessage());
    }
    try {
      sleeper.sleep(backoff);
      return true;
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private void reportRecovered() {
    LOGGER.info("Resumed receiving messages from SQS queue {}", properties.getQueue().url());
    context.log(
        activity ->
            activity
                .withSeverity(Severity.INFO)
                .withTag(ActivityLogTag.CONSUMER)
                .withMessage("Resumed receiving messages from SQS queue")
                .andReportHealth(Health.up()));
  }

  private void handleCorrelationResult(Message message, CorrelationResult result) {
    switch (result) {
      case Success ignored -> {
        LOGGER.debug("ACK - message correlated successfully");
        sqsClient.deleteMessage(
            DeleteMessageRequest.builder()
                .queueUrl(properties.getQueue().url())
                .receiptHandle(message.receiptHandle())
                .build());
      }

      case Failure failure -> {
        context.log(
            activity ->
                activity
                    .withSeverity(Severity.WARNING)
                    .withTag(ActivityLogTag.MESSAGE)
                    .withMessage(failure.message()));
        switch (failure.handlingStrategy()) {
          case ForwardErrorToUpstream ignored1 -> {
            LOGGER.debug("NACK (requeue) - message not correlated");
          }
          case Ignore ignored -> {
            LOGGER.debug("ACK - message ignored");
            sqsClient.deleteMessage(
                DeleteMessageRequest.builder()
                    .queueUrl(properties.getQueue().url())
                    .receiptHandle(message.receiptHandle())
                    .build());
          }
        }
      }
    }
  }

  private ReceiveMessageRequest createReceiveMessageRequest() {
    return ReceiveMessageRequest.builder()
        .waitTimeSeconds(Math.max(Integer.parseInt(properties.getQueue().pollingWaitTime()), 1))
        .queueUrl(properties.getQueue().url())
        .messageAttributeNames(
            Optional.ofNullable(properties.getQueue().messageAttributeNames())
                .filter(list -> !list.isEmpty())
                .orElse(ALL_ATTRIBUTES_KEY))
        .messageSystemAttributeNames(
            Optional.ofNullable(properties.getQueue().attributeNames())
                .filter(list -> !list.isEmpty())
                .map(names -> names.stream().map(MessageSystemAttributeName::fromValue).toList())
                .orElse(ALL_SYSTEM_ATTRIBUTES))
        .build();
  }

  public boolean isQueueConsumerActive() {
    return queueConsumerActive.get();
  }

  public void setQueueConsumerActive(final boolean isQueueConsumerActive) {
    this.queueConsumerActive.set(isQueueConsumerActive);
  }

  @FunctionalInterface
  interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;
  }
}
