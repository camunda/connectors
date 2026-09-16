/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.email.authentication.SimpleAuthentication;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

/**
 * Configuration (credential) template for a reusable email account: the mailbox login plus the
 * server coordinates of exactly one protocol. This mirrors the outbound task's own {@code Protocol}
 * chooser (pick SMTP, IMAP or POP3, then see only that protocol's fields) rather than holding all
 * three protocol blocks at once - the credential externalizes the same choice the task itself
 * makes, so picking a protocol here carries the same meaning as picking it on a task: an SMTP
 * account only ever sends, the same way an SMTP task only ever sends.
 *
 * <p>A mailbox used by both a send task and a read task needs one credential per protocol (e.g. one
 * SMTP account and one IMAP account, both pointing at the same real mailbox) rather than a single
 * credential covering all three - the same way a send task and a read task are two different,
 * single-protocol tasks in the diagram itself.
 *
 * <p>An element template embeds this template under {@code configurationTemplates} and a {@code
 * Configuration} chooser lets a Camunda developer pick a stored account instead of filling in the
 * inline authentication and server fields.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "protocol")
@JsonSubTypes(
    value = {
      @JsonSubTypes.Type(value = SmtpAccountConfiguration.class, name = "smtp"),
      @JsonSubTypes.Type(value = ImapAccountConfiguration.class, name = "imap"),
      @JsonSubTypes.Type(value = Pop3AccountConfiguration.class, name = "pop3")
    })
@TemplateDiscriminatorProperty(
    label = "Protocol",
    group = "protocol",
    name = "protocol",
    defaultValue = "smtp")
@io.camunda.connector.api.annotation.Configuration(
    id = "io.camunda.connectors:email-account:1",
    version = 1,
    name = "Email Account (Outbound)")
public sealed interface EmailAccountConfiguration
    permits SmtpAccountConfiguration, ImapAccountConfiguration, Pop3AccountConfiguration {

  String username();

  String password();

  /** The single server this account carries, for whichever protocol it was created for. */
  Configuration getConfiguration();

  default SimpleAuthentication toAuthentication() {
    return new SimpleAuthentication(username(), password());
  }
}
