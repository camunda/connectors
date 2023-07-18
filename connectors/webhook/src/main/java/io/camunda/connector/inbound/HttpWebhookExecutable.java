/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.disabled;
import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.enabled;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingResult;
import io.camunda.connector.inbound.authorization.AuthorizationService;
import io.camunda.connector.inbound.model.WebhookConnectorProperties;
import io.camunda.connector.inbound.model.WebhookProcessingResultImpl;
import io.camunda.connector.inbound.signature.HMACAlgoCustomerChoice;
import io.camunda.connector.inbound.signature.HMACSignatureValidator;
import io.camunda.connector.inbound.utils.HttpMethods;
import io.camunda.connector.inbound.utils.HttpWebhookUtil;
import io.camunda.connector.inbound.utils.ObjectMapperSupplier;
import java.io.IOException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "WEBHOOK_INBOUND", type = "io.camunda:webhook:1")
public class HttpWebhookExecutable implements WebhookConnectorExecutable {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpWebhookExecutable.class);
  private final ObjectMapper objectMapper;

  private WebhookConnectorProperties props;
  private JwkProvider jwkProvider;

  public HttpWebhookExecutable() {
    this(ObjectMapperSupplier.getMapperInstance());
  }

  public HttpWebhookExecutable(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public WebhookProcessingResult triggerWebhook(WebhookProcessingPayload payload)
      throws NoSuchAlgorithmException, InvalidKeyException, IOException {
    LOGGER.trace(
        "Triggered webhook with context " + props.getContext() + " and payload " + payload);

    if (!HttpMethods.any.name().equalsIgnoreCase(props.getMethod())
        && !payload.method().equalsIgnoreCase(props.getMethod())) {
      throw new IOException("Webhook failed: method not supported");
    }

    WebhookProcessingResultImpl response = new WebhookProcessingResultImpl();

    if (!webhookSignatureIsValid(props, payload)) {
      throw new IOException("Webhook failed: HMAC signature check didn't pass");
    }

    if (props.getAuthorizationType() != WebhookConnectorProperties.AuthorizationType.NONE) {
      AuthorizationService.verifyAuthorization(props, payload, jwkProvider, objectMapper);
    }

    response.setBody(
        HttpWebhookUtil.transformRawBodyToMap(
            payload.rawBody(), HttpWebhookUtil.extractContentType(payload.headers())));
    response.setHeaders(payload.headers());
    response.setParams(payload.params());

    return response;
  }

  private boolean webhookSignatureIsValid(
      WebhookConnectorProperties context, WebhookProcessingPayload payload)
      throws NoSuchAlgorithmException, InvalidKeyException, IOException {
    if (shouldValidateHmac(context)) {
      return validateHmacSignature(
          HttpWebhookUtil.extractSignatureData(payload, context.getHmacScopes()), payload, context);
    }
    return true;
  }

  private boolean shouldValidateHmac(WebhookConnectorProperties context) {
    final String shouldValidateHmac =
        Optional.ofNullable(context.getShouldValidateHmac()).orElse(disabled.name());
    return enabled.name().equals(shouldValidateHmac);
  }

  private boolean validateHmacSignature(
      byte[] signatureData, WebhookProcessingPayload payload, WebhookConnectorProperties context)
      throws NoSuchAlgorithmException, InvalidKeyException, IOException {
    final HMACSignatureValidator hmacSignatureValidator =
        new HMACSignatureValidator(
            signatureData,
            payload.headers(),
            context.getHmacHeader(),
            context.getHmacSecret(),
            HMACAlgoCustomerChoice.valueOf(context.getHmacAlgorithm()));
    return hmacSignatureValidator.isRequestValid();
  }

  @Override
  public void activate(InboundConnectorContext context) throws Exception {
    if (context == null) {
      throw new Exception("Inbound connector context cannot be null");
    }
    props = new WebhookConnectorProperties(context.getProperties());

    // jwk url must be specified in the element template for this to work
    if (WebhookConnectorProperties.AuthorizationType.JWT.equals(props.getAuthorizationType())) {
      this.jwkProvider =
          new JwkProviderBuilder(new URL(props.getJwkUrl()))
              .cached(10, 10, TimeUnit.MINUTES) // Cache JWKs for 10 minutes
              .rateLimited(10, 1, TimeUnit.MINUTES) // Rate limit to 10 requests per minute
              .build();
    }
  }
}
