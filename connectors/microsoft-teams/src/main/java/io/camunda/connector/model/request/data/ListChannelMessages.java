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

@TemplateSubType(label = "List channel messages", id = MSTeamsMethodTypes.LIST_CHANNEL_MESSAGES)
public record ListChannelMessages(
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "listChannelMessage.groupId",
            label = "Group ID",
            description = "The group ID for teams")
        String groupId,
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "listChannelMessage.channelId",
            label = "Channel ID",
            description = "The channel ID")
        String channelId,
    @TemplateProperty(
            group = "data",
            id = "listChannelMessage.top",
            label = "Top",
            optional = true,
            description = "Controls the number of items per response")
        String top,
    @TemplateProperty(
            group = "data",
            id = "listChannelMessage.isExpand",
            label = "With replies",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "false",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "false", label = "False"),
              @TemplateProperty.DropdownPropertyChoice(value = "true", label = "True")
            },
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            description = "Return message replies")
        String isExpand)
    implements ChannelData {
  public static final String EXPAND_VALUE = "replies";
}
