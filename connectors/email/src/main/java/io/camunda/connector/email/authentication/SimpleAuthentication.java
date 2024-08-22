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

@TemplateSubType(id = "simple", label = "Simple")
public final class SimpleAuthentication implements Authentication {
  @TemplateProperty(
      group = "authentication",
      label = "Email address",
      description = "Provide email")
  @NotBlank
  private String mail;

  @TemplateProperty(
      group = "authentication",
      label = "Email password",
      description = "Provide password")
  @NotBlank
  private String password;

  public @NotBlank String getMail() {
    return mail;
  }

  public void setMail(@NotBlank String mail) {
    this.mail = mail;
  }

  public @NotBlank String getPassword() {
    return password;
  }

  public void setPassword(@NotBlank String password) {
    this.password = password;
  }

  @Override
  public String getSender() {
    return this.mail;
  }

  @Override
  public String getSecret() {
    return this.password;
  }
}