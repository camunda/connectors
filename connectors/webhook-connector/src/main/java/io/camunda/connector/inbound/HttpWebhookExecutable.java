/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.WebhookConnectorExecutable;
import io.camunda.connector.impl.inbound.WebhookRequestPayload;
import io.camunda.connector.impl.inbound.WebhookResponsePayload;
import io.camunda.connector.inbound.model.WebhookResponsePayloadImpl;
import io.camunda.connector.inbound.signature.HMACAlgoCustomerChoice;
import io.camunda.connector.inbound.signature.HMACSignatureValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;

import static io.camunda.connector.inbound.model.WebhookResponsePayloadImpl.*;
import static io.camunda.connector.inbound.signature.HMACSignatureValidator.*;

@InboundConnector(name = "WEBHOOK_INBOUND", type = "io.camunda:webhook:1")
public class HttpWebhookExecutable implements WebhookConnectorExecutable {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpWebhookExecutable.class);

  public HttpWebhookExecutable() {}

  @Override
  public WebhookResponsePayload triggerWebhook(
          InboundConnectorContext context, 
          WebhookRequestPayload webhookRequestPayload) 
          throws NoSuchAlgorithmException, InvalidKeyException {
    WebhookResponsePayloadImpl response = new WebhookResponsePayloadImpl();
    context.replaceSecrets(context.getProperties());
    final String shouldValidateHmac = Optional.ofNullable(context.getProperties()
            .getProperty(HMAC_VALIDATION_ENABLED_PROPERTY))
            .orElse(HMAC_VALIDATION_DISABLED);
    if (HMAC_VALIDATION_ENABLED.equals(shouldValidateHmac)) {
      // TODO: do a proper check
      final HMACSignatureValidator hmacSignatureValidator = new HMACSignatureValidator(
              webhookRequestPayload.rawBody(),
              webhookRequestPayload.headers(),
              context.getProperties().getProperty(HMAC_HEADER_PROPERTY),
              context.getProperties().getProperty(HMAC_SECRET_KEY_PROPERTY),
              HMACAlgoCustomerChoice.valueOf(context.getProperties().getProperty(HMAC_ALGO_PROPERTY))
      );
      if (!hmacSignatureValidator.isRequestValid()) {
        response.setBody(Map.of(
                DEFAULT_RESPONSE_STATUS_KEY, DEFAULT_RESPONSE_STATUS_VALUE_FAIL,
                HMAC_VALIDATION_FAILED_KEY, HMAC_VALIDATION_FAILED_REASON_DIDNT_MATCH
        ));
        return response;
      }
    }
    LOGGER.error("IGPETROV: webhook triggered");
    LOGGER.error("IGPETROV: got lovely payload: " + webhookRequestPayload);
    return response;
  }

  @Override
  public void activate(InboundConnectorContext inboundConnectorContext) {
    LOGGER.error("IGPETROV: webhook activated");
  }

  @Override
  public void deactivate() {
    LOGGER.error("IGPETROV: webhook deactivate");
  }
}
