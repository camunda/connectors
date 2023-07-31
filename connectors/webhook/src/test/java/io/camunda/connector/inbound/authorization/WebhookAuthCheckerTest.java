/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.net.HttpHeaders;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.inbound.model.WebhookAuthorization;
import io.camunda.connector.inbound.model.WebhookAuthorization.ApiKeyAuth;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class WebhookAuthCheckerTest {

  @Nested
  class BasicAuth {

    private final WebhookAuthorization.BasicAuth expectedAuth =
        new WebhookAuthorization.BasicAuth("username", "password");

    @Test
    void basic_validCredentials() {
      // given
      var payload = mockBasicAuthHeader("username", "password");
      var checker = new WebhookAuthChecker(expectedAuth);

      // when/then
      assertDoesNotThrow(() -> checker.checkAuthorization(payload));
    }

    @Test
    void basic_invalidCredentials() {
      // given
      var payload = mockBasicAuthHeader("wrong-username", "wrong-password");
      var checker = new WebhookAuthChecker(expectedAuth);

      // when/then
      assertThrows(IOException.class, () -> checker.checkAuthorization(payload));
    }

    @Test
    void basic_malformedHeader() {
      // given
      var payload = mock(WebhookProcessingPayload.class);
      when(payload.headers()).thenReturn(Map.of(HttpHeaders.AUTHORIZATION, "NotBasic 123"));
      var checker = new WebhookAuthChecker(expectedAuth);

      // when/then
      assertThrows(IOException.class, () -> checker.checkAuthorization(payload));
    }

    private WebhookProcessingPayload mockBasicAuthHeader(String username, String password) {
      var header =
          "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
      var payload = mock(WebhookProcessingPayload.class);
      when(payload.headers()).thenReturn(Map.of(HttpHeaders.AUTHORIZATION, header));
      return payload;
    }
  }

  @Nested
  class ApiKey {
    private final FeelEngineWrapper feel = new FeelEngineWrapper();
    private final String locatorExpression = "=split(request.headers.authorization, \" \")[2]";
    private final Function<Object, String> locator =
        request -> feel.evaluate(locatorExpression, request);
    private final ApiKeyAuth expectedAuth = new ApiKeyAuth("apiKey", locator);

    @Test
    void apiKey_validKey() {
      // given
      var payload = preparePayload("apiKey");
      var checker = new WebhookAuthChecker(expectedAuth);

      // when/then
      assertDoesNotThrow(() -> checker.checkAuthorization(payload));
    }

    @Test
    void apiKey_invalidKey() {
      // given
      var payload = preparePayload("wrong-key");
      var checker = new WebhookAuthChecker(expectedAuth);

      // when/then
      assertThrows(IOException.class, () -> checker.checkAuthorization(payload));
    }

    @Test
    void apiKey_malformedHeader() {
      // given
      var payload = mock(WebhookProcessingPayload.class);
      when(payload.headers()).thenReturn(Map.of(HttpHeaders.AUTHORIZATION, "NotBearer"));
      var checker = new WebhookAuthChecker(expectedAuth);

      // when/then
      assertThrows(FeelEngineWrapperException.class, () -> checker.checkAuthorization(payload));
    }

    private WebhookProcessingPayload preparePayload(String apiKey) {
      return new TestWebhookProcessingPayload(apiKey, null);
    }
  }
}
