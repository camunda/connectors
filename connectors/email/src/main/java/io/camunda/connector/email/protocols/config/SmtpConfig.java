/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.protocols.config;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public final class SmtpConfig implements Configuration {

  @TemplateProperty(
      label = "SMTP Host",
      group = "protocol",
      id = "data.smtpHost",
      description = "",
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.smtpConfig.smtpHost"))
  @Valid
  @NotNull
  private String smtpHost;

  @TemplateProperty(
      label = "SMTP Port",
      group = "protocol",
      id = "data.smtpPort",
      description = "",
      defaultValue = "587",
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.smtpConfig.smtpPort"))
  @Valid
  @NotNull
  private Integer smtpPort = 587;

  @TemplateProperty(
      label = "SMTP Authentication",
      group = "protocol",
      id = "data.smtpAuth",
      description = "",
      type = TemplateProperty.PropertyType.Boolean,
      binding = @TemplateProperty.PropertyBinding(name = "data.smtpConfig.smtpAuth"))
  @Valid
  @NotNull
  private Boolean smtpAuth = true;

  @TemplateProperty(
      label = "SMTP TLS",
      group = "protocol",
      id = "data.smtpTLS",
      description = "",
      type = TemplateProperty.PropertyType.Boolean,
      binding = @TemplateProperty.PropertyBinding(name = "data.smtpConfig.smtpTLS"))
  @Valid
  @NotNull
  private Boolean smtpTLS = true;

  public @Valid @NotNull Integer getSmtpPort() {

    return smtpPort;
  }

  public void setSmtpPort(@Valid @NotNull Integer smtpPort) {
    this.smtpPort = smtpPort;
  }

  public @Valid @NotNull Boolean getSmtpAuth() {
    return smtpAuth;
  }

  public void setSmtpAuth(@Valid @NotNull Boolean smtpAuth) {
    this.smtpAuth = smtpAuth;
  }

  public @Valid @NotNull Boolean getSmtpTLS() {
    return smtpTLS;
  }

  public void setSmtpTLS(@Valid @NotNull Boolean smtpTLS) {
    this.smtpTLS = smtpTLS;
  }

  public @Valid @NotNull String getSmtpHost() {
    return smtpHost;
  }

  public void setSmtpHost(@Valid @NotNull String smtpHost) {
    this.smtpHost = smtpHost;
  }
}
