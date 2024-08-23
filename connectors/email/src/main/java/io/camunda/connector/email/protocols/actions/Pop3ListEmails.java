/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.protocols.actions;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "listEmailsPop3", label = "List Emails using POP3")
public final class Pop3ListEmails implements Pop3Action {
  @TemplateProperty(
      label = "Max email to read",
      group = "listEmailsPop3",
      id = "pop3maxToBeRead",
      defaultValue = "100",
      description = "",
      feel = Property.FeelMode.disabled,
      binding = @TemplateProperty.PropertyBinding(name = "data.pop3Action.maxToBeRead"))
  @Valid
  @NotNull
  Integer maxToBeRead;

  public @Valid @NotNull Integer getMaxToBeRead() {
    return maxToBeRead;
  }

  public void setMaxToBeRead(@Valid @NotNull Integer maxToBeRead) {
    this.maxToBeRead = maxToBeRead;
  }
}
