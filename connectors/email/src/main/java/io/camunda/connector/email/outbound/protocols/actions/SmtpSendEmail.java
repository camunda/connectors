/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols.actions;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "sendEmailSmtp", label = "Send Email using SMTP")
public final class SmtpSendEmail implements SmtpAction {
  @TemplateProperty(
      label = "TO",
      group = "sendEmailSmtp",
      id = "smtpTo",
      description =
          "Comma-separated list of email, e.g., 'email1@domain.com,email2@domain.com' or '=[ \"email1@domain.com\", \"email2@domain.com\"]'",
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.to"))
  @Valid
  @NotNull
  private Object to;

  @TemplateProperty(
      label = "CC",
      group = "sendEmailSmtp",
      id = "smtpCc",
      description =
          "Comma-separated list of email, e.g., 'email1@domain.com,email2@domain.com' or '=[ \"email1@domain.com\", \"email2@domain.com\"]'",
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.cc"),
      optional = true)
  @Valid
  private Object cc;

  @TemplateProperty(
      label = "CCI",
      group = "sendEmailSmtp",
      id = "smtpCci",
      description =
          "Comma-separated list of email, e.g., 'email1@domain.com,email2@domain.com' or '=[ \"email1@domain.com\", \"email2@domain.com\"]'",
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.cci"),
      optional = true)
  @Valid
  private Object cci;

  @TemplateProperty(
      label = "subject",
      group = "sendEmailSmtp",
      id = "smtpSubject",
      type = TemplateProperty.PropertyType.String,
      description = "Subject of the mail",
      binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.subject"),
      feel = Property.FeelMode.optional)
  @Valid
  @NotNull
  private String subject;

  @TemplateProperty(
      label = "Body",
      group = "sendEmailSmtp",
      id = "smtpBody",
      type = TemplateProperty.PropertyType.Text,
      description = "Body of the mail",
      binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.body"),
      feel = Property.FeelMode.optional)
  @Valid
  @NotNull
  private String body;

  public @Valid @NotNull Object getTo() {
    return to;
  }

  public void setTo(@Valid @NotNull Object to) {
    this.to = to;
  }

  public @Valid Object getCc() {
    return cc;
  }

  public void setCc(@Valid Object cc) {
    this.cc = cc;
  }

  public @Valid Object getCci() {
    return cci;
  }

  public void setCci(@Valid Object cci) {
    this.cci = cci;
  }

  public @Valid @NotNull String getSubject() {
    return subject;
  }

  public void setSubject(@Valid @NotNull String subject) {
    this.subject = subject;
  }

  public @Valid @NotNull String getBody() {
    return body;
  }

  public void setBody(@Valid @NotNull String body) {
    this.body = body;
  }
}
