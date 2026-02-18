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
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookHttpResponse;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.ConnectorElementType;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.slack.inbound.model.SlackWebhookProcessingResult;
import io.camunda.connector.slack.inbound.model.SlackWebhookProperties;
import io.camunda.connector.slack.inbound.model.SlackWebhookProperties.SlackConnectorPropertiesWrapper;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "Slack Inbound", type = "io.camunda:slack-webhook:1")
@ElementTemplate(
    engineVersion = "^8.3",
    id = "io.camunda.connectors.inbound.Slack.v1",
    name = "Slack Webhook Boundary Event Connector",
    icon = "icon.svg",
    version = 7,
    inputDataClass = SlackConnectorPropertiesWrapper.class,
    description = "Receive events from Slack",
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/slack/?slack=inbound",
    propertyGroups = {@PropertyGroup(id = "endpoint", label = "Webhook configuration")},
    elementTypes = {
      @ConnectorElementType(
          appliesTo = BpmnType.START_EVENT,
          elementType = BpmnType.MESSAGE_START_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.Slack.MessageStartEvent.v1",
          templateNameOverride = "Slack Webhook Message Start Event Connector"),
      @ConnectorElementType(
          appliesTo = {BpmnType.INTERMEDIATE_THROW_EVENT, BpmnType.INTERMEDIATE_CATCH_EVENT},
          elementType = BpmnType.INTERMEDIATE_CATCH_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.Slack.IntermediateCatchEvent.v1",
          templateNameOverride = "Slack Webhook Intermediate Catch Event Connector"),
      @ConnectorElementType(
          appliesTo = BpmnType.BOUNDARY_EVENT,
          elementType = BpmnType.BOUNDARY_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.Slack.BoundaryEvent.v1",
          templateNameOverride = "Slack Webhook Boundary Event Connector"),
      @ConnectorElementType(
          appliesTo = BpmnType.RECEIVE_TASK,
          elementType = BpmnType.RECEIVE_TASK,
          templateIdOverride = "io.camunda.connectors.inbound.Slack.ReceiveTask.v1",
          templateNameOverride = "Slack Webhook Receive Task Connector")
    })
public class SlackInboundWebhookExecutable implements WebhookConnectorExecutable {
  private static final Logger LOGGER = LoggerFactory.getLogger(SlackInboundWebhookExecutable.class);

  protected static final String HEADER_SLACK_REQUEST_TIMESTAMP = "x-slack-request-timestamp";
  protected static final String HEADER_SLACK_SIGNATURE = "x-slack-signature";

  protected static final String FORM_VALUE_COMMAND = "command";
  protected static final String FORM_VALUE_PAYLOAD = "payload";
  protected static final String COMMAND_RESPONSE_TYPE_KEY = "response_type";
  protected static final String COMMAND_RESPONSE_TYPE_DEFAULT_VALUE = "ephemeral";
  protected static final String COMMAND_RESPONSE_TEXT_KEY = "text";
  protected static final String COMMAND_RESPONSE_TEXT_DEFAULT_VALUE = "Command executed";

  private final ObjectMapper objectMapper;
  private SlackWebhookProperties props;
  private InboundConnectorContext context;

  public SlackInboundWebhookExecutable() {
    this(ConnectorsObjectMapperSupplier.getCopy());
  }

  public SlackInboundWebhookExecutable(final ObjectMapper mapper) {
    this.objectMapper = mapper;
  }

  @Override
  public WebhookResult triggerWebhook(WebhookProcessingPayload webhookProcessingPayload) {
    LOGGER.debug(
        "Triggered Slack webhook with method: {} and URL: {}",
        webhookProcessingPayload.method(),
        webhookProcessingPayload.requestURL());
    context.log(
        activity ->
            activity
                .withSeverity(Severity.INFO)
                .withTag(webhookProcessingPayload.method())
                .withMessage("URL: " + webhookProcessingPayload.requestURL()));
    verifySlackRequestAuthentic(webhookProcessingPayload);

    Map bodyAsMap =
        bodyAsMap(webhookProcessingPayload.headers(), webhookProcessingPayload.rawBody());

    // Command detected
    if (bodyAsMap.containsKey(FORM_VALUE_COMMAND)) {
      return new SlackWebhookProcessingResult(
          new MappedHttpRequest(
              bodyAsMap, Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()), null),
          bodyAsMap,
          new WebhookHttpResponse(defaultCommandResponse(), null, 200, List.of()));
    }

    // Interactive message detected
    if (bodyAsMap.size() == 1 && bodyAsMap.containsKey(FORM_VALUE_PAYLOAD)) {
      // Payload is a JSON string, so we need to parse it
      try {
        bodyAsMap = objectMapper.readValue((String) bodyAsMap.get(FORM_VALUE_PAYLOAD), Map.class);
      } catch (Exception e) {
        context.log(
            activity ->
                activity
                    .withSeverity(Severity.ERROR)
                    .withTag("JSON Parsing")
                    .withMessage("Failed to parse 'payload' as JSON: " + e.getMessage()));
      }
      return new SlackWebhookProcessingResult(
          new MappedHttpRequest(
              bodyAsMap, Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()), null),
          null,
          new WebhookHttpResponse(defaultCommandResponse(), null, 200, List.of()));
    }

    // Other requests, e.g. events
    return new SlackWebhookProcessingResult(
        new MappedHttpRequest(bodyAsMap, webhookProcessingPayload.headers(), null),
        null,
        new WebhookHttpResponse(bodyAsMap, null, 200, List.of()));
  }

  @Override
  public void activate(InboundConnectorContext context) {
    this.context = context;
    var wrapperProps = context.bindProperties(SlackConnectorPropertiesWrapper.class);
    props = new SlackWebhookProperties(wrapperProps);
    context.reportHealth(Health.up());
  }

  @Override
  public WebhookHttpResponse verify(WebhookProcessingPayload payload) {
    verifySlackRequestAuthentic(payload);
    return Optional.ofNullable(props.verificationExpression())
        .orElse(stringObjectMap -> null)
        .apply(
            Map.of(
                "body",
                bodyAsMap(payload.headers(), payload.rawBody()),
                "headers",
                payload.headers(),
                "params",
                payload.params()));
  }

  private Map bodyAsMap(Map<String, String> headers, byte[] rawBody) {
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
      try {
        return objectMapper.readValue(rawBody, Map.class);
      } catch (IOException e) {
        context.log(
            activity ->
                activity
                    .withSeverity(Severity.ERROR)
                    .withTag("JSON Parsing")
                    .withMessage(
                        "Failed to parse JSON from raw body due to an IOException: "
                            + e.getMessage()));
        throw new RuntimeException(e);
      }
    }
  }

  private void verifySlackRequestAuthentic(WebhookProcessingPayload webhookProcessingPayload) {
    if (!props
        .signatureVerifier()
        .isValid(
            webhookProcessingPayload.headers().get(HEADER_SLACK_REQUEST_TIMESTAMP),
            new String(webhookProcessingPayload.rawBody(), StandardCharsets.UTF_8),
            webhookProcessingPayload.headers().get(HEADER_SLACK_SIGNATURE),
            ZonedDateTime.now().toInstant().toEpochMilli())) {
      context.log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withTag(webhookProcessingPayload.method())
                  .withMessage("HMAC signature did not match"));
      throw new RuntimeException("HMAC signature did not match");
    }
  }

  private Map<String, Object> defaultCommandResponse() {
    return Map.of(
        COMMAND_RESPONSE_TYPE_KEY,
        COMMAND_RESPONSE_TYPE_DEFAULT_VALUE,
        COMMAND_RESPONSE_TEXT_KEY,
        COMMAND_RESPONSE_TEXT_DEFAULT_VALUE);
  }

  @Override
  public void deactivate() {
    context.reportHealth(Health.down());
    LOGGER.info("Deactivated Slack Inbound Webhook.");
  }
}
