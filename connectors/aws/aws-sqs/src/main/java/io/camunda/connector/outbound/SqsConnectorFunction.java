/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.outbound;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.AwsUtils;
import io.camunda.connector.aws.CredentialsProviderSupport;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.common.suppliers.AmazonSQSClientSupplier;
import io.camunda.connector.common.suppliers.DefaultAmazonSQSClientSupplier;
import io.camunda.connector.outbound.model.QueueRequestData;
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
  private final ObjectMapper objectMapper;

  public SqsConnectorFunction() {
    this(new DefaultAmazonSQSClientSupplier(), ObjectMapperSupplier.getMapperInstance());
  }

  public SqsConnectorFunction(
      final AmazonSQSClientSupplier sqsClientSupplier, final ObjectMapper objectMapper) {
    this.sqsClientSupplier = sqsClientSupplier;
    this.objectMapper = objectMapper;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) {
    var request = context.bindVariables(SqsConnectorRequest.class);
    var region = AwsUtils.extractRegionOrDefault(request.getConfiguration(), request.getQueue().getRegion());
    AWSCredentialsProvider provider = CredentialsProviderSupport.credentialsProvider(request);
    AmazonSQS sqsClient = sqsClientSupplier.sqsClient(provider, region);
    return new SqsConnectorResult(sendMsgToSqs(sqsClient, request.getQueue()).getMessageId());
  }

  private SendMessageResult sendMsgToSqs(final AmazonSQS sqsClient, final QueueRequestData queue) {
    try {
      String payload =
          queue.getMessageBody() instanceof String
              ? queue.getMessageBody().toString()
              : objectMapper.writeValueAsString(queue.getMessageBody());
      SendMessageRequest message =
          new SendMessageRequest()
              .withQueueUrl(queue.getUrl())
              .withMessageBody(payload)
              .withMessageAttributes(queue.getAwsSqsNativeMessageAttributes())
              .withMessageGroupId(queue.getMessageGroupId())
              .withMessageDeduplicationId(queue.getMessageDeduplicationId());
      return sqsClient.sendMessage(message);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Error mapping payload to json.");
    } finally {
      if (sqsClient != null) {
        sqsClient.shutdown();
      }
    }
  }
}
