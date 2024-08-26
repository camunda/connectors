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
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "deleteEmailPop3", label = "Delete Email using POP3")
public final class Pop3DeleteEmail implements Pop3Action {
  @TemplateProperty(
      label = "UIDL of email to delete",
      group = "deleteEmailPop3",
      id = "pop3uidlDelete",
      description = "",
      feel = Property.FeelMode.required,
      binding = @TemplateProperty.PropertyBinding(name = "data.pop3Action.uidlDelete"))
  @NotNull
  String uidlDelete;

  public @NotNull String getUidlDelete() {
    return uidlDelete;
  }

  public void setUidlDelete(@NotNull String uidlDelete) {
    this.uidlDelete = uidlDelete;
  }
}
