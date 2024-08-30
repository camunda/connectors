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
import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.email.outbound.protocols.actions.*;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "imap", label = "IMAP")
public final class Imap implements Protocol {
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      property = "imapActionDiscriminator")
  @JsonSubTypes(
      value = {
        @JsonSubTypes.Type(value = ImapListEmails.class, name = "listEmailsImap"),
        @JsonSubTypes.Type(value = ImapSearchEmails.class, name = "searchEmailsImap"),
        @JsonSubTypes.Type(value = ImapMoveEmail.class, name = "moveEmailImap"),
        @JsonSubTypes.Type(value = ImapReadEmail.class, name = "readEmailImap"),
        @JsonSubTypes.Type(value = ImapDeleteEmail.class, name = "deleteEmailImap")
      })
  @Valid
  @NotNull
  @NestedProperties(addNestedPath = false)
  private ImapAction imapAction;

  @Valid
  @NestedProperties(addNestedPath = false)
  private ImapConfig imapConfig;

  @Override
  public Action getProtocolAction() {
    return imapAction;
  }

  @Override
  public Configuration getConfiguration() {
    return imapConfig;
  }

  public @Valid @NotNull ImapAction getImapAction() {
    return imapAction;
  }

  public void setImapAction(@Valid @NotNull ImapAction imapAction) {
    this.imapAction = imapAction;
  }

  public @Valid ImapConfig getImapConfig() {
    return imapConfig;
  }

  public void setImapConfig(@Valid ImapConfig imapConfig) {
    this.imapConfig = imapConfig;
  }
}
