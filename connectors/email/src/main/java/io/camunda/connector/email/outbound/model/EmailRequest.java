/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.email.authentication.OutboundAuthentication;
import io.camunda.connector.email.config.Configuration;
import io.camunda.connector.email.outbound.protocols.Imap;
import io.camunda.connector.email.outbound.protocols.Pop3;
import io.camunda.connector.email.outbound.protocols.Protocol;
import io.camunda.connector.email.outbound.protocols.Smtp;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record EmailRequest(
    // Declared first so it renders (and is emitted in properties[]) before the fallback fields it
    // gates below - required both for UX (pick an account before falling back to inline fields)
    // and by ConditionPropertyOrderRule (a condition's referenced property must appear earlier).
    @TemplateProperty(
            id = "emailAccountConfiguration",
            label = "Email account credential",
            group = "authentication",
            type = TemplateProperty.PropertyType.Configuration,
            optional = true,
            binding = @TemplateProperty.PropertyBinding(name = "configuration"),
            description =
                "Choose a reusable email account credential, or configure one-time email"
                    + " parameters below.")
        @Valid
        EmailAccountConfiguration configuration,
    // Neither @NotNull nor @Valid any more: a bound account supplies the login instead (see
    // authentication() below), and the losing inline value is a leftover discriminator Modeler
    // emits unconditionally - validated only when it wins, via
    // getInlineAuthenticationWhenNoAccountBound().
    @TemplateProperty(group = "authentication", id = "type")
        @NestedProperties(
            condition =
                @PropertyCondition(
                    property = "emailAccountConfiguration",
                    isEmpty = NullableBoolean.TRUE))
        OutboundAuthentication authentication,
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
            property = "protocol")
        @JsonSubTypes(
            value = {
              @JsonSubTypes.Type(value = Imap.class, name = "imap"),
              @JsonSubTypes.Type(value = Pop3.class, name = "pop3"),
              @JsonSubTypes.Type(value = Smtp.class, name = "smtp"),
            })
        @Valid
        @NotNull
        @NestedProperties(addNestedPath = false)
        Protocol data) {

  /** Convenience constructor for the pre-account-chooser shape (no bound account). */
  public EmailRequest(OutboundAuthentication authentication, Protocol data) {
    this(null, authentication, data);
  }

  /**
   * A bound account carries the mailbox login and takes precedence over the inline authentication
   * fields, which are hidden once one is bound. Named as the record accessor rather than a
   * synthetic {@code effectiveAuthentication()} so every existing caller gets the effective value.
   */
  public OutboundAuthentication authentication() {
    return configuration != null ? configuration.toAuthentication() : authentication;
  }

  /**
   * The server coordinates the requested operation runs against: the bound account's block for the
   * chosen protocol, or the inline per-protocol fields when no account is bound. The account wins
   * over the inline fields, which Modeler keeps emitting (with their static defaults) even for a
   * diagram that only picks an account.
   */
  @JsonIgnore
  public Configuration getProtocolConfiguration() {
    if (data == null) {
      return null;
    }
    if (configuration == null) {
      return data.getConfiguration();
    }
    return switch (data) {
      case Smtp ignored -> configuration.toSmtpConfig();
      case Imap ignored -> configuration.toImapConfig();
      case Pop3 ignored -> configuration.toPop3Config();
    };
  }

  /**
   * Authentication is required, but it may come from the bound account instead of the inline
   * fields, so requiredness is asserted on the effective value (see {@link #authentication()}).
   * Replaces the former {@code @NotNull} on the component, which no longer holds.
   */
  @AssertTrue(message = "No authentication provided by the credential or the element template")
  @JsonIgnore
  public boolean isAuthenticationProvided() {
    return authentication() != null;
  }

  /**
   * The chosen protocol needs server coordinates from one of the two sources. A bound account can
   * legitimately carry no block for this protocol (an SMTP-only account bound to an IMAP task), and
   * that must fail binding rather than reach the mail session as a null configuration.
   */
  @AssertTrue(
      message = "No email server settings provided by the credential or the element template")
  @JsonIgnore
  public boolean isProtocolConfigurationProvided() {
    return getProtocolConfiguration() != null;
  }

  /**
   * Validates the inline authentication only when no account is bound. When one is bound it is the
   * effective source and the inline fields are irrelevant - including the {@code type: "simple"}
   * discriminator Modeler emits regardless of which source the user picked, which would otherwise
   * fail {@code SimpleAuthentication}'s {@code @NotEmpty} fields even though it lost.
   */
  @Valid
  @JsonIgnore
  public OutboundAuthentication getInlineAuthenticationWhenNoAccountBound() {
    return configuration != null ? null : authentication;
  }

  /**
   * Validates the inline server fields only when no account is bound, for the same reason as {@link
   * #getInlineAuthenticationWhenNoAccountBound()}. A bound account is validated on its own, through
   * the {@code @Valid} on the chooser component.
   */
  @Valid
  @JsonIgnore
  public Configuration getInlineProtocolConfigurationWhenNoAccountBound() {
    return configuration != null || data == null ? null : data.getConfiguration();
  }
}
