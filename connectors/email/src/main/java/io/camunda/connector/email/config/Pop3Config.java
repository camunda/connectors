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

public record Pop3Config(
    @TemplateProperty(
            label = "POP3 Host",
            group = "protocol",
            id = "data.pop3Host",
            tooltip =
                "Enter the address of the POP3 server if you want to download your emails to a single device. This server is typically used for retrieving emails without syncing. (e.g., pop.example.com)",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.pop3Config.pop3Host"))
        @Valid
        @NotNull
        String pop3Host,
    @TemplateProperty(
            label = "POP3 Port",
            group = "protocol",
            id = "data.pop3Port",
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            tooltip =
                "Enter the port number for connecting to the POP3 server. The standard port is 995 for secure connections with SSL/TLS, or 110 for non-secure connections.",
            defaultValue = "995",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.pop3Config.pop3Port"))
        @Valid
        @NotNull
        Integer pop3Port,
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
                    name = "data.pop3Config.pop3CryptographicProtocol"))
        @NotNull
        CryptographicProtocol pop3CryptographicProtocol)
    implements Configuration {}
