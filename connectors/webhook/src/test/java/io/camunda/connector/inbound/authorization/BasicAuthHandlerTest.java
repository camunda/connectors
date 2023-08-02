/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.net.HttpHeaders;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Failure.InvalidCredentials;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Success;
import io.camunda.connector.inbound.model.WebhookAuthorization;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class BasicAuthHandlerTest {

  private final WebhookAuthorization.BasicAuth expectedAuth =
      new WebhookAuthorization.BasicAuth("username", "password");

  @Test
  void basic_validCredentials() {
    // given
    var payload = mockBasicAuthHeader("username", "password");
    var handler = new BasicAuthHandler(expectedAuth);

    // when
    var result = handler.checkAuthorization(payload);

    // when/then
    assertThat(result).isInstanceOf(Success.class);
  }

  @Test
  void basic_invalidCredentials() {
    // given
    var payload = mockBasicAuthHeader("wrong-username", "wrong-password");
    var handler = new BasicAuthHandler(expectedAuth);

    // when
    var result = handler.checkAuthorization(payload);

    // when/then
    assertThat(result).isInstanceOf(InvalidCredentials.class);
  }

  @Test
  void basic_malformedHeader() {
    // given
    var payload = mock(WebhookProcessingPayload.class);
    when(payload.headers()).thenReturn(Map.of(HttpHeaders.AUTHORIZATION, "NotBasic 123"));
    var handler = new BasicAuthHandler(expectedAuth);

    // when
    var result = handler.checkAuthorization(payload);

    // when/then
    assertThat(result).isInstanceOf(InvalidCredentials.class);
  }

  @Test
  void basic_missingHeader() {
    // given
    var payload = mock(WebhookProcessingPayload.class);
    when(payload.headers()).thenReturn(Map.of());
    var handler = new BasicAuthHandler(expectedAuth);

    // when
    var result = handler.checkAuthorization(payload);

    // when/then
    assertThat(result).isInstanceOf(InvalidCredentials.class);
  }

  private WebhookProcessingPayload mockBasicAuthHeader(String username, String password) {
    var header =
        "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    var payload = mock(WebhookProcessingPayload.class);
    when(payload.headers()).thenReturn(Map.of(HttpHeaders.AUTHORIZATION, header));
    return payload;
  }
}
