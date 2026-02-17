/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.inbound.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "unseenPollingConfig", label = "Poll unseen emails")
public record PollUnseen(
    @TemplateProperty(
            label = "Handling strategy",
            tooltip = "Chose the desired handling strategy",
            group = "unseenPollingConfig",
            id = "data.pollingConfig.unseenHandlingStrategy",
            feel = FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            defaultValue = "READ",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  label = "Mark as read after processing",
                  value = "READ"),
              @TemplateProperty.DropdownPropertyChoice(
                  label = "Delete after processing",
                  value = "DELETE"),
              @TemplateProperty.DropdownPropertyChoice(
                  label = "Move to another folder after processing",
                  value = "MOVE")
            },
            binding =
                @TemplateProperty.PropertyBinding(name = "data.pollingConfig.handlingStrategy"))
        @NotNull
        HandlingStrategy handlingStrategy,
    @TemplateProperty(
            label = "Choose the target folder",
            tooltip =
                "Specify the destination folder to which the emails will be moved. To create a new folder or a hierarchy of folders, use a dot-separated path (e.g., 'Archive' or 'Projects.2023.January'). If any part of the path does not exist, it will be created automatically.",
            group = "unseenPollingConfig",
            id = "unseenTargetFolder",
            binding = @TemplateProperty.PropertyBinding(name = "data.pollingConfig.targetFolder"),
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "data.pollingConfig.unseenHandlingStrategy",
                    equals = "MOVE"))
        String targetFolder)
    implements PollingConfig {}
