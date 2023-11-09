/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.auth;

import io.camunda.connector.automationanywhere.model.request.Configuration;
import io.camunda.connector.automationanywhere.model.request.auth.ApiKeyAuthentication;
import io.camunda.connector.automationanywhere.model.request.auth.Authentication;
import io.camunda.connector.automationanywhere.model.request.auth.PasswordBasedAuthentication;
import io.camunda.connector.automationanywhere.model.request.auth.TokenBasedAuthentication;

public final class AuthenticationFactory {

  private AuthenticationFactory() {}

  public static AuthenticationProvider createProvider(
      final Authentication authData, final Configuration configuration) {

    if (authData instanceof TokenBasedAuthentication t) {
      return new TokenBasedAuthProvider(t.token());
    } else if (authData instanceof PasswordBasedAuthentication p) {
      return new PasswordBasedAuthProvider(
          p.username(),
          p.password(),
          p.multipleLogin(),
          configuration.controlRoomUrl(),
          configuration.connectionTimeoutInSeconds());
    } else if (authData instanceof ApiKeyAuthentication k) {
      return new ApiKeyAuthProvider(
          k.username(),
          k.apiKey(),
          configuration.controlRoomUrl(),
          configuration.connectionTimeoutInSeconds());
    } else {
      throw new IllegalArgumentException("Unsupported authentication type");
    }
  }
}
