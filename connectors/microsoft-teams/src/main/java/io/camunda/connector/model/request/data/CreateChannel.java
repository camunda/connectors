/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.data;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.model.MSTeamsMethodTypes;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(label = "Create channel", id = MSTeamsMethodTypes.CREATE_CHANNEL)
public record CreateChannel(
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "createChannel.groupId",
            label = "Group ID",
            description = "The group ID for teams")
        String groupId,
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "createChannel.name",
            label = "Display name",
            description = "Enter name of a channel")
        String name,
    @TemplateProperty(
            group = "data",
            id = "createChannel.description",
            label = "Description",
            optional = true,
            type = TemplateProperty.PropertyType.Text,
            description = "Enter description")
        String description,
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "createChannel.channelType",
            label = "Channel membership type",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "standard",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "standard", label = "STANDARD"),
              @TemplateProperty.DropdownPropertyChoice(value = "private", label = "PRIVATE"),
              @TemplateProperty.DropdownPropertyChoice(value = "shared", label = "SHARED")
            },
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            description = "Choose type")
        String channelType,
    @TemplateProperty(
            group = "data",
            id = "createChannel.owner",
            label = "Owner",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "data.createChannel.channelType",
                    oneOf = {"private", "shared"}),
            description = "Enter ID or principal name of a user")
        String owner)
    implements ChannelData {}
