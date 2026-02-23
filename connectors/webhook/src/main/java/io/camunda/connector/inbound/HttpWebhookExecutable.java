/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.enabled;

import io.camunda.connector.api.annotation.InboundConnector;
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
import io.camunda.connector.inbound.model.WebhookConnectorProperties;
import io.camunda.connector.inbound.model.WebhookConnectorProperties.WebhookConnectorPropertiesWrapper;
import io.camunda.connector.inbound.model.WebhookProcessingResultImpl;
import io.camunda.connector.inbound.signature.HMACVerifier;
import io.camunda.connector.inbound.utils.HttpMethods;
import io.camunda.connector.inbound.utils.HttpWebhookUtil;
import jakarta.annotation.Nullable;
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
    version = 13,
    inputDataClass = WebhookConnectorPropertiesWrapper.class,
    description = "Configure webhook to receive callbacks",
    documentationRef = "https://docs.camunda.io/docs/components/connectors/protocol/http-webhook/",
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
    authChecker = WebhookAuthorizationHandler.getHandlerForAuth(props.auth());
    responseExpression = mapResponseExpression();
    hmacVerifier =
        new HMACVerifier(
            props.hmacScopes(), props.hmacHeader(), props.hmacSecret(), props.hmacAlgorithm());
    context.reportHealth(Health.up());
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
