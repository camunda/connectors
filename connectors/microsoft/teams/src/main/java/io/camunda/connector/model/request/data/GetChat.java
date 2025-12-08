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

@TemplateSubType(label = "Get chat by ID", id = MSTeamsMethodTypes.GET_CHAT)
public record GetChat(
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "getChat.chatId",
            label = "Chat ID",
            description = "The chat ID")
        String chatId,
    @TemplateProperty(
            group = "data",
            id = "getChat.expand",
            label = "Expand response",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "withoutExpand",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  value = "withoutExpand",
                  label = "Without expand"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "lastMessagePreview",
                  label = "With the last message preview"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "members",
                  label = "With chat members")
            },
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            description =
                "Choose expand type. <a href='https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/microsoft-teams/#expand-response'>Learn more about expanding chat response</a>")
        String expand)
    implements ChatData {}
