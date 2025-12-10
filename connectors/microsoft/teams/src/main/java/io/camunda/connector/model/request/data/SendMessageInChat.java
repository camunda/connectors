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

@TemplateSubType(label = "Send message in chat", id = MSTeamsMethodTypes.SEND_MESSAGE_IN_CHAT)
public record SendMessageInChat(
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "sendMessageInChat.chatId",
            label = "Chat ID",
            description = "The chat ID")
        String chatId,
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "sendMessageInChat.content",
            type = TemplateProperty.PropertyType.Text,
            label = "Content",
            description = "Enter content")
        String content,
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
        String bodyType)
    implements ChatData {}
