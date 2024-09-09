/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.email.config.Configuration;
import io.camunda.connector.email.config.SmtpConfig;
import io.camunda.connector.email.outbound.protocols.actions.Action;
import io.camunda.connector.email.outbound.protocols.actions.SmtpAction;
import io.camunda.connector.email.outbound.protocols.actions.SmtpSendEmail;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "smtp", label = "SMTP")
public record Smtp(
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
            property = "smtpActionDiscriminator")
        @JsonSubTypes(
            value = {@JsonSubTypes.Type(value = SmtpSendEmail.class, name = "sendEmailSmtp")})
        @NestedProperties(addNestedPath = false)
        @Valid
        @NotNull
        SmtpAction smtpAction,
    @NestedProperties(addNestedPath = false) @Valid SmtpConfig smtpConfig)
    implements Protocol {

  @Override
  public @Valid @NotNull SmtpAction smtpAction() {
    return smtpAction;
  }

  @Override
  public Action getProtocolAction() {
    return smtpAction;
  }

  @Override
  public Configuration getConfiguration() {
    return smtpConfig;
  }
}
