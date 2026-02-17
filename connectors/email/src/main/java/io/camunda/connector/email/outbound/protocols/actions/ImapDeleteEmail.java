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

@TemplateSubType(id = "deleteEmailImap", label = "Delete an email")
public record ImapDeleteEmail(
    @TemplateProperty(
            label = "Message ID",
            group = "deleteEmailImap",
            id = "imapMessageIdDelete",
            tooltip = "The ID of the message, typically returned by a previous email task.",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.messageId"))
        @Valid
        @NotNull
        String messageId,
    @TemplateProperty(
            label = "Folder",
            group = "deleteEmailImap",
            id = "deleteEmailFolder",
            tooltip =
                "Specify the name of the folder from which you want to delete emails. If left blank, the default 'INBOX' will be used. For example, you can enter 'Trash' to delete emails from the Trash folder.",
            optional = true,
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.deleteEmailFolder"))
        String deleteEmailFolder)
    implements ImapAction {}
