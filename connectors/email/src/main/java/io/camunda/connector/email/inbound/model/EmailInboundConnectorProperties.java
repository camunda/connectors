/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.inbound.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.email.authentication.InboundAuthentication;
import io.camunda.connector.email.config.EmailAccountConfiguration;
import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

public record EmailInboundConnectorProperties(
    // Declared first so it renders (and is emitted in properties[]) before the fallback fields it
    // gates below - required both for UX (pick an account before falling back to inline fields)
    // and by ConditionPropertyOrderRule (a condition's referenced property must appear earlier).
    @TemplateProperty(
            id = "emailAccountConfiguration",
            label = "Email account credential",
            group = "authentication",
            type = TemplateProperty.PropertyType.Configuration,
            optional = true,
            binding = @TemplateProperty.PropertyBinding(name = "emailAccountConfiguration"),
            description =
                "Choose a reusable email account credential, or configure one-time email"
                    + " parameters below.")
        @Valid
        EmailAccountConfiguration emailAccountConfiguration,
    // Neither @Valid nor @NotNull any more: a bound account supplies the login instead (see
    // authentication() below), and the losing inline value is a leftover discriminator Modeler
    // emits unconditionally - validated only when it wins, via
    // getInlineAuthenticationWhenNoAccountBound().
    @TemplateProperty(group = "authentication", id = "type")
        @NestedProperties(
            condition =
                @PropertyCondition(
                    property = "emailAccountConfiguration",
                    isEmpty = NullableBoolean.TRUE))
        InboundAuthentication authentication,
    @NestedProperties(addNestedPath = false) @Valid EmailListenerConfig data) {

  /** Convenience constructor for the pre-account-chooser shape (no bound account). */
  public EmailInboundConnectorProperties(
      InboundAuthentication authentication, EmailListenerConfig data) {
    this(null, authentication, data);
  }

  /**
   * A bound account carries the mailbox login and takes precedence over the inline authentication
   * field, which is hidden once one is bound. Named as the record accessor rather than a synthetic
   * {@code effectiveAuthentication()} so every existing caller gets the effective value.
   */
  public InboundAuthentication authentication() {
    return emailAccountConfiguration != null
        ? emailAccountConfiguration.toAuthentication()
        : authentication;
  }

  /**
   * The IMAP coordinates this listener polls: the bound account's IMAP block, or the inline field
   * when no account is bound. The account wins over the inline field, which Modeler keeps emitting
   * (with its static defaults) even for a diagram that only picks an account.
   */
  @JsonIgnore
  public ImapConfig getImapConfiguration() {
    if (emailAccountConfiguration != null) {
      return emailAccountConfiguration.toImapConfig();
    }
    return data != null ? data.imapConfig() : null;
  }

  /**
   * Authentication is required, but it may come from the bound account instead of the inline field,
   * so requiredness is asserted on the effective value (see {@link #authentication()}). Replaces
   * the former {@code @NotNull} on the component, which no longer holds.
   */
  @AssertTrue(message = "No authentication provided by the credential or the element template")
  @JsonIgnore
  public boolean isAuthenticationProvided() {
    return authentication() != null;
  }

  /**
   * The listener needs IMAP server coordinates from one of the two sources. A bound account can
   * legitimately carry no IMAP block (e.g. an SMTP-only account), and that must fail binding rather
   * than reach the mail session as a null configuration.
   */
  @AssertTrue(
      message = "No email server settings provided by the credential or the element template")
  @JsonIgnore
  public boolean isImapConfigurationProvided() {
    return getImapConfiguration() != null;
  }

  /**
   * Validates the inline authentication only when no account is bound. When one is bound it is the
   * effective source and the inline field is irrelevant - including the {@code type: "simple"}
   * discriminator Modeler emits regardless of which source the user picked, which would otherwise
   * fail {@code SimpleAuthentication}'s {@code @NotEmpty} fields even though it lost.
   */
  @Valid
  @JsonIgnore
  public InboundAuthentication getInlineAuthenticationWhenNoAccountBound() {
    return emailAccountConfiguration != null ? null : authentication;
  }

  /**
   * Validates the inline IMAP field only when no account is bound, for the same reason as {@link
   * #getInlineAuthenticationWhenNoAccountBound()}. A bound account is validated on its own, through
   * the {@code @Valid} on the chooser component.
   */
  @Valid
  @JsonIgnore
  public ImapConfig getInlineImapConfigurationWhenNoAccountBound() {
    return emailAccountConfiguration != null || data == null ? null : data.imapConfig();
  }
}
