/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@JsonIgnoreProperties(ignoreUnknown = true)
@TemplateDiscriminatorProperty(
    name = "method",
    id = "chatMethod",
    group = "operation",
    defaultValue = "sendMessageToChat",
    label = "Method",
    description = "Select method for chat interaction")
@TemplateSubType(label = "Chat", id = "chat")
public sealed interface ChatData extends MSTeamsRequestData
    permits CreateChat,
        GetChat,
        ListChats,
        ListChatMembers,
        SendMessageInChat,
        ListMessagesInChat,
        GetMessageInChat {}
