/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.protocols;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.email.protocols.actions.*;
import io.camunda.connector.email.protocols.config.Pop3Config;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "pop3", label = "POP3")
public final class Pop3 implements Protocol {
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      property = "pop3ActionDiscriminator")
  @JsonSubTypes(
      value = {
        @JsonSubTypes.Type(value = Pop3ListEmails.class, name = "listEmailsPop3"),
        @JsonSubTypes.Type(value = Pop3ReadEmail.class, name = "readEmailPop3"),
        @JsonSubTypes.Type(value = Pop3DeleteEmail.class, name = "deleteEmailPop3")
      })
  @Valid
  @NotNull
  @NestedProperties(addNestedPath = false)
  private Pop3Action pop3Action;

  @Valid
  @NestedProperties(addNestedPath = false)
  private Pop3Config pop3Config;

  @Override
  public Action getProtocolAction() {
    return pop3Action;
  }

  public @Valid @NotNull Pop3Action getPop3Action() {
    return pop3Action;
  }

  public void setPop3Action(@Valid @NotNull Pop3Action pop3Action) {
    this.pop3Action = pop3Action;
  }

  public @Valid Pop3Config getPop3Config() {
    return pop3Config;
  }

  public void setPop3Config(@Valid Pop3Config pop3Config) {
    this.pop3Config = pop3Config;
  }
}
