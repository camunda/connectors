/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.disabled;
import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.enabled;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.inbound.authorization.WebhookAuthChecker;
import io.camunda.connector.inbound.model.WebhookConnectorProperties;
import io.camunda.connector.inbound.model.WebhookConnectorProperties.WebhookConnectorPropertiesWrapper;
import io.camunda.connector.inbound.model.WebhookProcessingResultImpl;
import io.camunda.connector.inbound.signature.HMACAlgoCustomerChoice;
import io.camunda.connector.inbound.signature.HMACSignatureValidator;
import io.camunda.connector.inbound.utils.HttpMethods;
import io.camunda.connector.inbound.utils.HttpWebhookUtil;
import io.camunda.connector.inbound.utils.ObjectMapperSupplier;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "WEBHOOK_INBOUND", type = "io.camunda:webhook:1")
public class HttpWebhookExecutable implements WebhookConnectorExecutable {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpWebhookExecutable.class);
  private final ObjectMapper objectMapper;

  private WebhookConnectorProperties props;
  private WebhookAuthChecker authChecker;

  public HttpWebhookExecutable() {
    this(ObjectMapperSupplier.getMapperInstance());
  }

  public HttpWebhookExecutable(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public WebhookResult triggerWebhook(WebhookProcessingPayload payload)
      throws NoSuchAlgorithmException, InvalidKeyException, IOException {
    LOGGER.trace("Triggered webhook with context " + props.context() + " and payload " + payload);

    if (!HttpMethods.any.name().equalsIgnoreCase(props.method())
        && !payload.method().equalsIgnoreCase(props.method())) {
      throw new IOException("Webhook failed: method not supported");
    }

    WebhookProcessingResultImpl response = new WebhookProcessingResultImpl();

    if (!webhookSignatureIsValid(payload)) {
      throw new IOException("Webhook failed: HMAC signature check didn't pass");
    }

    authChecker.checkAuthorization(payload);

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
      return validateHmacSignature(
          HttpWebhookUtil.extractSignatureData(payload, props.hmacScopes()), payload);
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
      throw new Exception("Inbound connector context cannot be null");
    }
    var wrappedProps = context.bindProperties(WebhookConnectorPropertiesWrapper.class);
    props = new WebhookConnectorProperties(wrappedProps);
    authChecker = new WebhookAuthChecker(props.auth(), objectMapper);
  }
}
