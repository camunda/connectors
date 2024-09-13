/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.inbound.model;

import com.fasterxml.jackson.annotation.*;
import io.camunda.connector.email.config.Configuration;
import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public final class EmailListenerConfig {
  @NestedProperties(addNestedPath = false)
  @Valid
  ImapConfig imapConfig;

  @TemplateProperty(
      label = "Folder to listen",
      group = "listenerInfos",
      id = "data.folderToListen",
      description = "",
      optional = true,
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.folderToListen"))
  Object folderToListen;

  @TemplateProperty(
      label = "Sync strategy",
      description = "Chose the desired polling strategy",
      group = "listenerInfos",
      id = "data.initialPollingConfig",
      feel = Property.FeelMode.required,
      type = TemplateProperty.PropertyType.Dropdown,
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
      defaultValue = "UNSEEN",
      choices = {
        @TemplateProperty.DropdownPropertyChoice(label = "Unseen", value = "UNSEEN"),
        @TemplateProperty.DropdownPropertyChoice(
            label = "No initial sync. Only new mails",
            value = "NONE"),
        @TemplateProperty.DropdownPropertyChoice(label = "All", value = "ALL")
      },
      binding = @TemplateProperty.PropertyBinding(name = "data.initialPollingConfig"))
  @NotNull
  InitialPollingConfig initialPollingConfig;

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      visible = true,
      include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      property = "handlingStrategyDiscriminator")
  @JsonSubTypes(
      value = {
        @JsonSubTypes.Type(value = ReadHandlingStrategy.class, name = "readHandlingStrategy"),
        @JsonSubTypes.Type(value = NoHandlingStrategy.class, name = "noHandlingStrategy"),
        @JsonSubTypes.Type(value = DeleteHandlingStrategy.class, name = "deleteHandlingStrategy"),
        @JsonSubTypes.Type(value = MoveHandlingStrategy.class, name = "moveHandlingStrategy"),
      })
  @NestedProperties(addNestedPath = false)
  HandlingStrategy handlingStrategy;

  @JsonCreator
  public EmailListenerConfig(
      @JsonProperty("imapConfig") ImapConfig imapConfig,
      @JsonProperty("folderToListen") String folderToListen,
      @JsonProperty("initialPollingConfig") InitialPollingConfig initialPollingConfig,
      @JsonProperty("handlingStrategyDiscriminator") String handlingStrategyDiscriminator,
      @JsonProperty("handlingStrategy") HandlingStrategy handlingStrategy) {
    this.imapConfig = imapConfig;
    this.folderToListen = folderToListen;
    this.initialPollingConfig = initialPollingConfig;
    this.handlingStrategy =
        Objects.requireNonNullElseGet(
            handlingStrategy,
            () ->
                switch (handlingStrategyDiscriminator) {
                  case "readHandlingStrategy" -> new ReadHandlingStrategy();
                  case "noHandlingStrategy" -> new NoHandlingStrategy();
                  case "deleteHandlingStrategy" -> new DeleteHandlingStrategy();
                  default ->
                      throw new IllegalStateException(
                          "Unexpected value: " + handlingStrategyDiscriminator);
                });
  }

  public Configuration imapConfig() {
    return imapConfig;
  }

  public Object folderToListen() {
    return folderToListen;
  }

  public HandlingStrategy handlingStrategy() {
    return handlingStrategy;
  }

  public InitialPollingConfig initialPollingConfig() {
    return initialPollingConfig;
  }
}
