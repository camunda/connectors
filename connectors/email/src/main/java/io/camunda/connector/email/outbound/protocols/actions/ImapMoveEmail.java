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

@TemplateSubType(id = "moveEmailImap", label = "Move email")
public record ImapMoveEmail(
    @TemplateProperty(
            label = "Message ID",
            group = "moveEmailImap",
            id = "imapMessageIdMove",
            tooltip = "The ID of the message, typically returned by a previous email task.",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.messageId"))
        @Valid
        @NotNull
        String messageId,
    @TemplateProperty(
            label = "Source folder",
            group = "moveEmailImap",
            id = "data.fromFolder",
            tooltip =
                "Enter the name of the folder from which the emails will be moved. This field is required. For example, enter 'INBOX' to move emails from your Inbox.",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.fromFolder"))
        @Valid
        @NotNull
        String fromFolder,
    @TemplateProperty(
            label = "Target folder",
            group = "moveEmailImap",
            id = "data.toFolder",
            tooltip =
                "Specify the destination folder to which the emails will be moved. To create a new folder or a hierarchy of folders, use a dot-separated path (e.g., 'Archive' or 'Projects.2023.January'). If any part of the path does not exist, it will be created automatically.",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.toFolder"))
        @Valid
        @NotNull
        String toFolder)
    implements ImapAction {}
