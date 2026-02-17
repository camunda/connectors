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

@TemplateSubType(id = "listEmailsImap", label = "List emails")
public record ImapListEmails(
    @TemplateProperty(
            label = "Maximum number of emails to be read",
            group = "listEmailsImap",
            id = "imapMaxToBeRead",
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            defaultValue = "100",
            tooltip =
                "Enter the maximum number of emails to be read from the specified folder. This limits the number of emails fetched to avoid performance issues with large mailboxes. The default value is set to 100.",
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.maxToBeRead"))
        @Valid
        @NotNull
        Integer maxToBeRead,
    @TemplateProperty(
            label = "Folder",
            group = "listEmailsImap",
            id = "imapListEmailsFolder",
            tooltip =
                "Specify the folder from which you want to list emails (e.g., 'INBOX', 'Sent', 'Drafts'). If left blank, emails will be listed from the default 'INBOX' folder.",
            optional = true,
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.listEmailsFolder"))
        String listEmailsFolder,
    @TemplateProperty(
            label = "Sort emails by",
            tooltip =
                "Choose the criterion by which the listed emails should be sorted. The default sorting is by 'Received Date'.",
            id = "imapSortField",
            group = "listEmailsImap",
            feel = FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            defaultValue = "RECEIVED_DATE",
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.sortField"))
        @NotNull
        SortFieldImap sortField,
    @TemplateProperty(
            label = "Sort order",
            tooltip =
                "Select the sort order for the emails. Choose 'ASC' for ascending order or 'DESC' for descending order. Ascending order will list older emails first, while descending order will list newer emails first. The default sort order is 'ASC'.",
            group = "listEmailsImap",
            id = "imapSortOrder",
            feel = FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            defaultValue = "ASC",
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.sortOrder"))
        @NotNull
        SortOrder sortOrder)
    implements ImapAction {}
