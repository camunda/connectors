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

@TemplateSubType(id = "searchEmailsImap", label = "Search emails")
public record ImapSearchEmails(
    @TemplateProperty(
            label = "Search criteria",
            group = "searchEmailsImap",
            id = "searchStringEmailImap",
            tooltip =
                "Define the search criteria using supported keywords and syntax to filter emails.",
            description =
                "Refer to our detailed documentation for full search syntax and examples: [Email Documentation](https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/email/).",
            type = TemplateProperty.PropertyType.Text,
            feel = FeelMode.required,
            optional = true,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.criteria"))
        Object criteria,
    @TemplateProperty(
            label = "Folder",
            group = "searchEmailsImap",
            id = "searchEmailFolder",
            tooltip =
                "Specify the folder in which to conduct the email search. If left blank, the search will default to the 'INBOX' folder. You may also specify subfolders using a dot-separated path (e.g., 'INBOX.Archives').",
            optional = true,
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.searchEmailFolder"))
        String searchEmailFolder)
    implements ImapAction {}
