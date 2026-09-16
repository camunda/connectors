/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.email.authentication.SimpleAuthentication;
import io.camunda.connector.email.config.CryptographicProtocol;
import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.email.config.Pop3Config;
import io.camunda.connector.email.config.SmtpConfig;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.hostvalidator.VerifiedHost;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

/**
 * Configuration (credential) template for a reusable email account: the mailbox login plus the
 * server coordinates of the protocols that mailbox speaks. An element template embeds this template
 * under {@code configurationTemplates} and a {@code Configuration} chooser lets a Camunda developer
 * pick a stored account instead of filling in the inline authentication and server fields.
 *
 * <p>All three protocol blocks live on one credential so that a single stored account ("my Gmail
 * mailbox") serves a send task and a read task alike: each protocol reads its own coordinates, so
 * an account can never be bound to a task that would use the wrong server. A block counts as
 * configured when its host is set; its port and encryption protocol then default to the protocol's
 * conventional values rather than being separately mandatory.
 */
@Configuration(id = "io.camunda.connectors:email-account:1", version = 1, name = "Email Account")
public record EmailAccountConfiguration(
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
    @VerifiedHost
        @TemplateProperty(
            group = "protocol",
            label = "SMTP host",
            optional = true,
            tooltip =
                "Address of the SMTP server used for sending emails (e.g., smtp.example.com). Leave empty if this account is not used to send emails.")
        String smtpHost,
    @TemplateProperty(
            group = "protocol",
            label = "SMTP port",
            optional = true,
            defaultValue = "587",
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            tooltip =
                "Typically 587 for secure connections with STARTTLS, 465 for SSL/TLS, and 25 for non-secure connections.")
        Integer smtpPort,
    @TemplateProperty(
            group = "protocol",
            label = "SMTP encryption protocol",
            optional = true,
            type = PropertyType.Dropdown,
            defaultValue = "TLS")
        CryptographicProtocol smtpCryptographicProtocol,
    @VerifiedHost
        @TemplateProperty(
            group = "protocol",
            label = "IMAP host",
            optional = true,
            tooltip =
                "Address of the IMAP server used to retrieve your emails (e.g., imap.example.com). Leave empty if this account is not read over IMAP.")
        String imapHost,
    @TemplateProperty(
            group = "protocol",
            label = "IMAP port",
            optional = true,
            defaultValue = "993",
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            tooltip = "Typically 993 for SSL/TLS, or 143 for non-secure connections.")
        Integer imapPort,
    @TemplateProperty(
            group = "protocol",
            label = "IMAP encryption protocol",
            optional = true,
            type = PropertyType.Dropdown,
            defaultValue = "TLS")
        CryptographicProtocol imapCryptographicProtocol,
    @VerifiedHost
        @TemplateProperty(
            group = "protocol",
            label = "POP3 host",
            optional = true,
            tooltip =
                "Address of the POP3 server used to download your emails (e.g., pop.example.com). Leave empty if this account is not read over POP3.")
        String pop3Host,
    @TemplateProperty(
            group = "protocol",
            label = "POP3 port",
            optional = true,
            defaultValue = "995",
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            tooltip = "Typically 995 for SSL/TLS, or 110 for non-secure connections.")
        Integer pop3Port,
    @TemplateProperty(
            group = "protocol",
            label = "POP3 encryption protocol",
            optional = true,
            type = PropertyType.Dropdown,
            defaultValue = "TLS")
        CryptographicProtocol pop3CryptographicProtocol) {

  @TemplateProperty(ignore = true)
  private static final int DEFAULT_SMTP_PORT = 587;

  @TemplateProperty(ignore = true)
  private static final int DEFAULT_IMAP_PORT = 993;

  @TemplateProperty(ignore = true)
  private static final int DEFAULT_POP3_PORT = 995;

  public EmailAccountConfiguration {
    // A credential editor writes an empty string, not an absent key, for a protocol block the user
    // left alone - so every "is this protocol configured" test below would see "" instead of null.
    smtpHost = blankToNull(smtpHost);
    imapHost = blankToNull(imapHost);
    pop3Host = blankToNull(pop3Host);
    if (smtpHost != null) {
      smtpPort = smtpPort != null ? smtpPort : DEFAULT_SMTP_PORT;
      smtpCryptographicProtocol = orTls(smtpCryptographicProtocol);
    }
    if (imapHost != null) {
      imapPort = imapPort != null ? imapPort : DEFAULT_IMAP_PORT;
      imapCryptographicProtocol = orTls(imapCryptographicProtocol);
    }
    if (pop3Host != null) {
      pop3Port = pop3Port != null ? pop3Port : DEFAULT_POP3_PORT;
      pop3CryptographicProtocol = orTls(pop3CryptographicProtocol);
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static CryptographicProtocol orTls(CryptographicProtocol protocol) {
    return protocol != null ? protocol : CryptographicProtocol.TLS;
  }

  /**
   * An account with no server at all cannot be used by any operation, nor validated. Asserted here
   * rather than by making one protocol mandatory: which of the three is needed depends on the task
   * the credential is bound to.
   */
  @AssertTrue(
      message = "Configure at least one of the SMTP, IMAP or POP3 servers on this email account")
  @JsonIgnore
  public boolean isAtLeastOneServerConfigured() {
    return smtpHost != null || imapHost != null || pop3Host != null;
  }

  public SimpleAuthentication toAuthentication() {
    return new SimpleAuthentication(username, password);
  }

  /** The SMTP coordinates of this account, or {@code null} when it carries none. */
  public SmtpConfig toSmtpConfig() {
    return smtpHost == null ? null : new SmtpConfig(smtpHost, smtpPort, smtpCryptographicProtocol);
  }

  /** The IMAP coordinates of this account, or {@code null} when it carries none. */
  public ImapConfig toImapConfig() {
    return imapHost == null ? null : new ImapConfig(imapHost, imapPort, imapCryptographicProtocol);
  }

  /** The POP3 coordinates of this account, or {@code null} when it carries none. */
  public Pop3Config toPop3Config() {
    return pop3Host == null ? null : new Pop3Config(pop3Host, pop3Port, pop3CryptographicProtocol);
  }

  @Override
  public String toString() {
    return "EmailAccountConfiguration{"
        + "username=[REDACTED]"
        + ", password=[REDACTED]"
        + ", smtpHost="
        + smtpHost
        + ", smtpPort="
        + smtpPort
        + ", smtpCryptographicProtocol="
        + smtpCryptographicProtocol
        + ", imapHost="
        + imapHost
        + ", imapPort="
        + imapPort
        + ", imapCryptographicProtocol="
        + imapCryptographicProtocol
        + ", pop3Host="
        + pop3Host
        + ", pop3Port="
        + pop3Port
        + ", pop3CryptographicProtocol="
        + pop3CryptographicProtocol
        + "}";
  }
}
