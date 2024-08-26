/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.email.outbound.protocols.actions.Action;
import io.camunda.connector.email.outbound.protocols.actions.SmtpAction;
import io.camunda.connector.email.outbound.protocols.actions.SmtpSendEmail;
import io.camunda.connector.email.outbound.protocols.config.SmtpConfig;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "smtp", label = "SMTP")
public final class Smtp implements Protocol {

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      property = "smtpActionDiscriminator")
  @JsonSubTypes(value = {@JsonSubTypes.Type(value = SmtpSendEmail.class, name = "sendEmailSmtp")})
  @Valid
  @NotNull
  @NestedProperties(addNestedPath = false)
  private SmtpAction smtpAction;

  @Valid
  @NestedProperties(addNestedPath = false)
  private SmtpConfig smtpConfig;

  public @Valid @NotNull SmtpAction getSmtpAction() {
    return smtpAction;
  }

  public void setSmtpAction(@Valid @NotNull SmtpAction smtpAction) {
    this.smtpAction = smtpAction;
  }

  public @Valid SmtpConfig getSmtpConfig() {
    return smtpConfig;
  }

  public void setSmtpConfig(@Valid SmtpConfig smtpConfig) {
    this.smtpConfig = smtpConfig;
  }

  @Override
  public Action getProtocolAction() {
    return smtpAction;
  }
}
