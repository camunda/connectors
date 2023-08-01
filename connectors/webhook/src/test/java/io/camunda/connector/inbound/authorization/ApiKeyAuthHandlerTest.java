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
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Failure.InvalidCredentials;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Success;
import io.camunda.connector.inbound.model.WebhookAuthorization.ApiKeyAuth;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class ApiKeyAuthHandlerTest {

  private final FeelEngineWrapper feel = new FeelEngineWrapper();
  private final String locatorExpression = "=split(request.headers.authorization, \" \")[2]";
  private final Function<Object, String> locator =
      request -> feel.evaluate(locatorExpression, request);
  private final ApiKeyAuth expectedAuth = new ApiKeyAuth("apiKey", locator);

  @Test
  void apiKey_validKey() {
    // given
    var payload = preparePayload("apiKey");
    var checker = new ApiKeyAuthHandler(expectedAuth);

    // when
    var result = checker.checkAuthorization(payload);

    // then
    assertThat(result).isInstanceOf(Success.class);
  }

  @Test
  void apiKey_invalidKey() {
    // given
    var payload = preparePayload("wrong-key");
    var checker = new ApiKeyAuthHandler(expectedAuth);

    // when
    var result = checker.checkAuthorization(payload);

    // then
    assertThat(result).isInstanceOf(InvalidCredentials.class);
  }

  @Test
  void apiKey_malformedHeader() {
    // given
    var payload = mock(WebhookProcessingPayload.class);
    when(payload.headers()).thenReturn(Map.of(HttpHeaders.AUTHORIZATION, "NotBearer"));
    var checker = new ApiKeyAuthHandler(expectedAuth);

    // when
    var result = checker.checkAuthorization(payload);

    // then
    assertThat(result).isInstanceOf(InvalidCredentials.class);
  }

  @Test
  void apiKey_invalidLocatorExpression() {
    var payload = preparePayload("key");
    var invalidAuthDefinition =
        new ApiKeyAuth(
            "key",
            request -> {
              throw new FeelEngineWrapperException("oops", "oops", null);
            });
    var checker = new ApiKeyAuthHandler(invalidAuthDefinition);

    // when
    var result = checker.checkAuthorization(payload);

    // then
    assertThat(result).isInstanceOf(InvalidCredentials.class);
  }

  private WebhookProcessingPayload preparePayload(String apiKey) {
    return new TestWebhookProcessingPayload(apiKey, null);
  }
}
