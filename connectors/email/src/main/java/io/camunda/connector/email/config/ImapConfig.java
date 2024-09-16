/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.config;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ImapConfig(
    @TemplateProperty(
            label = "IMAP Host",
            group = "protocol",
            id = "data.imapHost",
            tooltip =
                "Enter the address of the IMAP server used to retrieve your emails. This server allows you to sync your messages across multiple devices. (e.g., imap.example.com)",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapConfig.imapHost"))
        @Valid
        @NotNull
        String imapHost,
    @TemplateProperty(
            label = "IMAP Port",
            group = "protocol",
            id = "data.imapPort",
            tooltip =
                "Enter the port number for connecting to the IMAP server. Common ports are 993 for secure connections using SSL/TLS, or 143 for non-secure connections.",
            defaultValue = "993",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapConfig.imapPort"))
        @Valid
        @NotNull
        Integer imapPort,
    @TemplateProperty(
            label = "Encryption protocol",
            tooltip = "Select the encryption protocol for email security.",
            group = "protocol",
            feel = Property.FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            defaultValue = "TLS",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(label = "TLS", value = "TLS"),
              @TemplateProperty.DropdownPropertyChoice(label = "None", value = "NONE"),
              @TemplateProperty.DropdownPropertyChoice(label = "SSL", value = "SSL")
            },
            binding =
                @TemplateProperty.PropertyBinding(
                    name = "data.imapConfig.imapCryptographicProtocol"))
        @NotNull
        CryptographicProtocol imapCryptographicProtocol)
    implements Configuration {}
