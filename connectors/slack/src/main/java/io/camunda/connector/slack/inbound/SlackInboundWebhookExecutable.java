/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.slack.api.app_backend.events.payload.UrlVerificationPayload;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingResult;
import io.camunda.connector.slack.inbound.model.SlackWebhookProcessingResult;
import io.camunda.connector.slack.inbound.model.SlackWebhookProperties;
import io.camunda.connector.slack.inbound.suppliers.ObjectMapperSupplier;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Map;

@InboundConnector(name = "SLACK_INBOUND", type = "io.camunda:slack-webhook:1")
public class SlackInboundWebhookExecutable implements WebhookConnectorExecutable {

  protected static final String HEADER_SLACK_REQUEST_TIMESTAMP = "x-slack-request-timestamp";
  protected static final String HEADER_SLACK_SIGNATURE = "x-slack-signature";
  protected static final String FIELD_TYPE = "type";
  protected static final String FIELD_CHALLENGE = "challenge";

  private final ObjectMapper objectMapper;
  private SlackWebhookProperties props;

  public SlackInboundWebhookExecutable() {
    this(ObjectMapperSupplier.getMapperInstance());
  }

  public SlackInboundWebhookExecutable(final ObjectMapper mapper) {
    this.objectMapper = mapper;
  }

  @Override
  public WebhookProcessingResult triggerWebhook(WebhookProcessingPayload webhookProcessingPayload)
      throws Exception {
    // Step 1: check message authenticity and integrity
    if (!props
        .signatureVerifier()
        .isValid(
            webhookProcessingPayload.headers().get(HEADER_SLACK_REQUEST_TIMESTAMP),
            new String(webhookProcessingPayload.rawBody(), StandardCharsets.UTF_8),
            webhookProcessingPayload.headers().get(HEADER_SLACK_SIGNATURE),
            ZonedDateTime.now().toInstant().toEpochMilli())) {
      throw new Exception("HMAC signature did not match");
    }

    // Step 2: if URL verification, force return token
    Map bodyAsMap = objectMapper.readValue(webhookProcessingPayload.rawBody(), Map.class);
    if (UrlVerificationPayload.TYPE.equalsIgnoreCase((String) bodyAsMap.get(FIELD_TYPE))) {
      return new SlackWebhookProcessingResult(
          Map.of(FIELD_CHALLENGE, bodyAsMap.get(FIELD_CHALLENGE)),
          Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()),
          200);
    }
    return new SlackWebhookProcessingResult(bodyAsMap, webhookProcessingPayload.headers(), 200);
  }

  @Override
  public void activate(InboundConnectorContext context) throws Exception {
    if (context == null) {
      throw new Exception("Inbound connector context cannot be null");
    }
    props = new SlackWebhookProperties(context.getProperties());
    context.replaceSecrets(props);
  }
}
