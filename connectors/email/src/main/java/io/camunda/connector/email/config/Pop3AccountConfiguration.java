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
 * An {@link EmailAccountConfiguration} for downloading email over POP3: the mailbox login plus the
 * POP3 server coordinates. Mirrors the outbound task's own {@code Pop3} shape.
 */
@TemplateSubType(id = "pop3", label = "POP3", description = "Read emails using the POP3 protocol")
public record Pop3AccountConfiguration(
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
            label = "POP3 host",
            tooltip =
                "Address of the POP3 server used to download your emails (e.g., pop.example.com).")
        String pop3Host,
    @NotNull
        @TemplateProperty(
            group = "protocol",
            label = "POP3 port",
            defaultValue = "995",
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            tooltip = "Typically 995 for SSL/TLS, or 110 for non-secure connections.")
        Integer pop3Port,
    @NotNull
        @TemplateProperty(
            group = "protocol",
            label = "POP3 encryption protocol",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "TLS")
        CryptographicProtocol pop3CryptographicProtocol)
    implements EmailAccountConfiguration {

  @Override
  public Configuration getConfiguration() {
    return new Pop3Config(pop3Host, pop3Port, pop3CryptographicProtocol);
  }

  @Override
  public String toString() {
    return "Pop3AccountConfiguration{"
        + "username=[REDACTED]"
        + ", password=[REDACTED]"
        + ", pop3Host="
        + pop3Host
        + ", pop3Port="
        + pop3Port
        + ", pop3CryptographicProtocol="
        + pop3CryptographicProtocol
        + "}";
  }
}
