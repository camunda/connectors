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

@TemplateSubType(label = "Get message in chat", id = MSTeamsMethodTypes.GET_MESSAGE_IN_CHAT)
public record GetMessageInChat(
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "getMessageInChat.chatId",
            label = "Chat ID",
            description = "The chat ID")
        String chatId,
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "getMessageInChat.messageId",
            label = "Message ID",
            description = "The message ID")
        String messageId)
    implements ChatData {}
