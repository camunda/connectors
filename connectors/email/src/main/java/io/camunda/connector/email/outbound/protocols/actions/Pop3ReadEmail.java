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

@TemplateSubType(id = "readEmailPop3", label = "Read Email")
public record Pop3ReadEmail(
    @TemplateProperty(
            label = "Message ID",
            group = "readEmailPop3",
            id = "pop3MessageIdRead",
            tooltip = "The ID of the message, typically returned by a previous email task.",
            feel = Property.FeelMode.required,
            binding = @TemplateProperty.PropertyBinding(name = "data.pop3Action.messageId"))
        @Valid
        @NotNull
        String messageId,
    @TemplateProperty(
            label = "Delete after reading",
            group = "readEmailPop3",
            tooltip =
                "Enable this option if you want the email to be automatically deleted from the server after it is read. By default, this option is turned off to retain emails on the server.",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValue = "false",
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            binding = @TemplateProperty.PropertyBinding(name = "data.pop3Action.deleteOnRead"))
        boolean deleteOnRead)
    implements Pop3Action {}
