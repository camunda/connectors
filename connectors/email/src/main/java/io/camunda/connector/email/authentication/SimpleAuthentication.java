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

@TemplateSubType(id = "simple", label = "Simple")
public final class SimpleAuthentication implements Authentication {
  @TemplateProperty(
      group = "authentication",
      label = "Email address",
      description = "Provide email")
  @NotBlank
  private String simpleAuthenticationMail;

  @TemplateProperty(
      group = "authentication",
      label = "Email password",
      description = "Provide password")
  @NotBlank
  private String simpleAuthenticationPassword;

  public @NotBlank String getSimpleAuthenticationMail() {
    return simpleAuthenticationMail;
  }

  public void setSimpleAuthenticationMail(@NotBlank String simpleAuthenticationMail) {
    this.simpleAuthenticationMail = simpleAuthenticationMail;
  }

  public @NotBlank String getSimpleAuthenticationPassword() {
    return simpleAuthenticationPassword;
  }

  public void setSimpleAuthenticationPassword(@NotBlank String simpleAuthenticationPassword) {
    this.simpleAuthenticationPassword = simpleAuthenticationPassword;
  }

  @Override
  public String getAuthenticatedEmailAddress() {
    return this.simpleAuthenticationMail;
  }

  @Override
  public Optional<String> getUser() {
    return Optional.of(this.simpleAuthenticationMail);
  }

  @Override
  public Optional<String> getSecret() {
    return Optional.of(this.simpleAuthenticationPassword);
  }
}
