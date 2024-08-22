/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.authentication;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(id = "oauth", label = "Oauth")
public final class OauthAuthentication implements Authentication {
  @TemplateProperty(
      group = "authentication",
      label = "Email address",
      description = "Provide email")
  @NotBlank
  private String mailOauth2;

  @TemplateProperty(group = "authentication", label = "Oauth2 token", description = "Give token")
  @NotBlank
  private String tokenOauth2;

  public @NotBlank String getMailOauth2() {
    return mailOauth2;
  }

  public void setMailOauth2(@NotBlank String mailOauth2) {
    this.mailOauth2 = mailOauth2;
  }

  public @NotBlank String getTokenOauth2() {
    return tokenOauth2;
  }

  public void setTokenOauth2(@NotBlank String tokenOauth2) {
    this.tokenOauth2 = tokenOauth2;
  }

  @Override
  public String getSender() {
    return this.mailOauth2;
  }

  @Override
  public String getSecret() {
    return this.tokenOauth2;
  }
}
