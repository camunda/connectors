/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.signature.strategy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import java.util.Map;
import org.junit.jupiter.api.Test;

class URLAndBodyEncodingStrategyTest {

  private static byte[] bytesToSign(String body) {
    var payload = mock(WebhookProcessingPayload.class);
    when(payload.requestURL()).thenReturn("https://localhost/inbound/test");
    when(payload.headers()).thenReturn(Map.of("content-type", "application/x-www-form-urlencoded"));
    when(payload.rawBody()).thenReturn(body.getBytes(UTF_8));
    return new URLAndBodyEncodingStrategy().getBytesToSign(payload);
  }

  @Test
  void encodedAmpersandInValue_isSignedAsDecodedValue() {
    assertThat(new String(bytesToSign("Body=Rock+%26+Roll&From=%2B1555"), UTF_8))
        .isEqualTo("https://localhost/inbound/testBodyRock & RollFrom+1555");
  }

  @Test
  void literalEqualsInValue_isPreserved() {
    assertThat(new String(bytesToSign("Token=abc=="), UTF_8))
        .isEqualTo("https://localhost/inbound/testTokenabc==");
  }
}
