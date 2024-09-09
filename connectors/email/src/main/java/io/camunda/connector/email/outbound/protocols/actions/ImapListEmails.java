/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols.actions;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "listEmailsImap", label = "List emails using IMAP")
public record ImapListEmails(
    @TemplateProperty(
            label = "Max email to read",
            group = "listEmailsImap",
            id = "imapMaxToBeRead",
            defaultValue = "100",
            description = "",
            feel = Property.FeelMode.disabled,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.maxToBeRead"))
        @Valid
        @NotNull
        Integer maxToBeRead,
    @TemplateProperty(
            label = "Folder",
            group = "listEmailsImap",
            id = "imapListEmailsFolder",
            description = "",
            optional = true,
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.listEmailsFolder"))
        String listEmailsFolder,
    @TemplateProperty(
            label = "Sort emails by",
            description = "",
            id = "imapSortField",
            group = "listEmailsImap",
            feel = Property.FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            defaultValue = "RECEIVED_DATE",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  label = "Received Date",
                  value = "RECEIVED_DATE"),
              @TemplateProperty.DropdownPropertyChoice(label = "Sent Date", value = "SENT_DATE"),
              @TemplateProperty.DropdownPropertyChoice(label = "Size", value = "SIZE")
            },
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.sortField"))
        @NotNull
        SortFieldImap sortField,
    @TemplateProperty(
            label = "Sort order",
            description = "",
            group = "listEmailsImap",
            id = "imapSortOrder",
            feel = Property.FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            defaultValue = "ASC",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(label = "ASC", value = "ASC"),
              @TemplateProperty.DropdownPropertyChoice(label = "DESC", value = "DESC")
            },
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.sortOrder"))
        @NotNull
        SortOrder sortOrder)
    implements ImapAction {}
