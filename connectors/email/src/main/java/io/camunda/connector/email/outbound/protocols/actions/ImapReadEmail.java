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

@TemplateSubType(id = "readEmailImap", label = "Read an email")
public record ImapReadEmail(
    @TemplateProperty(
            label = "Message ID",
            group = "readEmailImap",
            id = "imapMessageIdRead",
            tooltip = "The ID of the message, typically returned by a previous email task.",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.messageId"))
        @Valid
        @NotNull
        String messageId,
    @TemplateProperty(
            label = "Folder",
            group = "readEmailImap",
            id = "readEmailFolder",
            tooltip =
                "Enter the name of the folder from which you wish to read emails. If left blank, emails will be read from the default 'INBOX' folder.",
            optional = true,
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.readEmailFolder"))
        String readEmailFolder)
    implements ImapAction {}
