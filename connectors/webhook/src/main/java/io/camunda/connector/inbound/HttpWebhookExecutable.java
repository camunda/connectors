/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.enabled;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorException;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookHttpResponse;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.api.inbound.webhook.WebhookResultContext;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.ConnectorElementType;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Failure;
import io.camunda.connector.inbound.authorization.WebhookAuthorizationHandler;
import io.camunda.connector.inbound.model.HMACScope;
import io.camunda.connector.inbound.model.WebhookConnectorProperties;
import io.camunda.connector.inbound.model.WebhookConnectorProperties.WebhookConnectorPropertiesWrapper;
import io.camunda.connector.inbound.model.WebhookProcessingResultImpl;
import io.camunda.connector.inbound.signature.HMACVerifier;
import io.camunda.connector.inbound.utils.HttpMethods;
import io.camunda.connector.inbound.utils.HttpWebhookUtil;
import jakarta.annotation.Nullable;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "Webhook", type = "io.camunda:webhook:1")
@ElementTemplate(
    engineVersion = "^8.3",
    id = "io.camunda.connectors.webhook",
    name = "Webhook Connector",
    icon = "icon.svg",
    version = 15,
    inputDataClass = WebhookConnectorPropertiesWrapper.class,
    description = "Configure webhook to receive callbacks",
    documentationRef = "https://docs.camunda.io/docs/components/connectors/protocol/http-webhook/",
    defaultResultExpression =
        "{\n"
            + "  myRequestBody: request.body\n"
            + "  // Use FEEL to extract values, e.g.,:\n"
            + "  // myMessage: request.body.message\n"
            + "}",
    propertyGroups = {
      @PropertyGroup(id = "endpoint", label = "Webhook configuration"),
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "authorization", label = "Authorization"),
      @PropertyGroup(id = "webhookResponse", label = "Webhook response")
    },
    elementTypes = {
      @ConnectorElementType(
          appliesTo = BpmnType.START_EVENT,
          elementType = BpmnType.MESSAGE_START_EVENT,
          templateIdOverride = "io.camunda.connectors.webhook.WebhookConnectorStartMessage.v1",
          templateNameOverride = "Webhook Message Start Event Connector"),
      @ConnectorElementType(
          appliesTo = BpmnType.START_EVENT,
          elementType = BpmnType.START_EVENT,
          templateIdOverride = "io.camunda.connectors.webhook.WebhookConnector.v1",
          templateNameOverride = "Webhook Start Event Connector"),
      @ConnectorElementType(
          appliesTo = {BpmnType.INTERMEDIATE_THROW_EVENT, BpmnType.INTERMEDIATE_CATCH_EVENT},
          elementType = BpmnType.INTERMEDIATE_CATCH_EVENT,
          templateIdOverride = "io.camunda.connectors.webhook.WebhookConnectorIntermediate.v1",
          templateNameOverride = "Webhook Intermediate Event Connector"),
      @ConnectorElementType(
          appliesTo = BpmnType.BOUNDARY_EVENT,
          elementType = BpmnType.BOUNDARY_EVENT,
          templateIdOverride = "io.camunda.connectors.webhook.WebhookConnectorBoundary.v1",
          templateNameOverride = "Webhook Boundary Event Connector"),
      @ConnectorElementType(
          appliesTo = BpmnType.RECEIVE_TASK,
          elementType = BpmnType.RECEIVE_TASK,
          templateIdOverride = "io.camunda.connectors.webhook.WebhookConnectorReceive.v1",
          templateNameOverride = "Webhook Receive Task Connector")
    })
public class HttpWebhookExecutable implements WebhookConnectorExecutable {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpWebhookExecutable.class);

  private WebhookConnectorProperties props;
  private WebhookAuthorizationHandler<?> authChecker;
  private InboundConnectorContext context;
  private Function<WebhookResultContext, WebhookHttpResponse> responseExpression;
  private HMACVerifier hmacVerifier;

  @Override
  public void activate(InboundConnectorContext context) {
    this.context = context;
    var wrappedProps = context.bindProperties(WebhookConnectorPropertiesWrapper.class);
    props = new WebhookConnectorProperties(wrappedProps);
    rejectInvalidHmacTimestampHeader(props);
    rejectInvalidHmacTolerance(props);
    rejectUnsupportedHmacScopeCombination(props);
    authChecker = WebhookAuthorizationHandler.getHandlerForAuth(props.auth());
    responseExpression = mapResponseExpression();
    hmacVerifier =
        new HMACVerifier(
            props.hmacScopes(),
            props.hmacHeader(),
            props.hmacSecret(),
            props.hmacAlgorithm(),
            props.hmacTimestampHeader(),
            props.hmacTolerance());
    context.reportHealth(Health.up());
  }

  /**
   * Fails webhook deployment (activation) when HMAC authentication is enabled with the {@code
   * timestamp} scope but its header is missing or matches the signature header. Neither
   * configuration can pass verification, so they are rejected at deploy time rather than on every
   * request.
   */
  private static void rejectInvalidHmacTimestampHeader(WebhookConnectorProperties props) {
    boolean timestampScopeSelected =
        Arrays.asList(props.hmacScopes()).contains(HMACScope.TIMESTAMP);
    if (!enabled.equals(props.shouldValidateHmac()) || !timestampScopeSelected) {
      return;
    }
    String timestampHeader = props.hmacTimestampHeader();
    if (timestampHeader == null || timestampHeader.isBlank()) {
      throw new ConnectorInputException(
          "HMAC scope 'timestamp' is selected but 'hmacTimestampHeader' is not configured. "
              + "Set 'hmacTimestampHeader' to the name of the header carrying the request timestamp.");
    }
    if (timestampHeader.equalsIgnoreCase(props.hmacHeader())) {
      throw new ConnectorInputException(
          "HMAC property 'hmacTimestampHeader' must be different from 'hmacHeader'.");
    }
  }

  /**
   * Fails webhook deployment (activation) when the {@code timestamp} scope is selected and {@code
   * hmacTolerance} is not a positive ISO-8601 duration. Gated on the {@code timestamp} scope,
   * matching {@link #rejectInvalidHmacTimestampHeader}: the property is hidden and irrelevant
   * otherwise, so a leftover invalid value from a previous configuration (e.g. after switching
   * scopes back to {@code body}) must not block activation.
   *
   * <p>The {@code @Pattern} constraint on the property is never evaluated at runtime — {@code
   * bindProperties} validates {@link WebhookConnectorPropertiesWrapper}, whose {@code inbound}
   * component isn't annotated {@code @Valid}, so Jakarta Validation doesn't cascade into the nested
   * {@link WebhookConnectorProperties} record. Enforced here explicitly instead of adding that
   * cascade, to avoid retroactively activating validation for the record's other, pre-existing
   * constraints as an unrelated side effect.
   */
  private static void rejectInvalidHmacTolerance(WebhookConnectorProperties props) {
    boolean timestampScopeSelected =
        Arrays.asList(props.hmacScopes()).contains(HMACScope.TIMESTAMP);
    if (!enabled.equals(props.shouldValidateHmac()) || !timestampScopeSelected) {
      return;
    }
    String tolerance = props.hmacTolerance();
    if (tolerance == null || tolerance.isBlank()) {
      return;
    }
    Duration parsed;
    try {
      parsed = Duration.parse(tolerance);
    } catch (DateTimeParseException e) {
      throw new ConnectorInputException(
          "HMAC property 'hmacTolerance' must be an ISO-8601 duration, but was " + tolerance);
    }
    if (!parsed.isPositive()) {
      throw new ConnectorInputException(
          "HMAC property 'hmacTolerance' must be a positive duration, but was " + tolerance);
    }
    if (parsed.getNano() != 0) {
      throw new ConnectorInputException(
          "HMAC property 'hmacTolerance' must be a whole-second duration, but was " + tolerance);
    }
  }

  /**
   * Fails webhook deployment (activation) when HMAC is enabled and the configured {@code
   * hmacScopes} — after stripping {@code timestamp}, which isn't itself signable — reduce to a
   * combination {@link HMACVerifier} doesn't support (e.g. {@code [timestamp, url]} strips down to
   * {@code [url]} alone). Gated on HMAC being enabled: {@link HMACVerifier} is constructed
   * unconditionally below regardless of {@code shouldValidateHmac}, so this check must be too, or a
   * deployment with HMAC disabled could fail activation over a scope combination that will never
   * actually be evaluated.
   */
  private static void rejectUnsupportedHmacScopeCombination(WebhookConnectorProperties props) {
    if (enabled.equals(props.shouldValidateHmac())) {
      HMACVerifier.rejectUnsupportedScopeCombination(props.hmacScopes());
    }
  }

  @Override
  public WebhookResult triggerWebhook(WebhookProcessingPayload payload) {
    LOGGER.trace("Triggered webhook with context {} and payload {}", props.context(), payload);

    validateHttpMethod(payload);
    verifyHmac(payload);

    var authResult = authChecker.checkAuthorization(payload);
    if (authResult instanceof Failure failureResult) {
      throw failureResult.toException();
    }

    var mappedRequest = mapRequest(payload);
    return new WebhookProcessingResultImpl(mappedRequest, responseExpression, null);
  }

  private void validateHttpMethod(WebhookProcessingPayload payload) {
    if (!HttpMethods.any.name().equalsIgnoreCase(props.method())
        && !payload.method().equalsIgnoreCase(props.method())) {
      throw new WebhookConnectorException(405, "Method " + payload.method() + " not supported");
    }
  }

  private static MappedHttpRequest mapRequest(WebhookProcessingPayload payload) {
    return new MappedHttpRequest(
        HttpWebhookUtil.transformRawBodyToObject(
            payload.rawBody(), HttpWebhookUtil.extractContentType(payload.headers())),
        payload.headers(),
        payload.params());
  }

  @Nullable
  private Function<WebhookResultContext, WebhookHttpResponse> mapResponseExpression() {
    Function<WebhookResultContext, WebhookHttpResponse> responseExpression = null;
    if (props.responseExpression() != null) {
      responseExpression = props.responseExpression();
    } else if (props.responseBodyExpression() != null) {
      // To be backwards compatible we need to wrap the responseBodyExpression into a
      // responseExpression
      // and only use the body in the final response
      responseExpression =
          (context) -> {
            Object responseBody = props.responseBodyExpression().apply(context);
            return WebhookHttpResponse.ok(responseBody);
          };
    }
    return responseExpression;
  }

  private void verifyHmac(WebhookProcessingPayload payload) {
    if (enabled.equals(props.shouldValidateHmac())) {
      hmacVerifier.verifySignature(payload);
    }
  }

  @Override
  public WebhookHttpResponse verify(WebhookProcessingPayload payload) {
    WebhookHttpResponse result = null;
    if (props.verificationExpression() != null) {
      result =
          props
              .verificationExpression()
              .apply(
                  Map.of(
                      "request",
                      Map.of(
                          "body",
                          HttpWebhookUtil.transformRawBodyToObject(
                              payload.rawBody(),
                              HttpWebhookUtil.extractContentType(payload.headers())),
                          "headers",
                          payload.headers(),
                          "params",
                          payload.params())));
    }
    return result;
  }

  @Override
  public void deactivate() {
    LOGGER.debug("Deactivating webhook connector");
    context.reportHealth(Health.down());
  }
}
