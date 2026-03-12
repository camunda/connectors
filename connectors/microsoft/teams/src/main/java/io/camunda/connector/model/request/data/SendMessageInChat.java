/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.data;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.model.Attachment;
import io.camunda.connector.model.MSTeamsMethodTypes;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@TemplateSubType(label = "Send message in chat", id = MSTeamsMethodTypes.SEND_MESSAGE_IN_CHAT)
public record SendMessageInChat(
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "sendMessageInChat.chatId",
            label = "Chat ID",
            description = "The chat ID")
        String chatId,
    @TemplateProperty(
            group = "data",
            id = "sendMessageInChat.bodyType",
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
            id = "sendMessageInChat.content",
            type = TemplateProperty.PropertyType.Text,
            label = "Content",
            description = "Enter content")
        String content,
    @TemplateProperty(
            label = "Attachments",
            group = "data",
            id = "sendMessageInChat.attachments",
            feel = FeelMode.required,
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "data.sendMessageInChat.bodyType",
                    equals = "HTML"),
            description =
                "Optional list of attachments. Each item must have an 'id', 'contentType'"
                    + " (e.g. 'application/vnd.microsoft.card.adaptive') and 'content'"
                    + " (e.g. an Adaptive Card JSON payload). Attachment IDs must match"
                    + " <attachment id=\"...\"></attachment> tags in the message body"
                    + " (auto-appended if missing).")
        List<Attachment> attachments)
    implements ChatData {}
