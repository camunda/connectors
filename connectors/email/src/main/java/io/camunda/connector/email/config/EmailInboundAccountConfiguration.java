/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.config;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.email.authentication.SimpleAuthentication;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.hostvalidator.VerifiedHost;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration (credential) template for a mailbox read over IMAP: the login plus the IMAP server
 * coordinates. Deliberately IMAP-only rather than sharing {@link EmailAccountConfiguration}'s
 * SMTP/IMAP/POP3 shape: the inbound Email listener only ever polls over IMAP, and a shared type
 * would let an SMTP- or POP3-only account be picked in the inbound chooser only to fail at binding
 * time instead of at credential-modelling time. Registering one shared {@code @Configuration}
 * across both an inbound and an outbound connector also hits a real element-template-generator
 * limitation - the same configuration id gets a different property schema (Number vs. String port
 * fields) depending on which connector's generation context produced it - so a dedicated type per
 * direction sidesteps that too, the same way {@code SlackTokenConfiguration} and {@code
 * SlackSigningSecretConfiguration} already do for Slack.
 */
@Configuration(
    id = "io.camunda.connectors:email-account-imap:1",
    version = 1,
    name = "Email Account (IMAP)")
public record EmailInboundAccountConfiguration(
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
            type = PropertyType.Dropdown,
            defaultValue = "TLS")
        CryptographicProtocol imapCryptographicProtocol) {

  public SimpleAuthentication toAuthentication() {
    return new SimpleAuthentication(username, password);
  }

  public ImapConfig toImapConfig() {
    return new ImapConfig(imapHost, imapPort, imapCryptographicProtocol);
  }

  @Override
  public String toString() {
    return "EmailInboundAccountConfiguration{"
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
