/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.data;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.model.Attachment;
import io.camunda.connector.model.MSTeamsMethodTypes;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@TemplateSubType(label = "Send message to channel", id = MSTeamsMethodTypes.SEND_MESSAGE_TO_CHANNEL)
public record SendMessageToChannel(
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "sendMessageToChannel.groupId",
            label = "Group ID",
            description = "The group ID for teams")
        String groupId,
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "sendMessageToChannel.channelId",
            label = "Channel ID",
            description = "The channel ID")
        String channelId,
    @TemplateProperty(
            group = "data",
            id = "sendMessageToChannel.bodyType",
            label = "Content type",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "TEXT",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "TEXT", label = "Text"),
              @TemplateProperty.DropdownPropertyChoice(value = "HTML", label = "HTML")
            },
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            description = "The type of the content. Possible values are text and html")
        String bodyType,
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "sendMessageToChannel.content",
            label = "Content",
            type = TemplateProperty.PropertyType.Text,
            description = "Enter content")
        String content,
    @TemplateProperty(
            label = "documents",
            group = "data",
            id = "sendMessageToChannel.documents",
            feel = FeelMode.required,
            optional = true)
        List<Document> documents,
    @TemplateProperty(
            label = "Attachments",
            group = "data",
            id = "sendMessageToChannel.attachments",
            feel = FeelMode.required,
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "data.sendMessageToChannel.bodyType",
                    equals = "HTML"),
            description =
                "Optional list of attachments. Each item must have an 'id', 'contentType'"
                    + " (e.g. 'application/vnd.microsoft.card.adaptive') and 'content'"
                    + " (e.g. an Adaptive Card JSON payload). Attachment IDs must match"
                    + " <attachment id=\"...\"></attachment> tags in the message body"
                    + " (auto-appended if missing).")
        List<Attachment> attachments)
    implements ChannelData {}
