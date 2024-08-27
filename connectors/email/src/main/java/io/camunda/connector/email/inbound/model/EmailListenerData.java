/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.inbound.model;

import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class EmailListenerData {

  @Valid
  @NestedProperties(addNestedPath = false)
  private ImapConfig imapConfig;

  @TemplateProperty(
      label = "Folder to listen",
      group = "listenerInfos",
      id = "data.folderToListen",
      description = "",
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.folderToListen"))
  private @Valid @NotNull Object folderToListen;

  @TemplateProperty(
      label = "Trigger on new message",
      group = "listenerInfos",
      type = TemplateProperty.PropertyType.Boolean,
      defaultValue = "true",
      defaultValueType = TemplateProperty.DefaultValueType.Boolean,
      binding = @TemplateProperty.PropertyBinding(name = "data.triggerAdded"))
  private boolean triggerAdded;

  @TemplateProperty(
      label = "Trigger on deleted message",
      group = "listenerInfos",
      type = TemplateProperty.PropertyType.Boolean,
      defaultValue = "false",
      defaultValueType = TemplateProperty.DefaultValueType.Boolean,
      binding = @TemplateProperty.PropertyBinding(name = "data.triggerRemoved"))
  private boolean triggerRemoved;

  @TemplateProperty(
      label = "Mark as read",
      group = "listenerInfos",
      type = TemplateProperty.PropertyType.Boolean,
      defaultValue = "false",
      defaultValueType = TemplateProperty.DefaultValueType.Boolean,
      binding = @TemplateProperty.PropertyBinding(name = "data.markAsRead"))
  private boolean markAsRead;

  public @Valid ImapConfig getImapConfig() {
    return imapConfig;
  }

  public void setImapConfig(@Valid ImapConfig imapConfig) {
    this.imapConfig = imapConfig;
  }

  public @Valid @NotNull Object getFolderToListen() {
    return folderToListen;
  }

  public void setFolderToListen(@Valid @NotNull String folderToListen) {
    this.folderToListen = folderToListen;
  }

  public boolean isTriggerAdded() {
    return triggerAdded;
  }

  public void setTriggerAdded(boolean triggerAdded) {
    this.triggerAdded = triggerAdded;
  }

  public boolean isTriggerRemoved() {
    return triggerRemoved;
  }

  public void setTriggerRemoved(boolean triggerRemoved) {
    this.triggerRemoved = triggerRemoved;
  }

  public boolean isMarkAsRead() {
    return markAsRead;
  }

  public void setMarkAsRead(boolean markAsRead) {
    this.markAsRead = markAsRead;
  }
}
