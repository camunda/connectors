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

public record SmtpConfig(
    @TemplateProperty(
            label = "SMTP Host",
            group = "protocol",
            id = "data.smtpHost",
            description = "",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpConfig.smtpHost"))
        @Valid
        @NotNull
        String smtpHost,
    @TemplateProperty(
            label = "SMTP Port",
            group = "protocol",
            id = "data.smtpPort",
            description = "",
            defaultValue = "587",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpConfig.smtpPort"))
        @Valid
        @NotNull
        Integer smtpPort,
    @TemplateProperty(
            label = "Cryptographic protocol",
            description = "Chose the desired cryptographic protocol",
            group = "protocol",
            feel = Property.FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            defaultValue = "TLS",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(label = "tls", value = "TLS"),
              @TemplateProperty.DropdownPropertyChoice(label = "none", value = "NONE"),
              @TemplateProperty.DropdownPropertyChoice(label = "ssl", value = "SSL")
            },
            binding =
                @TemplateProperty.PropertyBinding(
                    name = "data.smtpConfig.smtpCryptographicProtocol"))
        @NotNull
        CryptographicProtocol smtpCryptographicProtocol)
    implements Configuration {}
