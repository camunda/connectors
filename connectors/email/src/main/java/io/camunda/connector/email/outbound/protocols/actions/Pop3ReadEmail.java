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

@TemplateSubType(id = "readEmailPop3", label = "Read Email using POP3")
public final class Pop3ReadEmail implements Pop3Action {
  @TemplateProperty(
      label = "UIDL of email to read",
      group = "readEmailPop3",
      id = "pop3MessageIdRead",
      description = "",
      feel = Property.FeelMode.required,
      binding = @TemplateProperty.PropertyBinding(name = "data.pop3Action.messageId"))
  @Valid
  @NotNull
  private String messageId;

  @TemplateProperty(
      label = "Delete after reading",
      group = "readEmailPop3",
      type = TemplateProperty.PropertyType.Boolean,
      defaultValue = "false",
      defaultValueType = TemplateProperty.DefaultValueType.Boolean,
      binding = @TemplateProperty.PropertyBinding(name = "data.pop3Action.deleteOnRead"))
  private boolean deleteOnRead = false;

  public boolean isDeleteOnRead() {
    return deleteOnRead;
  }

  public void setDeleteOnRead(boolean deleteOnRead) {
    this.deleteOnRead = deleteOnRead;
  }

  public @Valid @NotNull String getMessageId() {
    return messageId;
  }

  public void setMessageId(@Valid @NotNull String messageId) {
    this.messageId = messageId;
  }
}
