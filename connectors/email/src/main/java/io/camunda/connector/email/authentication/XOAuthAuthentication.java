/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.authentication;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotEmpty;
import java.util.HashMap;
import java.util.Map;

@TemplateSubType(id = XOAuthAuthentication.TYPE, label = "OAuth 2.0")
public record XOAuthAuthentication(
    @FEEL
        @NotEmpty
        @TemplateProperty(
            group = "authentication",
            description = "The mail address e-mails should used for readign or writing mails",
            label = "E-Mail Address")
        @NotEmpty
        String username,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            description =
                "The refresh token used to obtain new access tokens. See <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/email/#authentication\">documentation</a> for detailed explanation")
        @NotEmpty
        String refreshToken,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            description =
                "The client id used to obtain new access tokens. See <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/email/#authentication\">documentation</a> for detailed explanation")
        @NotEmpty
        String clientId,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            description =
                "The client secret used to obtain new access tokens. See <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/email/#authentication\">documentation</a> for detailed explanation")
        @NotEmpty
        String clientSecret,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            description =
                "The token endpoint used to obtain new access tokens. See <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/email/#authentication\">documentation</a> for detailed explanation")
        @NotEmpty
        String tokenEndpoint,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            description =
                "The scopes of the requested tokens. See <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/email/#authentication\">documentation</a> for detailed explanation")
        String scope)
    implements Authentication {

  @TemplateProperty(ignore = true)
  public static final String TYPE = "xoauth-user-credentials-flow";

  public Map<String, Object> getRequestAccessTokenBody() {
    Map<String, Object> body = new HashMap<>();
    body.put("grant_type", "refresh_token");
    body.put("refresh_token", this.refreshToken());
    body.put("client_id", this.clientId());
    body.put("client_secret", this.clientSecret());
    if (this.scope() != null) {
      body.put("scope", this.scope());
    }
    return body;
  }
}
