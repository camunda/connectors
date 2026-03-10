/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.Severity;
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
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final Logger LOGGER = LoggerFactory.getLogger(SnsWebhookExecutable.class);

  protected static final String TOPIC_ARN_HEADER = "x-amz-sns-topic-arn";

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  private InboundConnectorContext context;
  private SnsWebhookConnectorProperties props;

  public SnsWebhookExecutable() {
    this(ObjectMapperSupplier.getMapperInstance(), HttpClient.newHttpClient());
  }

  public SnsWebhookExecutable(final ObjectMapper objectMapper, final HttpClient httpClient) {
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  @Override
  public WebhookResult triggerWebhook(WebhookProcessingPayload webhookProcessingPayload)
      throws Exception {
    LOGGER.trace(
        "Triggered webhook with context {} and payload {}",
        props.context(),
        webhookProcessingPayload);

    context.log(
        Activity.level(Severity.INFO)
            .tag(webhookProcessingPayload.method())
            .message("Url: " + webhookProcessingPayload.requestURL()));

    checkMessageAllowListed(webhookProcessingPayload);
    Map bodyAsMap = objectMapper.readValue(webhookProcessingPayload.rawBody(), Map.class);
    String messageType =
        Optional.ofNullable(webhookProcessingPayload.headers().get("x-amz-sns-message-type"))
            .orElseGet(
                () -> Optional.ofNullable(bodyAsMap.get("Type")).map(Object::toString).orElse(""));

    if ("SubscriptionConfirmation".equalsIgnoreCase(messageType)) {
      return tryConfirmSubscription(webhookProcessingPayload, bodyAsMap);
    } else if ("Notification".equalsIgnoreCase(messageType)) {
      return handleNotification(webhookProcessingPayload, bodyAsMap);
    } else {
      String errorMessage = "Operation not supported: " + messageType;
      LOGGER.warn(errorMessage);
      context.log(
          Activity.level(Severity.ERROR)
              .tag(webhookProcessingPayload.method())
              .message("Url: " + webhookProcessingPayload.requestURL() + ". " + errorMessage));
      throw new IOException(errorMessage);
    }
  }

  private SnsWebhookProcessingResult tryConfirmSubscription(
      WebhookProcessingPayload webhookProcessingPayload, Map bodyAsMap) throws Exception {
    confirmSubscription(bodyAsMap);

    return new SnsWebhookProcessingResult(
        new MappedHttpRequest(
            bodyAsMap, webhookProcessingPayload.headers(), webhookProcessingPayload.params()),
        Map.of("snsEventType", "Subscription"));
  }

  private void confirmSubscription(Map bodyAsMap) throws Exception {
    Object subscribeUrl = bodyAsMap.get("SubscribeURL");
    if (subscribeUrl == null) {
      throw new Exception("SNS SubscriptionConfirmation did not contain SubscribeURL");
    }
    HttpRequest request =
        HttpRequest.newBuilder().uri(URI.create(subscribeUrl.toString())).GET().build();
    HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    if (response.statusCode() >= 300) {
      throw new IOException(
          "Subscription confirmation request failed with status " + response.statusCode());
    }
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
    this.context = context;
    props =
        new SnsWebhookConnectorProperties(
            context.bindProperties(SnsWebhookConnectorPropertiesWrapper.class));
    context.reportHealth(Health.up());
  }

  @Override
  public void deactivate() throws Exception {
    context.reportHealth(Health.down());
  }
}
