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

@TemplateSubType(id = "readEmailImap", label = "Read an email using IMAP")
public final class ImapReadEmail implements ImapAction {
  @TemplateProperty(
      label = "Imap UID",
      group = "readEmailImap",
      id = "imapMessageIdRead",
      description = "",
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.messageId"))
  @Valid
  @NotNull
  String messageId;

  @TemplateProperty(
      label = "Folder",
      group = "readEmailImap",
      id = "readEmailFolder",
      description = "",
      optional = true,
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.readEmailFolder"))
  private String readEmailFolder;

  public @Valid @NotNull String getMessageId() {
    return messageId;
  }

  public void setMessageId(@Valid @NotNull String messageId) {
    this.messageId = messageId;
  }

  public String getReadEmailFolder() {
    return readEmailFolder;
  }

  public void setReadEmailFolder(String readEmailFolder) {
    this.readEmailFolder = readEmailFolder;
  }
}
