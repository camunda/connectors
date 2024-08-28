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
import java.util.Optional;

@TemplateSubType(id = "listEmailImap", label = "List emails using IMAP")
public final class ImapListEmails implements ImapAction {
  @TemplateProperty(
      label = "Max email to read",
      group = "listEmailImap",
      id = "imapMaxToBeRead",
      defaultValue = "100",
      description = "",
      feel = Property.FeelMode.disabled,
      binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.maxToBeRead"))
  @Valid
  @NotNull
  Integer maxToBeRead;

  @TemplateProperty(
      label = "Folder",
      group = "listEmailImap",
      id = "imapListEmailsFolder",
      description = "",
      optional = true,
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.listEmailsFolder"))
  private String listEmailsFolder;

  @TemplateProperty(
      label = "Sort emails by",
      description = "",
      group = "listEmailImap",
      feel = Property.FeelMode.required,
      type = TemplateProperty.PropertyType.Dropdown,
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
      defaultValue = "RECEIVED_DATE",
      choices = {
        @TemplateProperty.DropdownPropertyChoice(label = "Received Date", value = "RECEIVED_DATE"),
        @TemplateProperty.DropdownPropertyChoice(label = "Sent Date", value = "SENT_DATE"),
        @TemplateProperty.DropdownPropertyChoice(label = "Size", value = "SIZE")
      },
      binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.sortField"))
  @NotNull
  private SortField sortField;

  @TemplateProperty(
      label = "Sort order",
      description = "",
      group = "listEmailImap",
      feel = Property.FeelMode.required,
      type = TemplateProperty.PropertyType.Dropdown,
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
      defaultValue = "ASC",
      choices = {
        @TemplateProperty.DropdownPropertyChoice(label = "ASC", value = "ASC"),
        @TemplateProperty.DropdownPropertyChoice(label = "DESC", value = "DESC")
      },
      binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.sortOrder"))
  @NotNull
  private SortOrder sortOrder;

  public @Valid @NotNull Integer getMaxToBeRead() {
    return maxToBeRead;
  }

  public void setMaxToBeRead(@Valid @NotNull Integer maxToBeRead) {
    this.maxToBeRead = maxToBeRead;
  }

  public Optional<String> getListEmailsFolder() {
    return Optional.ofNullable(listEmailsFolder);
  }

  public void setListEmailsFolder(String listEmailsFolder) {
    this.listEmailsFolder = listEmailsFolder;
  }

  public @NotNull SortField getSortField() {
    return sortField;
  }

  public void setSortField(@NotNull SortField sortField) {
    this.sortField = sortField;
  }

  public @NotNull SortOrder getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(@NotNull SortOrder sortOrder) {
    this.sortOrder = sortOrder;
  }
}
