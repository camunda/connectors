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
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorResult;
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
    LOGGER.info("Started SQS consumer for queue {}", properties.getQueue().getUrl());

    final ReceiveMessageRequest receiveMessageRequest = createReceiveMessageRequest();
    ReceiveMessageResult receiveMessageResult;
    do {
      try {
        receiveMessageResult = sqsClient.receiveMessage(receiveMessageRequest);
        List<Message> messages = receiveMessageResult.getMessages();
        for (Message message : messages) {
          try {
            correlate(message);
            sqsClient.deleteMessage(properties.getQueue().getUrl(), message.getReceiptHandle());
          } catch (ConnectorInputException e) {
            LOGGER.warn("NACK - failed to parse SQS message body: {}", e.getMessage());
          }
        }
      } catch (Exception e) {
        LOGGER.debug("NACK - failed to correlate event", e);
      }
    } while (queueConsumerActive.get());
    LOGGER.info("Stopping SQS consumer for queue {}", properties.getQueue().getUrl());
  }

  private void correlate(final Message message) {
    InboundConnectorResult<?> correlate =
        context.correlate(MessageMapper.toSqsInboundMessage(message));
    if (correlate.isActivated()) {
      LOGGER.debug("Inbound event correlated successfully: {}", correlate.getResponseData());
    } else {
      LOGGER.debug("Inbound event was correlated but not activated: {}", correlate.getErrorData());
    }
  }

  private ReceiveMessageRequest createReceiveMessageRequest() {
    return new ReceiveMessageRequest()
        .withWaitTimeSeconds(Integer.valueOf(properties.getQueue().getPollingWaitTime()))
        .withQueueUrl(properties.getQueue().getUrl())
        .withMessageAttributeNames(
            Optional.ofNullable(properties.getQueue().getMessageAttributeNames())
                .filter(list -> !list.isEmpty())
                .orElse(ALL_ATTRIBUTES_KEY))
        .withAttributeNames(
            Optional.ofNullable(properties.getQueue().getAttributeNames())
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
