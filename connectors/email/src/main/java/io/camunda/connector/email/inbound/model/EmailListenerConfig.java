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
            tooltip =
                "Enter the names of the folders you wish to monitor, separated by commas, for new emails or changes (e.g., 'INBOX, Sent, Archive'). If left blank, the listener will default to monitoring the 'INBOX' folder.",
            optional = true,
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.folderToListen"))
        Object folderToListen,
    @TemplateProperty(
            label = "Sync strategy",
            tooltip = "Chose the desired polling strategy",
            group = "listenerInfos",
            id = "data.initialPollingConfig",
            feel = Property.FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            defaultValue = "UNSEEN",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  label = "Unseen emails will be sync",
                  value = "UNSEEN"),
              @TemplateProperty.DropdownPropertyChoice(
                  label = "No initial sync. Only new emails",
                  value = "NONE"),
              @TemplateProperty.DropdownPropertyChoice(
                  label = "All emails will be sync",
                  value = "ALL")
            },
            binding = @TemplateProperty.PropertyBinding(name = "data.initialPollingConfig"))
        @NotNull
        InitialPollingConfig initialPollingConfig,
    @TemplateProperty(
            label = "Handling strategy",
            tooltip = "Chose the desired handling strategy",
            group = "listenerInfos",
            id = "data.handlingStrategy",
            feel = Property.FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            defaultValue = "READ",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  label = "Mark as read after processing",
                  value = "READ"),
              @TemplateProperty.DropdownPropertyChoice(label = "Do nothing", value = "NO_HANDLING"),
              @TemplateProperty.DropdownPropertyChoice(
                  label = "Delete after processing",
                  value = "DELETE"),
              @TemplateProperty.DropdownPropertyChoice(
                  label = "Move to another folder after processing",
                  value = "MOVE")
            },
            binding = @TemplateProperty.PropertyBinding(name = "data.handlingStrategy"))
        @NotNull
        HandlingStrategy handlingStrategy,
    @TemplateProperty(
            label = "Choose the target folder",
            tooltip =
                "Specify the destination folder to which the emails will be moved. To create a new folder or a hierarchy of folders, use a dot-separated path (e.g., 'Archive' or 'Projects.2023.January'). If any part of the path does not exist, it will be created automatically.",
            group = "listenerInfos",
            id = "data.targetFolder",
            binding = @TemplateProperty.PropertyBinding(name = "data.targetFolder"),
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "data.handlingStrategy",
                    equals = "MOVE"))
        String targetFolder) {}
