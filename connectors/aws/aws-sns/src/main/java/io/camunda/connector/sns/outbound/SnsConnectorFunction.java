/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.outbound;

import static io.camunda.connector.aws.AwsUtils.extractRegionOrDefault;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.CredentialsProviderSupport;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.sns.outbound.model.SnsConnectorRequest;
import io.camunda.connector.sns.outbound.model.SnsConnectorResult;
import io.camunda.connector.sns.suppliers.SnsClientSupplier;
import java.util.Optional;
import org.apache.commons.text.StringEscapeUtils;

@OutboundConnector(
    name = "AWS SNS Outbound",
    inputVariables = {"authentication", "configuration", "topic"},
    type = "io.camunda:aws-sns:1")
@ElementTemplate(
    id = "io.camunda.connectors.AWSSNS.v1",
    name = "Amazon SNS Outbound connector",
    description = "Send message to topic",
    inputDataClass = SnsConnectorRequest.class,
    version = 7,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Topic properties"),
      @ElementTemplate.PropertyGroup(id = "input", label = "Input message data")
    },
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-sns/?amazonsns=outbound",
    icon = "icon.svg")
public class SnsConnectorFunction implements OutboundConnectorFunction {

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
  public Object execute(final OutboundConnectorContext context) {
    final var request = context.bindVariables(SnsConnectorRequest.class);
    AmazonSNS snsClient = createSnsClient(request);
    return new SnsConnectorResult(sendMsgToSns(snsClient, request).getMessageId());
  }

  private AmazonSNS createSnsClient(final SnsConnectorRequest request) {
    Optional<String> endpoint =
        Optional.ofNullable(request.getConfiguration()).map(AwsBaseConfiguration::endpoint);
    var credentialsProvider = CredentialsProviderSupport.credentialsProvider(request);
    var region = extractRegionOrDefault(request.getConfiguration(), request.getTopic().getRegion());

    return endpoint
        .map(ep -> snsClientSupplier.getSnsClient(credentialsProvider, region, ep))
        .orElseGet(() -> snsClientSupplier.getSnsClient(credentialsProvider, region));
  }

  private PublishResult sendMsgToSns(final AmazonSNS snsClient, SnsConnectorRequest request) {
    try {
      String topicMessage =
          request.getTopic().getMessage() instanceof String
              ? StringEscapeUtils.unescapeJson(request.getTopic().getMessage().toString())
              : objectMapper.writeValueAsString(request.getTopic().getMessage());
      PublishRequest message =
          new PublishRequest()
              .withTopicArn(request.getTopic().getTopicArn())
              .withMessage(topicMessage)
              .withMessageGroupId(request.getTopic().getMessageGroupId())
              .withMessageDeduplicationId(request.getTopic().getMessageDeduplicationId())
              .withMessageAttributes(request.getTopic().getAwsSnsNativeMessageAttributes())
              .withSubject(request.getTopic().getSubject());
      return snsClient.publish(message);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Error mapping message to json.");
    } finally {
      if (snsClient != null) {
        snsClient.shutdown();
      }
    }
  }
}
