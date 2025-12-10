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
import io.camunda.connector.model.OrderBy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(label = "List messages in chat", id = MSTeamsMethodTypes.LIST_MESSAGES_IN_CHAT)
public record ListMessagesInChat(
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "listMessagesInChat.chatId",
            label = "Chat ID",
            description = "The chat ID")
        String chatId,
    @TemplateProperty(
            group = "data",
            id = "listMessagesInChat.top",
            label = "Top",
            optional = true,
            description =
                "Controls the number of items per response. Maximum allowed $top value is 50")
        String top,
    @NotNull
        @TemplateProperty(
            group = "data",
            id = "listMessagesInChat.orderBy",
            label = "Order by",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "withoutOrdering",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  value = "withoutOrdering",
                  label = "Without ordering"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "lastModifiedDateTime",
                  label = "Last modified data time"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "createdDateTime",
                  label = "Created data time")
            },
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        OrderBy orderBy,
    @TemplateProperty(
            group = "data",
            id = "listMessagesInChat.filter",
            label = "Filter",
            description =
                "Sets the date range filter for the lastModifiedDateTime and createdDateTime properties. <a href='https://learn.microsoft.com/en-us/graph/filter-query-parameter'>Learn more about filtering</a>")
        String filter)
    implements ChatData {}
