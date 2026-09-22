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
import io.camunda.connector.api.inbound.ProcessElement;
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
import io.camunda.connector.inbound.model.DynamicWebhookProperties.DynamicWebhookPropertiesWrapper;
import io.camunda.connector.inbound.model.HMACScope;
import io.camunda.connector.inbound.model.WebhookConnectorProperties;
import io.camunda.connector.inbound.model.WebhookConnectorProperties.WebhookConnectorPropertiesWrapper;
import io.camunda.connector.inbound.model.WebhookOutputExample;
import io.camunda.connector.inbound.model.WebhookProcessingResultImpl;
import io.camunda.connector.inbound.signature.HMACVerifier;
import io.camunda.connector.inbound.utils.HttpMethods;
import io.camunda.connector.inbound.utils.HttpWebhookUtil;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(
    name = "Webhook",
    type = "io.camunda:webhook:1",
    // Deduplicate on the connector-scoped properties only; the element-scoped response expressions
    // (DynamicWebhookProperties) are intentionally excluded so elements differing only in their
    // response still deduplicate into a single executable.
    deduplicationClasses = {WebhookConnectorPropertiesWrapper.class})
@ElementTemplate(
    engineVersion = "^8.3",
    id = "io.camunda.connectors.webhook",
    name = "Webhook Connector",
    icon = "icon.svg",
    version = 16,
    inputDataClass = {
      WebhookConnectorPropertiesWrapper.class,
      DynamicWebhookPropertiesWrapper.class
    },
    description = "Configure webhook to receive callbacks",
    keywords = {
      "receive webhook",
      "HTTP trigger",
      "event received",
      "inbound event",
      "webhook trigger",
      "HTTP callback",
      "listen for event"
    },
    documentationRef = "https://docs.camunda.io/docs/components/connectors/protocol/http-webhook/",
    outputDataClass = WebhookOutputExample.class,
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

  /**
   * Raw (flattened) key of the legacy, deprecated response property, superseded by {@code
   * responseExpression}. Its presence on any deployed element is rejected at activation time (see
   * {@link #rejectDeprecatedResponseBodyExpression}).
   */
  private static final String LEGACY_RESPONSE_BODY_EXPRESSION_PROPERTY =
      "inbound.responseBodyExpression";

  private WebhookConnectorProperties props;
  private WebhookAuthorizationHandler<?> authChecker;
  private InboundConnectorContext context;
  private HMACVerifier hmacVerifier;

  @Override
  public void activate(InboundConnectorContext context) {
    this.context = context;
    rejectDeprecatedResponseBodyExpression(context);
    var wrappedProps = context.bindProperties(WebhookConnectorPropertiesWrapper.class);
    props = new WebhookConnectorProperties(wrappedProps);
    rejectMissingHmacTimestampHeader(props);
    rejectInvalidHmacTolerance(props);
    rejectUnsupportedHmacScopeCombination(props);
    authChecker = WebhookAuthorizationHandler.getHandlerForAuth(props.auth());
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
   * Fails webhook deployment (activation) when the legacy, deprecated {@code
   * responseBodyExpression} property is present on <em>any</em> element. It was superseded by
   * {@code responseExpression} — which can return a full HTTP response — and removed from element
   * templates long ago, but was still silently honored at runtime. Deployments must migrate to
   * {@code responseExpression}.
   *
   * <p>Every element is inspected, not just the representative one exposed by {@link
   * InboundConnectorContext#getProperties()}: element-scoped response properties are excluded from
   * deduplication, so elements sharing a single executable may each carry a different (or legacy)
   * response expression, and the runtime honors the one from the element that actually matched.
   *
   * <p>Throwing here causes the runtime to report the connector as {@code DOWN} with this message,
   * which surfaces in the Manage &amp; Run UI. See
   * https://github.com/camunda/connectors/issues/7468.
   */
  private static void rejectDeprecatedResponseBodyExpression(InboundConnectorContext context) {
    var definition = context.getDefinition();
    if (definition == null) {
      return;
    }
    boolean anyElementUsesLegacyProperty =
        definition.elements().stream()
            .map(ProcessElement::properties)
            .filter(Objects::nonNull)
            .map(properties -> properties.get(LEGACY_RESPONSE_BODY_EXPRESSION_PROPERTY))
            .anyMatch(expression -> expression != null && !expression.isBlank());
    if (anyElementUsesLegacyProperty) {
      throw new ConnectorInputException(
          "The webhook property 'responseBodyExpression' is deprecated and no longer supported. "
              + "Replace it with 'responseExpression', which returns a full HTTP response, e.g. "
              + "'={body: ..., statusCode: 200, headers: {...}}'. See "
              + "https://docs.camunda.io/docs/components/connectors/protocol/http-webhook/ for details.");
    }
  }

  /**
   * Fails webhook deployment (activation) when HMAC authentication is enabled with the {@code
   * timestamp} scope but no {@code hmacTimestampHeader} is configured to read it from — that
   * combination can never pass verification, so it's rejected at deploy time rather than on every
   * request.
   */
  private static void rejectMissingHmacTimestampHeader(WebhookConnectorProperties props) {
    boolean timestampScopeSelected =
        Arrays.asList(props.hmacScopes()).contains(HMACScope.TIMESTAMP);
    boolean timestampHeaderConfigured =
        props.hmacTimestampHeader() != null && !props.hmacTimestampHeader().isBlank();
    if (enabled.equals(props.shouldValidateHmac())
        && timestampScopeSelected
        && !timestampHeaderConfigured) {
      throw new ConnectorInputException(
          "HMAC scope 'timestamp' is selected but 'hmacTimestampHeader' is not configured. "
              + "Set 'hmacTimestampHeader' to the name of the header carrying the request timestamp.");
    }
  }

  /**
   * Fails webhook deployment (activation) when the {@code timestamp} scope is selected and {@code
   * hmacTolerance} is not a positive ISO-8601 duration. Gated on the {@code timestamp} scope,
   * matching {@link #rejectMissingHmacTimestampHeader}: the property is hidden and irrelevant
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
    // The response is resolved per request from the element that actually matched (element-scoped),
    // via the function below. The runtime evaluates it after correlation, so webhook elements
    // deduplicated into one executable each produce their own response.
    return new WebhookProcessingResultImpl(
        mappedRequest, HttpWebhookExecutable::resolveResponse, null);
  }

  @SuppressWarnings("deprecation") // intentionally honors the legacy responseBodyExpression
  private static WebhookHttpResponse resolveResponse(WebhookResultContext result) {
    if (result.correlation() == null) {
      return null;
    }
    var expressions =
        result.correlation().bindProperties(DynamicWebhookPropertiesWrapper.class).inbound();
    if (expressions == null) {
      return null;
    }
    if (expressions.responseExpression() != null) {
      return expressions.responseExpression().apply(result);
    }
    if (expressions.responseBodyExpression() != null) {
      // Backwards compatibility: wrap the legacy body-only expression into a full response.
      return WebhookHttpResponse.ok(expressions.responseBodyExpression().apply(result));
    }
    return null;
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
