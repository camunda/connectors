/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.auth;

import io.camunda.connector.http.client.model.auth.*;

public class AuthenticationMapper {

  public static HttpAuthentication map(Authentication auth) {
    if (auth == null) {
      return new io.camunda.connector.http.client.model.auth.NoAuthentication();
    }

    return switch (auth) {
      case NoAuthentication ignored ->
          new io.camunda.connector.http.client.model.auth.NoAuthentication();
      case BasicAuthentication(String username, String password) ->
          new io.camunda.connector.http.client.model.auth.BasicAuthentication(username, password);
      case BearerAuthentication(String token) ->
          new io.camunda.connector.http.client.model.auth.BearerAuthentication(token);
      case OAuthAuthentication(
              String oauthTokenEndpoint,
              String clientId,
              String clientSecret,
              String audience,
              String clientAuthentication,
              String scopes) ->
          new io.camunda.connector.http.client.model.auth.OAuthAuthentication(
              oauthTokenEndpoint, clientId, clientSecret, audience, clientAuthentication, scopes);
    };
  }
}
