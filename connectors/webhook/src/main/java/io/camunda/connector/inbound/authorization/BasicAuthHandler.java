/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import com.google.common.net.HttpHeaders;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Failure.InvalidCredentials;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Success;
import io.camunda.connector.inbound.model.WebhookAuthorization.BasicAuth;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map.Entry;
import java.util.stream.Collectors;

final class BasicAuthHandler extends WebhookAuthorizationHandler<BasicAuth> {
  public BasicAuthHandler(BasicAuth authorization) {
    super(authorization);
  }

  @Override
  public AuthorizationResult checkAuthorization(WebhookProcessingPayload payload) {
    String authHeader = getAuthHeader(payload);
    boolean isPresent =
        authHeader != null
            && authHeader.toLowerCase().startsWith("basic ")
            && authHeader.trim().length() > "basic ".length();

    if (!isPresent) {
      return AUTH_HEADER_MISSING_RESULT;
    }
    String authValue = authHeader.split(" ")[1];
    String expectedAuth = expectedAuthorization.username() + ":" + expectedAuthorization.password();
    String actualAuth =
        new String(Base64.getDecoder().decode(authValue.getBytes(StandardCharsets.UTF_8)));
    if (!expectedAuth.equals(actualAuth)) {
      return AUTH_HEADER_INVALID_RESULT;
    }
    return Success.INSTANCE;
  }

  private String getAuthHeader(WebhookProcessingPayload payload) {
    return payload.headers().entrySet().stream()
        .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(), Entry::getValue))
        .get(HttpHeaders.AUTHORIZATION.toLowerCase());
  }

  private static final AuthorizationResult AUTH_HEADER_MISSING_RESULT =
      new InvalidCredentials("Basic auth header is missing");
  private static final AuthorizationResult AUTH_HEADER_INVALID_RESULT =
      new InvalidCredentials("Basic auth header is invalid");
}
