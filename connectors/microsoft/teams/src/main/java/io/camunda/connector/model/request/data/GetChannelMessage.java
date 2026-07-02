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

@TemplateSubType(
    id = MSTeamsMethodTypes.GET_CHANNEL_MESSAGE,
    label = "Get channel message",
    description = "Retrieve a specific message from a Microsoft Teams channel",
    keywords = {
      "get channel message",
      "fetch message",
      "retrieve message",
      "read channel post",
      "message details"
    })
public record GetChannelMessage(
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "getChannelMessage.groupId",
            label = "Group ID",
            description = "The group ID for teams")
        String groupId,
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "getChannelMessage.channelId",
            label = "Channel ID",
            description = "The channel ID")
        String channelId,
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "getChannelMessage.messageId",
            label = "Message ID",
            description = "The message ID")
        String messageId)
    implements ChannelData {}
