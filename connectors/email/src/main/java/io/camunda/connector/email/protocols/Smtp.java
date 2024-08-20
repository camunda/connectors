/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.protocols;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.protocols.actions.smtp.SmtpAction;
import io.camunda.connector.email.protocols.actions.smtp.SmtpSendEmail;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Properties;

public final class Smtp implements Protocol {
  @TemplateProperty(
      label = "SMTP Host",
      group = "protocol",
      id = "data.smtpHost",
      description = "",
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.smtpHost"))
  @Valid
  @NotNull
  private String smtpHost;

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      property = "action")
  @JsonSubTypes(value = {@JsonSubTypes.Type(value = SmtpSendEmail.class, name = "sendEmailSmtp")})
  @Valid
  @NotNull
  @NestedProperties(addNestedPath = false)
  private SmtpAction smtpAction;

  @TemplateProperty(
      label = "SMTP Port",
      group = "protocol",
      id = "data.smtpPort",
      description = "",
      defaultValue = "587",
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.smtpPort"))
  @Valid
  @NotNull
  private Integer smtpPort = 587;

  @TemplateProperty(
      label = "SMTP Authentication",
      group = "protocol",
      id = "data.smtpAuth",
      description = "",
      type = TemplateProperty.PropertyType.Boolean,
      binding = @TemplateProperty.PropertyBinding(name = "data.smtpAuth"))
  @Valid
  @NotNull
  private Boolean smtpAuth = true;

  @TemplateProperty(
      label = "SMTP TLS",
      group = "protocol",
      id = "data.smtpTLS",
      description = "",
      type = TemplateProperty.PropertyType.Boolean,
      binding = @TemplateProperty.PropertyBinding(name = "data.smtpTLS"))
  @Valid
  @NotNull
  private Boolean smtpTLS = true;

  public @Valid @NotNull String getSmtpHost() {

    return smtpHost;
  }

  public void setSmtpHost(@Valid @NotNull String smtpHost) {
    this.smtpHost = smtpHost;
  }

  public @Valid @NotNull SmtpAction getSmtpAction() {
    return smtpAction;
  }

  public void setSmtpAction(@Valid @NotNull SmtpAction smtpAction) {
    this.smtpAction = smtpAction;
  }

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

  @Override
  public Object execute(Authentication authentication) {
    Properties properties = new Properties();
    properties.put("mail.smtp.host", this.smtpHost);
    properties.put("mail.smtp.port", this.smtpPort.toString());
    properties.put("mail.smtp.auth", this.smtpAuth);
    properties.put("mail.smtp.starttls.enable", this.smtpTLS);
    return null;
  }
}
