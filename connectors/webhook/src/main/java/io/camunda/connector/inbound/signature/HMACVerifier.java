/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.signature;

import io.camunda.connector.api.inbound.webhook.WebhookConnectorException.WebhookSecurityException;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorException.WebhookSecurityException.Reason;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.inbound.model.HMACScope;
import io.camunda.connector.inbound.signature.strategy.HMACEncodingStrategy;
import io.camunda.connector.inbound.signature.strategy.HMACEncodingStrategyFactory;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class HMACVerifier {

  private final HMACScope[] hmacScopes;
  private final String hmacHeader;
  private final String hmacSecret;
  private final String hmacAlgorithm;

  public HMACVerifier(
      HMACScope[] hmacScopes, String hmacHeader, String hmacSecret, String hmacAlgorithm) {
    this.hmacScopes = hmacScopes;
    this.hmacHeader = hmacHeader;
    this.hmacSecret = hmacSecret;
    this.hmacAlgorithm = hmacAlgorithm;
  }

  public void verifySignature(WebhookProcessingPayload payload) {
    if (!webhookSignatureIsValid(payload)) {
      throw new WebhookSecurityException(
          401, Reason.INVALID_SIGNATURE, "HMAC signature check didn't pass");
    }
  }

  private boolean webhookSignatureIsValid(WebhookProcessingPayload payload) {
    try {
      HMACEncodingStrategy strategy =
          HMACEncodingStrategyFactory.getStrategy(hmacScopes, payload.method());
      byte[] bytesToSign = strategy.getBytesToSign(payload);
      return validateHmacSignature(bytesToSign, payload);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private boolean validateHmacSignature(byte[] signatureData, WebhookProcessingPayload payload)
      throws NoSuchAlgorithmException, InvalidKeyException, IOException {
    final HMACSignatureValidator hmacSignatureValidator =
        new HMACSignatureValidator(
            signatureData,
            payload.headers(),
            hmacHeader,
            hmacSecret,
            HMACAlgoCustomerChoice.valueOf(hmacAlgorithm));
    return hmacSignatureValidator.isRequestValid();
  }
}
