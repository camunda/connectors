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
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingResult;
import io.camunda.connector.slack.inbound.model.SlackWebhookProcessingResult;
import io.camunda.connector.slack.inbound.model.SlackWebhookProperties;
import io.camunda.connector.slack.inbound.suppliers.ObjectMapperSupplier;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@InboundConnector(name = "SLACK_INBOUND", type = "io.camunda:slack-webhook:1")
public class SlackInboundWebhookExecutable implements WebhookConnectorExecutable {

  protected static final String HEADER_SLACK_REQUEST_TIMESTAMP = "x-slack-request-timestamp";
  protected static final String HEADER_SLACK_SIGNATURE = "x-slack-signature";

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
    if (!props
        .signatureVerifier()
        .isValid(
            webhookProcessingPayload.headers().get(HEADER_SLACK_REQUEST_TIMESTAMP),
            new String(webhookProcessingPayload.rawBody(), StandardCharsets.UTF_8),
            webhookProcessingPayload.headers().get(HEADER_SLACK_SIGNATURE),
            ZonedDateTime.now().toInstant().toEpochMilli())) {
      throw new Exception("HMAC signature did not match");
    }

    Map bodyAsMap =
        bodyAsMap(webhookProcessingPayload.headers(), webhookProcessingPayload.rawBody());

    // Command detected
    if (bodyAsMap.containsKey("command")) {
      // FIXME: hard coded values will be removed. For SaaS testing only
      return new SlackWebhookProcessingResult(
          Map.of("response_type", "in_channel", "text", "OK"),
          webhookProcessingPayload.headers(),
          bodyAsMap,
          200);
    }
    return new SlackWebhookProcessingResult(
        bodyAsMap, webhookProcessingPayload.headers(), Map.of(), 200);
  }

  @Override
  public void activate(InboundConnectorContext context) throws Exception {
    if (context == null) {
      throw new Exception("Inbound connector context cannot be null");
    }
    props = new SlackWebhookProperties(context.getProperties());
    context.replaceSecrets(props);
  }

  private Map bodyAsMap(Map<String, String> headers, byte[] rawBody) throws IOException {
    var caseInsensitiveMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    caseInsensitiveMap.putAll(headers);
    String contentTypeHeader =
        caseInsensitiveMap.getOrDefault(HttpHeaders.CONTENT_TYPE, "").toString();
    if (MediaType.FORM_DATA.toString().equalsIgnoreCase(contentTypeHeader)) {
      String bodyAsString =
          URLDecoder.decode(new String(rawBody, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
      return Arrays.stream(bodyAsString.split("&"))
          .filter(Objects::nonNull)
          .map(param -> param.split("="))
          .collect(Collectors.toMap(param -> param[0], param -> param.length == 1 ? "" : param[1]));
    } else {
      // Do our best to parse to JSON (throws exception otherwise)
      return objectMapper.readValue(rawBody, Map.class);
    }
  }
}
