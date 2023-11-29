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
import io.camunda.connector.api.inbound.ActivityLog;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.VerifiableWebhook;
import io.camunda.connector.api.inbound.webhook.VerifiableWebhook.WebhookHttpVerificationResult;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorException;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorException.WebhookSecurityException;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorException.WebhookSecurityException.Reason;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "Webhook", type = "io.camunda:webhook:1")
public class HttpWebhookExecutable implements WebhookConnectorExecutable, VerifiableWebhook {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpWebhookExecutable.class);

  private WebhookConnectorProperties props;
  private WebhookAuthorizationHandler<?> authChecker;

  private InboundConnectorContext context;

  @Override
  public WebhookResult triggerWebhook(WebhookProcessingPayload payload)
      throws NoSuchAlgorithmException, InvalidKeyException, IOException {
    LOGGER.trace("Triggered webhook with context " + props.context() + " and payload " + payload);
    this.context.log(
        ActivityLog.level(Severity.INFO)
            .tag(payload.method())
            .message(
                "Url: "
                    + payload.requestURL()
                    + ", params: "
                    + payload.params()
                    + ", headers: "
                    + payload.headers()));
    if (!HttpMethods.any.name().equalsIgnoreCase(props.method())
        && !payload.method().equalsIgnoreCase(props.method())) {
      throw new WebhookConnectorException(
          HttpResponseStatus.METHOD_NOT_ALLOWED.code(),
          "Method " + payload.method() + " not supported");
    }

    WebhookProcessingResultImpl response = new WebhookProcessingResultImpl();

    if (!webhookSignatureIsValid(payload)) {
      throw new WebhookSecurityException(
          HttpResponseStatus.UNAUTHORIZED.code(),
          Reason.INVALID_SIGNATURE,
          "HMAC signature check didn't pass");
    }

    var authResult = authChecker.checkAuthorization(payload);
    if (authResult instanceof Failure failureResult) {
      throw failureResult.toException();
    }

    response.setRequest(
        new MappedHttpRequest(
            HttpWebhookUtil.transformRawBodyToMap(
                payload.rawBody(), HttpWebhookUtil.extractContentType(payload.headers())),
            payload.headers(),
            payload.params()));

    if (props.responseBodyExpression() != null) {
      response.setResponseBodyExpression(props.responseBodyExpression());
    }

    return response;
  }

  private boolean webhookSignatureIsValid(WebhookProcessingPayload payload)
      throws NoSuchAlgorithmException, InvalidKeyException, IOException {
    if (shouldValidateHmac()) {
      HMACEncodingStrategy strategy =
          HMACEncodingStrategyFactory.getStrategy(props.hmacScopes(), payload.method());
      byte[] bytesToSign = strategy.getBytesToSign(payload);
      return validateHmacSignature(bytesToSign, payload);
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
  public void activate(InboundConnectorContext context) throws Exception {
    if (context == null) {
      context.reportHealth(Health.down("Inbound connector context cannot be null"));
      throw new Exception("Inbound connector context cannot be null");
    }
    this.context = context;
    var wrappedProps = context.bindProperties(WebhookConnectorPropertiesWrapper.class);
    props = new WebhookConnectorProperties(wrappedProps);
    context.reportHealth(Health.up());
    authChecker = WebhookAuthorizationHandler.getHandlerForAuth(props.auth());
  }

  @Override
  public void deactivate() throws Exception {}

  @Override
  public WebhookHttpVerificationResult verify(final WebhookProcessingPayload payload) {
    WebhookHttpVerificationResult result = null;
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
