/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols.actions;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "listEmailsPop3", label = "List Emails")
public record Pop3ListEmails(
    @TemplateProperty(
            label = "Maximum number of emails to be read",
            group = "listEmailsPop3",
            id = "pop3maxToBeRead",
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            defaultValue = "100",
            tooltip =
                "Enter the maximum number of emails to be read from the specified folder. This limits the number of emails fetched to avoid performance issues with large mailboxes. The default value is set to 100.",
            binding = @TemplateProperty.PropertyBinding(name = "data.pop3Action.maxToBeRead"))
        @Valid
        @NotNull
        Integer maxToBeRead,
    @TemplateProperty(
            label = "Sort emails by",
            tooltip =
                "Choose the criterion by which the listed emails should be sorted. The default sorting is by 'Sent Date'.",
            group = "listEmailsPop3",
            id = "pop3SortField",
            feel = FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            defaultValue = "SENT_DATE",
            binding = @TemplateProperty.PropertyBinding(name = "data.pop3Action.sortField"))
        @NotNull
        SortFieldPop3 sortField,
    @TemplateProperty(
            label = "Sort order",
            tooltip =
                "Select the sort order for the emails. Choose 'ASC' for ascending order or 'DESC' for descending order. Ascending order will list older emails first, while descending order will list newer emails first. The default sort order is 'ASC'.",
            id = "pop3SortOrder",
            group = "listEmailsPop3",
            feel = FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            defaultValue = "ASC",
            binding = @TemplateProperty.PropertyBinding(name = "data.pop3Action.sortOrder"))
        @NotNull
        SortOrder sortOrder)
    implements Pop3Action {}
