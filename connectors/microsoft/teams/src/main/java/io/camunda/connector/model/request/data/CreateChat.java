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
import io.camunda.connector.model.MSTeamsMethodTypes;
import io.camunda.connector.model.Member;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@TemplateSubType(label = "Create a new chat", id = MSTeamsMethodTypes.CREATE_CHAT)
public record CreateChat(
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "createChat.chatType",
            label = "Chat type",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "one_on_one",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "one_on_one", label = "One on one"),
              @TemplateProperty.DropdownPropertyChoice(value = "group", label = "Group")
            },
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            description = "The type of a new chat")
        String chatType,
    @TemplateProperty(
            group = "data",
            id = "createChat.topic",
            label = "Topic",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "data.createChat.chatType",
                    equals = "group"),
            description = "Set topic of chat (optional)")
        String topic,
    @NotNull
        @TemplateProperty(
            group = "data",
            id = "createChat.members",
            label = "Members",
            feel = FeelMode.required,
            description =
                "Set array members of chat. <a href='https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/microsoft-teams/#members-property'>Learn more about the required format</a>")
        List<Member> members)
    implements ChatData {}
