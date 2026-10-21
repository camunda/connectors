/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.connection;

import io.camunda.connector.http.client.authentication.OAuthConstants;
import org.jspecify.annotations.Nullable;

/** How a custom model endpoint authenticates the calls. */
public sealed interface EndpointAuthentication {

  record None() implements EndpointAuthentication {}

  record ApiKey(String apiKey) implements EndpointAuthentication {

    @Override
    public String toString() {
      return "ApiKey{apiKey=[REDACTED]}";
    }
  }

  /**
   * OAuth 2.0 client credentials flow.
   *
   * @param audience the identifier of the target API, or {@code null} for none
   * @param scopes the requested scopes, or {@code null} for none
   */
  record OAuthClientCredentials(
      String tokenEndpoint,
      String clientId,
      String clientSecret,
      @Nullable String audience,
      ClientAuthenticationMethod clientAuthentication,
      @Nullable String scopes)
      implements EndpointAuthentication {

    @Override
    public String toString() {
      return "OAuthClientCredentials{tokenEndpoint=%s, clientId=[REDACTED], clientSecret=[REDACTED], audience=%s, clientAuthentication=%s, scopes=%s}"
          .formatted(tokenEndpoint, audience, clientAuthentication, scopes);
    }
  }

  enum ClientAuthenticationMethod {
    BASIC_AUTH_HEADER(OAuthConstants.BASIC_AUTH_HEADER),
    CREDENTIALS_BODY(OAuthConstants.CREDENTIALS_BODY);

    private final String oauthConstant;

    ClientAuthenticationMethod(String oauthConstant) {
      this.oauthConstant = oauthConstant;
    }

    public String oauthConstant() {
      return oauthConstant;
    }
  }
}
