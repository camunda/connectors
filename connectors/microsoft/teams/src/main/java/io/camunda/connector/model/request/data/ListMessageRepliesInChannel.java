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
    label = "List message replies",
    id = MSTeamsMethodTypes.LIST_MESSAGE_REPLIES_IN_CHANNEL)
public record ListMessageRepliesInChannel(
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "listMessageRepliesInChannel.groupId",
            label = "Group ID",
            description = "The group ID for teams")
        String groupId,
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "listMessageRepliesInChannel.channelId",
            label = "Channel ID",
            description = "The channel ID")
        String channelId,
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "listMessageRepliesInChannel.messageId",
            label = "Message ID",
            description = "The message ID")
        String messageId)
    implements ChannelData {}
