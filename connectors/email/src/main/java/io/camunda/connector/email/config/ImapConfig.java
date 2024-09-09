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
            description = "",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapConfig.imapHost"))
        @Valid
        @NotNull
        String imapHost,
    @TemplateProperty(
            label = "IMAP Port",
            group = "protocol",
            id = "data.imapPort",
            description = "",
            defaultValue = "993",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.imapConfig.imapPort"))
        @Valid
        @NotNull
        Integer imapPort,
    @TemplateProperty(
            label = "Cryptographic protocol",
            description = "Chose the desired cryptographic protocol",
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
