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
 * An {@link EmailAccountConfiguration} for sending email over SMTP: the mailbox login plus the SMTP
 * server coordinates. Mirrors the outbound task's own {@code Smtp} shape.
 */
@TemplateSubType(id = "smtp", label = "SMTP", description = "Send emails using the SMTP protocol")
public record SmtpAccountConfiguration(
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
            label = "SMTP host",
            tooltip =
                "Address of the SMTP server used for sending emails (e.g., smtp.example.com).")
        String smtpHost,
    @NotNull
        @TemplateProperty(
            group = "protocol",
            label = "SMTP port",
            defaultValue = "587",
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            tooltip =
                "Typically 587 for secure connections with STARTTLS, 465 for SSL/TLS, and 25 for non-secure connections.")
        Integer smtpPort,
    @NotNull
        @TemplateProperty(
            group = "protocol",
            label = "SMTP encryption protocol",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "TLS")
        CryptographicProtocol smtpCryptographicProtocol)
    implements EmailAccountConfiguration {

  @Override
  public Configuration getConfiguration() {
    return new SmtpConfig(smtpHost, smtpPort, smtpCryptographicProtocol);
  }

  @Override
  public String toString() {
    return "SmtpAccountConfiguration{"
        + "username=[REDACTED]"
        + ", password=[REDACTED]"
        + ", smtpHost="
        + smtpHost
        + ", smtpPort="
        + smtpPort
        + ", smtpCryptographicProtocol="
        + smtpCryptographicProtocol
        + "}";
  }
}
