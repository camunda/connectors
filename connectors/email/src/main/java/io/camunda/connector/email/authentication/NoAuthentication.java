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
import java.util.Optional;

@TemplateSubType(id = "none", label = "No authentication")
public final class NoAuthentication implements Authentication {
  @TemplateProperty(
      group = "authentication",
      label = "Email address",
      description = "Provide email")
  @NotBlank
  private String noAuthenticationMail;

  public @NotBlank String getMailOauth2() {
    return noAuthenticationMail;
  }

  public void setMailOauth2(@NotBlank String noAuthenticationMail) {
    this.noAuthenticationMail = noAuthenticationMail;
  }

  @Override
  public String getSender() {
    return this.noAuthenticationMail;
  }

  @Override
  public Optional<String> getUser() {
    return Optional.empty();
  }

  @Override
  public Optional<String> getSecret() {
    return Optional.empty();
  }
}
