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

@TemplateSubType(id = "sendEmailSmtp", label = "Send Email")
public record SmtpSendEmail(
    @TemplateProperty(
            label = "From",
            group = "sendEmailSmtp",
            id = "smtpFrom",
            tooltip =
                "Comma-separated list of email, e.g., 'email1@domain.com,email2@domain.com' or '=[ \"email1@domain.com\", \"email2@domain.com\"]'",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.from"))
        @Valid
        @NotNull
        Object from,
    @TemplateProperty(
            label = "To",
            group = "sendEmailSmtp",
            id = "smtpTo",
            tooltip =
                "Comma-separated list of email, e.g., 'email1@domain.com,email2@domain.com' or '=[ \"email1@domain.com\", \"email2@domain.com\"]'",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.to"))
        @Valid
        @NotNull
        Object to,
    @TemplateProperty(
            label = "Cc",
            group = "sendEmailSmtp",
            id = "smtpCc",
            tooltip =
                "Comma-separated list of email, e.g., 'email1@domain.com,email2@domain.com' or '=[ \"email1@domain.com\", \"email2@domain.com\"]'",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.cc"),
            optional = true)
        @Valid
        Object cc,
    @TemplateProperty(
            label = "Bcc",
            group = "sendEmailSmtp",
            id = "smtpBcc",
            tooltip =
                "Comma-separated list of email, e.g., 'email1@domain.com,email2@domain.com' or '=[ \"email1@domain.com\", \"email2@domain.com\"]'",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.bcc"),
            optional = true)
        @Valid
        Object bcc,
    @TemplateProperty(
            label = "Subject",
            group = "sendEmailSmtp",
            id = "smtpSubject",
            type = TemplateProperty.PropertyType.String,
            tooltip = "Email's subject",
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.subject"),
            feel = Property.FeelMode.optional)
        @Valid
        @NotNull
        String subject,
    @TemplateProperty(
            label = "Email Content",
            group = "sendEmailSmtp",
            id = "smtpBody",
            type = TemplateProperty.PropertyType.Text,
            tooltip = "Email's content",
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.body"),
            feel = Property.FeelMode.optional)
        @Valid
        @NotNull
        String body)
    implements SmtpAction {}
