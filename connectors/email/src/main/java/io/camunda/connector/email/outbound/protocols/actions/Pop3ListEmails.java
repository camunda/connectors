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

  @TemplateProperty(
      label = "Sort emails by",
      description = "",
      group = "listEmailsPop3",
      id = "pop3SortField",
      feel = Property.FeelMode.required,
      type = TemplateProperty.PropertyType.Dropdown,
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
      defaultValue = "RECEIVED_DATE",
      choices = {
        @TemplateProperty.DropdownPropertyChoice(label = "Received Date", value = "RECEIVED_DATE"),
        @TemplateProperty.DropdownPropertyChoice(label = "Sent Date", value = "SENT_DATE"),
        @TemplateProperty.DropdownPropertyChoice(label = "Size", value = "SIZE")
      },
      binding = @TemplateProperty.PropertyBinding(name = "data.pop3Action.sortField"))
  @NotNull
  private SortField sortField;

  @TemplateProperty(
      label = "Sort order",
      description = "",
      id = "pop3SortOrder",
      group = "listEmailsPop3",
      feel = Property.FeelMode.required,
      type = TemplateProperty.PropertyType.Dropdown,
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
      defaultValue = "ASC",
      choices = {
        @TemplateProperty.DropdownPropertyChoice(label = "ASC", value = "ASC"),
        @TemplateProperty.DropdownPropertyChoice(label = "DESC", value = "DESC")
      },
      binding = @TemplateProperty.PropertyBinding(name = "data.pop3Action.sortOrder"))
  @NotNull
  private SortOrder sortOrder;

  public @NotNull SortOrder getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(@NotNull SortOrder sortOrder) {
    this.sortOrder = sortOrder;
  }

  public @NotNull SortField getSortField() {
    return sortField;
  }

  public void setSortField(@NotNull SortField sortField) {
    this.sortField = sortField;
  }

  public @Valid @NotNull Integer getMaxToBeRead() {
    return maxToBeRead;
  }

  public void setMaxToBeRead(@Valid @NotNull Integer maxToBeRead) {
    this.maxToBeRead = maxToBeRead;
  }
}
