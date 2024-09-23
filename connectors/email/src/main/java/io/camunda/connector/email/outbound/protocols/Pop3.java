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
import io.camunda.connector.email.config.Pop3Config;
import io.camunda.connector.email.outbound.protocols.actions.*;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "pop3", label = "POP3")
public record Pop3(
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
            property = "pop3ActionDiscriminator")
        @JsonSubTypes(
            value = {
              @JsonSubTypes.Type(value = Pop3ListEmails.class, name = "listEmailsPop3"),
              @JsonSubTypes.Type(value = Pop3ReadEmail.class, name = "readEmailPop3"),
              @JsonSubTypes.Type(value = Pop3DeleteEmail.class, name = "deleteEmailPop3"),
              @JsonSubTypes.Type(value = Pop3SearchEmails.class, name = "searchEmailsPop3")
            })
        @NestedProperties(addNestedPath = false)
        @Valid
        @NotNull
        Pop3Action pop3Action,
    @NestedProperties(addNestedPath = false) @Valid Pop3Config pop3Config)
    implements Protocol {
  @Override
  public Action getProtocolAction() {
    return pop3Action;
  }

  @Override
  public Configuration getConfiguration() {
    return pop3Config;
  }
}
