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

@TemplateSubType(id = "moveEmailImap", label = "Move email")
public record ImapMoveEmail(
    @TemplateProperty(
            label = "Message ID",
            group = "moveEmailImap",
            id = "imapMessageIdMove",
            description = "",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.messageId"))
        @Valid
        @NotNull
        String messageId,
    @TemplateProperty(
            label = "Source folder",
            group = "moveEmailImap",
            id = "data.fromFolder",
            description = "",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.fromFolder"))
        @Valid
        @NotNull
        String fromFolder,
    @TemplateProperty(
            label = "Target folder",
            group = "moveEmailImap",
            id = "data.toFolder",
            description = "",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.toFolder"))
        @Valid
        @NotNull
        String toFolder)
    implements ImapAction {}
