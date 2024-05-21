/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy.ForwardErrorToUpstream;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy.Ignore;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.CorrelationResult.Failure;
import io.camunda.connector.api.inbound.CorrelationResult.Success;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.inbound.model.SqsInboundProperties;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqsQueueConsumer implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(SqsQueueConsumer.class);

  private static final List<String> ALL_ATTRIBUTES_KEY = List.of("All");

  private final AmazonSQS sqsClient;
  private final SqsInboundProperties properties;
  private final InboundConnectorContext context;
  private final AtomicBoolean queueConsumerActive;

  public SqsQueueConsumer(
      AmazonSQS sqsClient, SqsInboundProperties properties, InboundConnectorContext context) {
    this.sqsClient = sqsClient;
    this.properties = properties;
    this.context = context;
    this.queueConsumerActive = new AtomicBoolean(true);
  }

  @Override
  public void run() {
    LOGGER.info("Started SQS consumer for queue {}", properties.getQueue().url());

    final ReceiveMessageRequest receiveMessageRequest = createReceiveMessageRequest();
    ReceiveMessageResult receiveMessageResult;
    do {
      try {
        receiveMessageResult = sqsClient.receiveMessage(receiveMessageRequest);
        List<Message> messages = receiveMessageResult.getMessages();
        for (Message message : messages) {
          context.log(
              Activity.level(Severity.INFO)
                  .tag("Message")
                  .message("Received SQS Message with ID " + message.getMessageId()));
          var result = context.correlateWithResult(MessageMapper.toSqsInboundMessage(message));
          handleCorrelationResult(message, result);
        }
      } catch (Exception e) {
        LOGGER.debug("NACK - unhandled exception", e);
        context.log(
            Activity.level(Severity.WARNING)
                .tag("Message")
                .message("NACK - failed to correlate event : " + e.getMessage()));
      }
    } while (queueConsumerActive.get());
    LOGGER.info("Stopping SQS consumer for queue {}", properties.getQueue().url());
    context.reportHealth(Health.down());
  }

  private void handleCorrelationResult(Message message, CorrelationResult result) {
    switch (result) {
      case Success ignored -> {
        LOGGER.debug("ACK - message correlated successfully");
        sqsClient.deleteMessage(properties.getQueue().url(), message.getReceiptHandle());
      }

      case Failure failure -> {
        context.log(Activity.level(Severity.WARNING).tag("Message").message(failure.message()));
        switch (failure.handlingStrategy()) {
          case ForwardErrorToUpstream ignored1 -> {
            LOGGER.debug("NACK (requeue) - message not correlated");
          }
          case Ignore ignored -> {
            LOGGER.debug("ACK - message ignored");
            sqsClient.deleteMessage(properties.getQueue().url(), message.getReceiptHandle());
          }
        }
      }
    }
  }

  private ReceiveMessageRequest createReceiveMessageRequest() {
    return new ReceiveMessageRequest()
        .withWaitTimeSeconds(Integer.valueOf(properties.getQueue().pollingWaitTime()))
        .withQueueUrl(properties.getQueue().url())
        .withMessageAttributeNames(
            Optional.ofNullable(properties.getQueue().messageAttributeNames())
                .filter(list -> !list.isEmpty())
                .orElse(ALL_ATTRIBUTES_KEY))
        .withAttributeNames(
            Optional.ofNullable(properties.getQueue().attributeNames())
                .filter(list -> !list.isEmpty())
                .orElse(ALL_ATTRIBUTES_KEY));
  }

  public boolean isQueueConsumerActive() {
    return queueConsumerActive.get();
  }

  public void setQueueConsumerActive(final boolean isQueueConsumerActive) {
    this.queueConsumerActive.set(isQueueConsumerActive);
  }
}
