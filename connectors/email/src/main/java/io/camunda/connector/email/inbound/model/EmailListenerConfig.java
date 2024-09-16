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

public record EmailListenerConfig(
    @NestedProperties(addNestedPath = false) @Valid ImapConfig imapConfig,
    @TemplateProperty(
            label = "Folder to listen",
            group = "listenerInfos",
            id = "data.folderToListen",
            description = "",
            optional = true,
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.folderToListen"))
        Object folderToListen,
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
        InitialPollingConfig initialPollingConfig,
    @TemplateProperty(
            label = "Handling strategy",
            description = "Chose the desired handling strategy",
            group = "listenerInfos",
            id = "data.handlingStrategy",
            feel = Property.FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            defaultValue = "READ",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(label = "Read", value = "READ"),
              @TemplateProperty.DropdownPropertyChoice(label = "None", value = "NO_HANDLING"),
              @TemplateProperty.DropdownPropertyChoice(label = "Delete", value = "DELETE"),
              @TemplateProperty.DropdownPropertyChoice(label = "Move", value = "MOVE")
            },
            binding = @TemplateProperty.PropertyBinding(name = "data.handlingStrategy"))
        @NotNull
        HandlingStrategy handlingStrategy,
    @TemplateProperty(
            label = "Choose the target folder",
            description = "Chose the target folder",
            group = "listenerInfos",
            id = "data.targetFolder",
            binding = @TemplateProperty.PropertyBinding(name = "data.targetFolder"),
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "data.handlingStrategy",
                    equals = "MOVE"))
        String targetFolder) {}
