/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.disabled;
import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.enabled;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorException;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorException.WebhookSecurityException;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorException.WebhookSecurityException.Reason;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookHttpResponse;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.api.inbound.webhook.WebhookResultContext;
import io.camunda.connector.generator.dsl.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.ConnectorElementType;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Failure;
import io.camunda.connector.inbound.authorization.WebhookAuthorizationHandler;
import io.camunda.connector.inbound.model.WebhookConnectorProperties;
import io.camunda.connector.inbound.model.WebhookConnectorProperties.WebhookConnectorPropertiesWrapper;
import io.camunda.connector.inbound.model.WebhookProcessingResultImpl;
import io.camunda.connector.inbound.signature.HMACAlgoCustomerChoice;
import io.camunda.connector.inbound.signature.HMACSignatureValidator;
import io.camunda.connector.inbound.signature.strategy.HMACEncodingStrategy;
import io.camunda.connector.inbound.signature.strategy.HMACEncodingStrategyFactory;
import io.camunda.connector.inbound.utils.HttpMethods;
import io.camunda.connector.inbound.utils.HttpWebhookUtil;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "Webhook", type = "io.camunda:webhook:1")
@ElementTemplate(
    id = "io.camunda.connectors.webhook",
    name = "Webhook Connector",
    icon = "icon.svg",
    version = 11,
    inputDataClass = WebhookConnectorPropertiesWrapper.class,
    description = "Configure webhook to receive callbacks",
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/http-webhook/",
    propertyGroups = {
      @PropertyGroup(id = "endpoint", label = "Webhook configuration"),
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "authorization", label = "Authorization"),
      @PropertyGroup(id = "webhookResponse", label = "Webhook response")
    },
    elementTypes = {
      @ConnectorElementType(
          appliesTo = BpmnType.START_EVENT,
          elementType = BpmnType.START_EVENT,
          templateIdOverride = "io.camunda.connectors.webhook.WebhookConnector.v1",
          templateNameOverride = "Webhook Start Event Connector"),
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
          templateNameOverride = "Webhook Boundary Event Connector")
    })
public class HttpWebhookExecutable implements WebhookConnectorExecutable {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpWebhookExecutable.class);

  private WebhookConnectorProperties props;
  private WebhookAuthorizationHandler<?> authChecker;
  private InboundConnectorContext context;
  private Function<WebhookResultContext, WebhookHttpResponse> responseExpression;

  @Override
  public void activate(InboundConnectorContext context) {
    this.context = context;
    var wrappedProps = context.bindProperties(WebhookConnectorPropertiesWrapper.class);
    props = new WebhookConnectorProperties(wrappedProps);
    authChecker = WebhookAuthorizationHandler.getHandlerForAuth(props.auth());
    responseExpression = mapResponseExpression();
  }

  @Override
  public WebhookResult triggerWebhook(WebhookProcessingPayload payload) {
    LOGGER.trace("Triggered webhook with context {} and payload {}", props.context(), payload);

    this.context.log(
        Activity.level(Severity.INFO)
            .tag(payload.method())
            .message("Url: " + payload.requestURL()));

    validateHttpMethod(payload);
    verifySignature(payload);

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
      throw new WebhookConnectorException(
          HttpResponseStatus.METHOD_NOT_ALLOWED.code(),
          "Method " + payload.method() + " not supported");
    }
  }

  private static MappedHttpRequest mapRequest(WebhookProcessingPayload payload) {
    return new MappedHttpRequest(
        HttpWebhookUtil.transformRawBodyToMap(
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

  private void verifySignature(WebhookProcessingPayload payload) {
    if (!webhookSignatureIsValid(payload)) {
      throw new WebhookSecurityException(
          HttpResponseStatus.UNAUTHORIZED.code(),
          Reason.INVALID_SIGNATURE,
          "HMAC signature check didn't pass");
    }
  }

  private boolean webhookSignatureIsValid(WebhookProcessingPayload payload) {
    try {
      if (shouldValidateHmac()) {
        HMACEncodingStrategy strategy =
            HMACEncodingStrategyFactory.getStrategy(props.hmacScopes(), payload.method());
        byte[] bytesToSign = strategy.getBytesToSign(payload);
        return validateHmacSignature(bytesToSign, payload);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return true;
  }

  private boolean shouldValidateHmac() {
    final String shouldValidateHmac =
        Optional.ofNullable(props.shouldValidateHmac()).orElse(disabled.name());
    return enabled.name().equals(shouldValidateHmac);
  }

  private boolean validateHmacSignature(byte[] signatureData, WebhookProcessingPayload payload)
      throws NoSuchAlgorithmException, InvalidKeyException, IOException {
    final HMACSignatureValidator hmacSignatureValidator =
        new HMACSignatureValidator(
            signatureData,
            payload.headers(),
            props.hmacHeader(),
            props.hmacSecret(),
            HMACAlgoCustomerChoice.valueOf(props.hmacAlgorithm()));
    return hmacSignatureValidator.isRequestValid();
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
                          HttpWebhookUtil.transformRawBodyToMap(
                              payload.rawBody(),
                              HttpWebhookUtil.extractContentType(payload.headers())),
                          "headers",
                          payload.headers(),
                          "params",
                          payload.params())));
    }
    return result;
  }
}
