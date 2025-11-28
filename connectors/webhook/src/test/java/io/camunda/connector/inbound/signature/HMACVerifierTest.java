/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.signature;

import static io.camunda.connector.inbound.signature.HMACAlgoCustomerChoice.sha_256;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorException.WebhookSecurityException;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.inbound.model.HMACScope;
import io.camunda.connector.inbound.utils.HttpMethods;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HMACVerifierTest {

  @Test
  void verifySignature_WhenSignatureMatches_ShouldNotThrowException() {
    HMACVerifier verifier =
        new HMACVerifier(new HMACScope[] {HMACScope.BODY}, "X-HMAC-Sig", "mySecretKey", sha_256);

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers())
        .thenReturn(
            Map.of(
                HttpHeaders.CONTENT_TYPE,
                MediaType.JSON_UTF_8.toString(),
                "X-HMAC-Sig",
                "fa431d91a69beb76186b3b082c5bb87bab0702769d65761af2361cbf3a17cc09"));
    when(payload.rawBody()).thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    assertThatCode(() -> verifier.verifySignature(payload)).doesNotThrowAnyException();
  }

  @Test
  void verifySignature_WhenSignatureDoesNotMatch_ShouldThrowException() {
    HMACVerifier verifier =
        new HMACVerifier(new HMACScope[] {HMACScope.BODY}, "X-HMAC-Sig", "mySecretKey", sha_256);

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers())
        .thenReturn(
            Map.of(
                HttpHeaders.CONTENT_TYPE,
                MediaType.JSON_UTF_8.toString(),
                "X-HMAC-Sig",
                "invalidSignature123"));
    when(payload.rawBody()).thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> verifier.verifySignature(payload))
        .isInstanceOf(WebhookSecurityException.class)
        .hasMessageContaining("HMAC signature check didn't pass");
  }
}
