/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.outbound;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.AwsUtils;
import io.camunda.connector.aws.AwsUtils;
import io.camunda.connector.aws.CredentialsProviderSupport;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.sns.outbound.model.SnsConnectorRequest;
import io.camunda.connector.sns.outbound.model.SnsConnectorResult;
import io.camunda.connector.sns.outbound.model.TopicRequestData;
import io.camunda.connector.sns.suppliers.SnsClientSupplier;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "AWSSNS",
    inputVariables = {"authentication", "topic"},
    type = "io.camunda:aws-sns:1")
public class SnsConnectorFunction implements OutboundConnectorFunction {
  private static final Logger LOGGER = LoggerFactory.getLogger(SnsConnectorFunction.class);

  private final SnsClientSupplier snsClientSupplier;
  private final ObjectMapper objectMapper;

  public SnsConnectorFunction() {
    this(new SnsClientSupplier(), ObjectMapperSupplier.getMapperInstance());
  }

  public SnsConnectorFunction(
      final SnsClientSupplier snsClientSupplier, final ObjectMapper objectMapper) {
    this.snsClientSupplier = snsClientSupplier;
    this.objectMapper = objectMapper;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) throws JsonProcessingException {
    final var request = context.bindVariables(SnsConnectorRequest.class);
    var region =
            AwsUtils.extractRegionOrDefault(request.getConfiguration(), request.getTopic().getRegion());
    AWSCredentialsProvider provider = CredentialsProviderSupport.credentialsProvider(request);
    AmazonSNS snsClient = snsClientSupplier.getSnsClient(provider, region);
    return new SnsConnectorResult(sendMsgToSns(snsClient, request.getTopic()).getMessageId());
  }

  private PublishResult sendMsgToSns(final AmazonSNS snsClient, final TopicRequestData topic)
      throws JsonProcessingException {
    try {
      String topicMessage =
          topic.getMessage() instanceof String
              ? StringEscapeUtils.unescapeJson(topic.getMessage().toString())
              : objectMapper.writeValueAsString(topic.getMessage());
      PublishRequest message =
          new PublishRequest()
              .withTopicArn(topic.getTopicArn())
              .withMessage(topicMessage)
              .withMessageAttributes(topic.getAwsSnsNativeMessageAttributes())
              .withSubject(topic.getSubject());
      return snsClient.publish(message);
    } finally {
      if (snsClient != null) {
        snsClient.shutdown();
      }
    }
  }
}
