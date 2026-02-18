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
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "deleteEmailPop3", label = "Delete Email")
public record Pop3DeleteEmail(
    @TemplateProperty(
            label = "Message ID",
            group = "deleteEmailPop3",
            id = "pop3MessageIdDelete",
            tooltip = "The ID of the message, typically returned by a previous email task.",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.pop3Action.messageId"))
        @NotNull
        String messageId)
    implements Pop3Action {}
