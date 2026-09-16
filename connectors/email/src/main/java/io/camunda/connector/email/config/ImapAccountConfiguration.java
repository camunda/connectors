/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.config;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.hostvalidator.VerifiedHost;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * An {@link EmailAccountConfiguration} for reading email over IMAP: the mailbox login plus the IMAP
 * server coordinates. Mirrors the outbound task's own {@code Imap} shape.
 */
@TemplateSubType(
    id = "imap",
    label = "IMAP",
    description = "Read and manage emails using the IMAP protocol")
public record ImapAccountConfiguration(
    @NotBlank
        @TemplateProperty(
            group = "authentication",
            label = "Username",
            secret = true,
            tooltip =
                "Enter your full email address (e.g., user@example.com) or the username provided by your email service.")
        String username,
    @NotBlank
        @TemplateProperty(
            group = "authentication",
            label = "Email password",
            secret = true,
            tooltip = "Enter the password associated with your email account.")
        String password,
    @NotBlank
        @VerifiedHost
        @TemplateProperty(
            group = "protocol",
            label = "IMAP host",
            tooltip =
                "Address of the IMAP server used to retrieve your emails (e.g., imap.example.com).")
        String imapHost,
    @NotNull
        @TemplateProperty(
            group = "protocol",
            label = "IMAP port",
            defaultValue = "993",
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            tooltip = "Typically 993 for SSL/TLS, or 143 for non-secure connections.")
        Integer imapPort,
    @NotNull
        @TemplateProperty(
            group = "protocol",
            label = "IMAP encryption protocol",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "TLS")
        CryptographicProtocol imapCryptographicProtocol)
    implements EmailAccountConfiguration {

  @Override
  public Configuration getConfiguration() {
    return new ImapConfig(imapHost, imapPort, imapCryptographicProtocol);
  }

  @Override
  public String toString() {
    return "ImapAccountConfiguration{"
        + "username=[REDACTED]"
        + ", password=[REDACTED]"
        + ", imapHost="
        + imapHost
        + ", imapPort="
        + imapPort
        + ", imapCryptographicProtocol="
        + imapCryptographicProtocol
        + "}";
  }
}
