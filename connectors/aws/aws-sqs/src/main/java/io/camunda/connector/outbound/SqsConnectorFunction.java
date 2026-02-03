/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.outbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.AwsUtils;
import io.camunda.connector.aws.CredentialsProviderSupport;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.common.suppliers.AmazonSQSClientSupplier;
import io.camunda.connector.common.suppliers.DefaultAmazonSQSClientSupplier;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.outbound.model.QueueRequestData;
import io.camunda.connector.outbound.model.SqsConnectorRequest;
import io.camunda.connector.outbound.model.SqsConnectorResult;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@OutboundConnector(
    name = "AWS SQS Outbound",
    inputVariables = {"authentication", "configuration", "queue"},
    type = "io.camunda:aws-sqs:1")
@ElementTemplate(
    engineVersion = "^8.3",
    id = "io.camunda.connectors.AWSSQS.v1",
    name = "Amazon SQS Outbound Connector",
    description = "Send messages to Amazon SQS.",
    metadata =
        @ElementTemplate.Metadata(
            keywords = {
              "send message",
              "publish message",
              "send message to queue",
              "publish message to queue"
            }),
    inputDataClass = SqsConnectorRequest.class,
    version = 11,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Queue properties"),
      @ElementTemplate.PropertyGroup(id = "input", label = "Input message data")
    },
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-sqs/?amazonsqs=outbound",
    icon = "icon.svg")
public class SqsConnectorFunction implements OutboundConnectorFunction {

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
    SqsClient sqsClient = createAwsSqsClient(request);
    return new SqsConnectorResult(sendMsgToSqs(sqsClient, request.getQueue()).messageId());
  }

  private SqsClient createAwsSqsClient(SqsConnectorRequest request) {
    var region =
        AwsUtils.extractRegionOrDefault(request.getConfiguration(), request.getQueue().getRegion());
    Optional<String> endpoint =
        Optional.ofNullable(request.getConfiguration()).map(AwsBaseConfiguration::endpoint);
    var credentialsProvider = CredentialsProviderSupport.credentialsProvider(request);
    return endpoint
        .map(ep -> sqsClientSupplier.sqsClient(credentialsProvider, region, ep))
        .orElseGet(() -> sqsClientSupplier.sqsClient(credentialsProvider, region));
  }

  private SendMessageResponse sendMsgToSqs(final SqsClient sqsClient, final QueueRequestData queue) {
    try {
      String payload =
          queue.getMessageBody() instanceof String
              ? queue.getMessageBody().toString()
              : objectMapper.writeValueAsString(queue.getMessageBody());
      SendMessageRequest message =
          SendMessageRequest.builder()
              .queueUrl(queue.getUrl())
              .messageBody(payload)
              .messageAttributes(queue.getAwsSqsNativeMessageAttributes())
              .messageGroupId(queue.getMessageGroupId())
              .messageDeduplicationId(queue.getMessageDeduplicationId())
          .build();
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
