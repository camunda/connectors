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
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.inbound.model.SqsInboundProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqsQueueConsumer {

  private static final Logger LOGGER = LoggerFactory.getLogger(SqsQueueConsumer.class);

  private final AmazonSQS sqsClient;
  private final SqsInboundProperties properties;
  private final InboundConnectorContext context;

  public SqsQueueConsumer(
      AmazonSQS sqsClient, SqsInboundProperties properties, InboundConnectorContext context) {
    this.sqsClient = sqsClient;
    this.properties = properties;
    this.context = context;
  }

  public void consumeQueueUntilActivated() {
    LOGGER.info("Started SQS consumer for queue {}", properties.getQueue().getName());
    boolean isNotActivated = true;
    ReceiveMessageRequest receiveMessageRequest =
        new ReceiveMessageRequest()
            .withWaitTimeSeconds(1)
            .withQueueUrl(properties.getQueue().getName());
    if (properties.getQueue().isContainAttributeNames()) {
      receiveMessageRequest.withAttributeNames(properties.getQueue().getAttributeNames());
    }
    if (properties.getQueue().isContainMessageAttributeNames()) {
      receiveMessageRequest.withMessageAttributeNames(
          properties.getQueue().getMessageAttributeNames());
    }
    do {
      ReceiveMessageResult receiveMessageResult = sqsClient.receiveMessage(receiveMessageRequest);

      List<Message> messages = receiveMessageResult.getMessages();
      for (Message message : messages) {
        InboundConnectorResult<?> correlate = context.correlate(message);
        if (correlate.isActivated()) {
          isNotActivated = false;
          break;
        }
      }
    } while (isNotActivated);
  }
}
