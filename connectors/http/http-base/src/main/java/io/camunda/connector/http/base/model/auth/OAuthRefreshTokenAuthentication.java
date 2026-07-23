/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model.auth;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

@TemplateSubType(
    id = io.camunda.connector.http.client.model.auth.OAuthRefreshTokenAuthentication.TYPE,
    label = "OAuth 2.0 Refresh Token")
public record OAuthRefreshTokenAuthentication(
    @FEEL
        @NotEmpty
        @Pattern(
            regexp = "^(=|(http://|https://|secrets|\\{\\{).*$)",
            message = "Must be a http(s) URL")
        @TemplateProperty(
            id = "oauthRefreshToken.oauthTokenEndpoint",
            group = "authentication",
            label = "OAuth 2.0 token endpoint")
        String oauthTokenEndpoint,
    @FEEL
        @NotEmpty
        @TemplateProperty(
            id = "oauthRefreshToken.clientId",
            group = "authentication",
            tooltip = "Your application's client ID from the OAuth client",
            label = "Client ID")
        String clientId,
    @FEEL
        @TemplateProperty(
            id = "oauthRefreshToken.clientSecret",
            group = "authentication",
            tooltip = "Your application's client secret from the OAuth client",
            label = "Client secret",
            optional = true,
            secret = true)
        String clientSecret,
    @FEEL
        @NotEmpty
        @TemplateProperty(
            id = "oauthRefreshToken.refreshToken",
            group = "authentication",
            tooltip = "The refresh token used to obtain a new access token",
            label = "Refresh token",
            secret = true)
        String refreshToken,
    @TemplateProperty(
            id = "oauthRefreshToken.scopes",
            group = "authentication",
            tooltip = "The scopes to request authorization for (space-separated)",
            label = "Scopes",
            optional = true)
        String scopes)
    implements Authentication {

  @TemplateProperty(ignore = true)
  public static final String TYPE =
      io.camunda.connector.http.client.model.auth.OAuthRefreshTokenAuthentication.TYPE;
}
