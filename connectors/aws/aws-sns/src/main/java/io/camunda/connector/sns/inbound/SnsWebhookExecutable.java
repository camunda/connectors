/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.inbound;

import com.amazonaws.services.sns.message.SnsMessage;
import com.amazonaws.services.sns.message.SnsMessageManager;
import com.amazonaws.services.sns.message.SnsNotification;
import com.amazonaws.services.sns.message.SnsSubscriptionConfirmation;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.generator.dsl.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.ConnectorElementType;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import io.camunda.connector.sns.inbound.model.SnsWebhookConnectorProperties;
import io.camunda.connector.sns.inbound.model.SnsWebhookConnectorProperties.SnsWebhookConnectorPropertiesWrapper;
import io.camunda.connector.sns.inbound.model.SnsWebhookProcessingResult;
import io.camunda.connector.sns.inbound.model.SubscriptionAllowListFlag;
import io.camunda.connector.sns.suppliers.SnsClientSupplier;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@InboundConnector(name = "AWS SNS Inbound", type = "io.camunda:aws-sns-webhook:1")
@ElementTemplate(
    id = "io.camunda.connectors.AWSSNS.inbound.v1",
    name = "SNS HTTPS Connectorr",
    icon = "icon.svg",
    version = 4,
    inputDataClass = SnsWebhookConnectorPropertiesWrapper.class,
    description = "Receive events from AWS SNS",
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-sns/?amazonsns=inbound",
    propertyGroups = {@PropertyGroup(id = "subscription", label = "Subscription Configuration")},
    elementTypes = {
      @ConnectorElementType(
          appliesTo = BpmnType.START_EVENT,
          elementType = BpmnType.START_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.AWSSNS.StartEvent.v1",
          templateNameOverride = "SNS HTTPS Start Event Connector"),
      @ConnectorElementType(
          appliesTo = BpmnType.START_EVENT,
          elementType = BpmnType.MESSAGE_START_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.AWSSNS.MessageStartEvent.v1",
          templateNameOverride = "SNS HTTPS Message Start Event Connector Subscription"),
      @ConnectorElementType(
          appliesTo = {BpmnType.INTERMEDIATE_THROW_EVENT, BpmnType.INTERMEDIATE_CATCH_EVENT},
          elementType = BpmnType.INTERMEDIATE_CATCH_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.AWSSNS.IntermediateCatchEvent.v1",
          templateNameOverride = "SNS HTTPS Intermediate Catch Event Connector"),
      @ConnectorElementType(
          appliesTo = BpmnType.BOUNDARY_EVENT,
          elementType = BpmnType.BOUNDARY_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.AWSSNS.Boundary.v1",
          templateNameOverride = "SNS HTTPS Boundary Event Connector")
    })
public class SnsWebhookExecutable implements WebhookConnectorExecutable {

  protected static final String TOPIC_ARN_HEADER = "x-amz-sns-topic-arn";

  private final ObjectMapper objectMapper;
  private final SnsClientSupplier snsClientSupplier;

  private SnsWebhookConnectorProperties props;

  public SnsWebhookExecutable() {
    this(ObjectMapperSupplier.getMapperInstance(), new SnsClientSupplier());
  }

  public SnsWebhookExecutable(
      final ObjectMapper objectMapper, final SnsClientSupplier snsClientSupplier) {
    this.objectMapper = objectMapper;
    this.snsClientSupplier = snsClientSupplier;
  }

  @Override
  public WebhookResult triggerWebhook(WebhookProcessingPayload webhookProcessingPayload)
      throws Exception {
    checkMessageAllowListed(webhookProcessingPayload);
    Map bodyAsMap = objectMapper.readValue(webhookProcessingPayload.rawBody(), Map.class);
    String region = extractRegionFromTopicArnHeader(webhookProcessingPayload.headers());
    SnsMessageManager msgManager = snsClientSupplier.messageManager(region);
    SnsMessage msg =
        msgManager.parseMessage(new ByteArrayInputStream(webhookProcessingPayload.rawBody()));
    if (msg instanceof SnsSubscriptionConfirmation ssc) {
      return tryConfirmSubscription(webhookProcessingPayload, bodyAsMap, ssc);
    } else if (msg instanceof SnsNotification) {
      return handleNotification(webhookProcessingPayload, bodyAsMap);
    } else {
      throw new IOException("Operation not supported: " + msg.getClass().getName());
    }
  }

  private SnsWebhookProcessingResult tryConfirmSubscription(
      WebhookProcessingPayload webhookProcessingPayload,
      Map bodyAsMap,
      SnsSubscriptionConfirmation confirmation) {
    // If request was tampered, or insufficient ACL, confirmation will throw an exception
    confirmation.confirmSubscription();

    return new SnsWebhookProcessingResult(
        new MappedHttpRequest(
            bodyAsMap, webhookProcessingPayload.headers(), webhookProcessingPayload.params()),
        Map.of("snsEventType", "Subscription"));
  }

  private SnsWebhookProcessingResult handleNotification(
      WebhookProcessingPayload webhookProcessingPayload, Map bodyAsMap) {
    return new SnsWebhookProcessingResult(
        new MappedHttpRequest(
            bodyAsMap, webhookProcessingPayload.headers(), webhookProcessingPayload.params()),
        Map.of("snsEventType", "Notification"));
  }

  private void checkMessageAllowListed(WebhookProcessingPayload webhookProcessingPayload)
      throws Exception {
    if (SubscriptionAllowListFlag.specific.equals(props.securitySubscriptionAllowedFor())
        && !props
            .topicsAllowListParsed()
            .contains(webhookProcessingPayload.headers().get(TOPIC_ARN_HEADER))) {
      throw new Exception(
          "Request didn't match allow list. Allow list: "
              + props.topicsAllowListParsed()
              + ". Request coming from "
              + webhookProcessingPayload.headers().get(TOPIC_ARN_HEADER));
    }
  }

  @Override
  public void activate(InboundConnectorContext context) throws Exception {
    if (context == null) {
      throw new Exception("Inbound connector context cannot be null");
    }
    props =
        new SnsWebhookConnectorProperties(
            context.bindProperties(SnsWebhookConnectorPropertiesWrapper.class));
  }

  // Topic ARN header has a format arn:aws:sns:region-xyz:000011112222:TopicName, and
  // we need to extract region from it, which is at index 3, given string is separated by ':'
  private String extractRegionFromTopicArnHeader(final Map<String, String> headers)
      throws Exception {
    final var topicArn =
        Optional.ofNullable(headers.get(TOPIC_ARN_HEADER))
            .orElseThrow(
                () -> new Exception("SNS request did not contain header: " + TOPIC_ARN_HEADER));
    return topicArn.split(":")[3];
  }
}
