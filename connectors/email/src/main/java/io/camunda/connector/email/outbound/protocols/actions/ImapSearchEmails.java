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

@TemplateSubType(id = "searchEmailsImap", label = "Search an email using IMAP")
public record ImapSearchEmails(
    @TemplateProperty(
            label = "Search criteria",
            group = "searchEmailsImap",
            id = "searchStringEmailImap",
            description = "",
            type = TemplateProperty.PropertyType.Text,
            feel = Property.FeelMode.required,
            optional = true,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.criteria"))
        Object criteria,
    @TemplateProperty(
            label = "Folder",
            group = "searchEmailsImap",
            id = "searchEmailFolder",
            description = "",
            optional = true,
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.searchEmailFolder"))
        String searchEmailFolder)
    implements ImapAction {}
