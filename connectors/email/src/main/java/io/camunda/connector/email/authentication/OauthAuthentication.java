/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.authentication;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.mail.Session;
import jakarta.mail.Transport;
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

  @Override
  public Session smtpSession() {
    return null;
  }

  @Override
  public Transport smtpTransport(Session session) {
    return null;
  }
}
