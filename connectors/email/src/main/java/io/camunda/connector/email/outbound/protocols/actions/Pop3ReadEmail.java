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

@TemplateSubType(
    id = "readEmailPop3",
    label = "Read Email",
    description = "Read the content of a specific email via POP3",
    keywords = {
      "read email",
      "pop3 read",
      "fetch email",
      "get email content",
      "retrieve email",
      "open message"
    })
public record Pop3ReadEmail(
    @TemplateProperty(
            label = "Message ID",
            group = "readEmailPop3",
            id = "pop3MessageIdRead",
            tooltip =
                "The ID of the message, typically returned by a previous email task. Warning: reading an email using POP3 will delete it.",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.pop3Action.messageId"))
        @Valid
        @NotNull
        String messageId)
    implements Pop3Action {}
