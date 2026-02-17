/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.config;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SmtpConfig(
    @TemplateProperty(
            label = "SMTP Host",
            group = "protocol",
            id = "data.smtpHost",
            tooltip =
                "Provide the address of the SMTP server used for sending emails. This server handles the delivery of your outgoing messages. (e.g., smtp.example.com)",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpConfig.smtpHost"))
        @Valid
        @NotNull
        String smtpHost,
    @TemplateProperty(
            label = "SMTP Port",
            group = "protocol",
            id = "data.smtpPort",
            tooltip =
                "Enter the port number for connecting to the SMTP server. Typically, port 587 is used for secure connections with STARTTLS, port 465 for secure connections using SSL/TLS, and port 25 for non-secure connections.",
            defaultValue = "587",
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpConfig.smtpPort"))
        @Valid
        @NotNull
        Integer smtpPort,
    @TemplateProperty(
            label = "Cryptographic protocol",
            tooltip = "Select the encryption protocol for email security.",
            group = "protocol",
            feel = FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            defaultValue = "TLS",
            binding =
                @TemplateProperty.PropertyBinding(
                    name = "data.smtpConfig.smtpCryptographicProtocol"))
        @NotNull
        CryptographicProtocol smtpCryptographicProtocol)
    implements Configuration {}
