/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations.model.auth;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.http.client.authentication.OAuthConstants;
import jakarta.validation.constraints.Pattern;

@TemplateSubType(id = OAuthAuthentication.TYPE, label = "OAuth 2.0 (client credentials)")
public record OAuthAuthentication(
    @Pattern(
            regexp = "^($|=|(http://|https://|secrets|\\{\\{).*$)",
            message = "Must be a http(s) URL")
        @TemplateProperty(
            group = "authentication",
            label = "OAuth 2.0 token endpoint",
            description = "The OAuth token endpoint")
        String oauthTokenEndpoint,
    @TemplateProperty(
            group = "authentication",
            label = "Client ID",
            description = "Your application's client ID from the OAuth client")
        String clientId,
    @TemplateProperty(
            group = "authentication",
            label = "Client secret",
            description = "Your application's client secret from the OAuth client")
        String clientSecret,
    @TemplateProperty(
            group = "authentication",
            label = "Audience",
            description = "The unique identifier of the target API you want to access",
            optional = true)
        String audience,
    @TemplateProperty(
            group = "authentication",
            label = "Client authentication",
            type = PropertyType.Dropdown,
            defaultValue = OAuthConstants.CREDENTIALS_BODY,
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
            label = "Scopes",
            description =
                "The scopes which you want to request authorization for (e.g. read:contacts)",
            optional = true)
        String scopes)
    implements AppIntegrationsAuthentication {

  @TemplateProperty(ignore = true)
  public static final String TYPE = "oauth-client-credentials-flow";

  @Override
  public String toString() {
    return "OAuthAuthentication{oauthTokenEndpoint='%s', clientId='%s', clientSecret=***, audience='%s', clientAuthentication='%s', scopes='%s'}"
        .formatted(oauthTokenEndpoint, clientId, audience, clientAuthentication, scopes);
  }
}
