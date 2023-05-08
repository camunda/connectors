/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.outbound;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.google.gson.Gson;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.common.suppliers.AmazonSQSClientSupplier;
import io.camunda.connector.common.suppliers.DefaultAmazonSQSClientSupplier;
import io.camunda.connector.common.suppliers.SqsGsonComponentSupplier;
import io.camunda.connector.outbound.model.SqsConnectorRequest;
import io.camunda.connector.outbound.model.SqsConnectorResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "AWSSQS",
    inputVariables = {"authentication", "queue"},
    type = "io.camunda:aws-sqs:1")
public class SqsConnectorFunction implements OutboundConnectorFunction {
  private static final Logger LOGGER = LoggerFactory.getLogger(SqsConnectorFunction.class);

  private final AmazonSQSClientSupplier sqsClientSupplier;
  private final Gson gson;

  public SqsConnectorFunction() {
    this(new DefaultAmazonSQSClientSupplier(), SqsGsonComponentSupplier.gsonInstance());
  }

  public SqsConnectorFunction(final AmazonSQSClientSupplier sqsClientSupplier, final Gson gson) {
    this.sqsClientSupplier = sqsClientSupplier;
    this.gson = gson;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) {
    final var variables = context.getVariables();
    LOGGER.debug("Executing SQS connector with variables : {}", variables);
    final var request = gson.fromJson(variables, SqsConnectorRequest.class);
    context.validate(request);
    context.replaceSecrets(request);
    return new SqsConnectorResult(sendMsgToSqs(request).getMessageId());
  }

  private SendMessageResult sendMsgToSqs(SqsConnectorRequest request) {
    AmazonSQS sqsClient = null;
    try {
      sqsClient =
          sqsClientSupplier.sqsClient(
              request.getAuthentication().getAccessKey(),
              request.getAuthentication().getSecretKey(),
              request.getQueue().getRegion());
      String payload =
          request.getQueue().getMessageBody() instanceof String
              ? request.getQueue().getMessageBody().toString()
              : gson.toJson(request.getQueue().getMessageBody());
      SendMessageRequest message =
          new SendMessageRequest()
              .withQueueUrl(request.getQueue().getUrl())
              .withMessageBody(payload)
              .withMessageAttributes(request.getQueue().getAwsSqsNativeMessageAttributes())
              .withMessageGroupId(request.getQueue().getMessageGroupId())
              .withMessageDeduplicationId(request.getQueue().getMessageDeduplicationId());
      return sqsClient.sendMessage(message);
    } finally {
      if (sqsClient != null) {
        sqsClient.shutdown();
      }
    }
  }
}
