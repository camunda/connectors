/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model.auth;

import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.HashMap;
import java.util.Map;

@TemplateSubType(id = OAuthAuthentication.TYPE, label = "OAuth 2.0")
public record OAuthAuthentication(
    @FEEL
        @NotEmpty
        @Pattern(
            regexp = "^(=|(http://|https://|secrets|\\{\\{).*$)",
            message = "Must be a http(s) URL")
        @TemplateProperty(
            group = "authentication",
            description = "The OAuth token endpoint",
            label = "OAuth 2.0 token endpoint")
        String oauthTokenEndpoint,
    @FEEL
        @NotEmpty
        @TemplateProperty(
            group = "authentication",
            description = "Your application's client ID from the OAuth client",
            label = "Client ID")
        String clientId,
    @FEEL
        @NotEmpty
        @TemplateProperty(
            group = "authentication",
            description = "Your application's client secret from the OAuth client",
            label = "Client secret")
        String clientSecret,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            description = "The unique identifier of the target API you want to access",
            optional = true)
        String audience,
    @FEEL
        @NotEmpty
        @TemplateProperty(
            group = "authentication",
            type = PropertyType.Dropdown,
            choices = {
              @DropdownPropertyChoice(
                  value = OAuthConstants.CREDENTIALS_BODY,
                  label = "Send client credentials in body"),
              @DropdownPropertyChoice(
                  value = OAuthConstants.BASIC_AUTH_HEADER,
                  label = "Send as Basic Auth header")
            },
            description =
                "Send client ID and client secret as Basic Auth request in the header, or as client credentials in the request body")
        String clientAuthentication,
    @TemplateProperty(
            group = "authentication",
            description =
                "The scopes which you want to request authorization for (e.g.read:contacts)",
            optional = true)
        String scopes)
    implements Authentication {

  @TemplateProperty(ignore = true)
  public static final String TYPE = "oauth-client-credentials-flow";

  @TemplateProperty(ignore = true)
  public static final String GRANT_TYPE = "client_credentials";

  public Map<String, String> getDataForAuthRequestBody() {
    Map<String, String> data = new HashMap<>();
    data.put(OAuthConstants.GRANT_TYPE, GRANT_TYPE);
    data.put(OAuthConstants.AUDIENCE, this.audience());
    data.put(OAuthConstants.SCOPE, this.scopes());

    return data;
  }

  @Override
  public String toString() {
    return "OAuthAuthentication{"
        + "oauthTokenEndpoint='"
        + oauthTokenEndpoint
        + "'"
        + ", clientId='"
        + clientId
        + "'"
        + ", clientSecret=[REDACTED]"
        + ", audience='"
        + audience
        + "'"
        + ", clientAuthentication='"
        + clientAuthentication
        + "'"
        + ", scopes='"
        + scopes
        + "'"
        + "}";
  }
}
