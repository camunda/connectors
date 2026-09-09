/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.inbound.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.slack.api.app_backend.SlackSignature;
import io.camunda.connector.api.inbound.webhook.WebhookHttpResponse;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.function.Function;

public record SlackWebhookProperties(
    @TemplateProperty(
            id = "context",
            label = "Webhook ID",
            group = "endpoint",
            tooltip = "The webhook ID is a part of the URL endpoint",
            feel = FeelMode.disabled)
        @NotBlank
        String context,
    // Declared before the inline signing secret it gates below - required both for UX (pick a
    // credential before falling back to the inline secret) and by ConditionPropertyOrderRule (a
    // condition's referenced property must appear earlier).
    @TemplateProperty(
            id = "slackCredential",
            label = "Slack credential",
            group = "endpoint",
            type = PropertyType.Configuration,
            optional = true,
            feel = FeelMode.disabled,
            binding = @TemplateProperty.PropertyBinding(name = "slackCredential"),
            description =
                "Choose a reusable Slack signing secret credential, or configure a one-time"
                    + " signing secret below.")
        @Valid
        SlackSigningSecretConfiguration slackCredential,
    // @NotBlank moved off this field onto isSigningSecretPresent() below: once a credential
    // supplies the secret, this inline value is legitimately absent. Dropping @NotBlank also drops
    // the generator-derived notEmpty constraint, so it is restored by hand - the field is still
    // required client-side while it is visible (no credential bound). Hidden and un-required via
    // the isEmpty condition once a credential is chosen above; there is no inline override,
    // because the signing secret *is* the secret the credential exists to hold.
    @TemplateProperty(
            id = "slackSigningSecret",
            label = "Slack signing secret",
            group = "endpoint",
            tooltip =
                "Used to verify that incoming requests originate from Slack. See <a href='https://api.slack.com/authentication/verifying-requests-from-slack' target='_blank'>Verifying requests from Slack</a>",
            feel = FeelMode.disabled,
            condition =
                @PropertyCondition(property = "slackCredential", isEmpty = NullableBoolean.TRUE),
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String slackSigningSecret,
    @TemplateProperty(
            id = "verificationExpression",
            group = "endpoint",
            type = PropertyType.Hidden,
            feel = FeelMode.disabled,
            optional = true,
            defaultValue =
                "=if (body.type != null and body.type = \"url_verification\") then {body:{\"challenge\":body.challenge}, statusCode: 200} else null")
        Function<Map<String, Object>, WebhookHttpResponse> verificationExpression) {

  /**
   * A blank secret means "not set", not "set to an empty value": Modeler emits an empty string for
   * the hidden inline field once a credential is bound, so collapsing it to {@code null} here lets
   * every downstream check see a real absence instead of special-casing {@code ""}.
   */
  public SlackWebhookProperties {
    if (slackSigningSecret != null && slackSigningSecret.isBlank()) {
      slackSigningSecret = null;
    }
  }

  public SlackWebhookProperties(SlackConnectorPropertiesWrapper wrapper) {
    // Copies the raw components, not the effective secret, so the precedence below still applies.
    this(
        wrapper.inbound.context,
        wrapper.inbound.slackCredential,
        wrapper.inbound.slackSigningSecret,
        wrapper.inbound.verificationExpression);
  }

  /**
   * The effective signing secret: the bound credential wins over the inline field. Overriding the
   * record accessor rather than adding a new method means every existing caller of {@code
   * slackSigningSecret()} gets the effective value automatically.
   */
  @Override
  public String slackSigningSecret() {
    return slackCredential != null ? slackCredential.signingSecret() : slackSigningSecret;
  }

  @AssertTrue(
      message = "No signing secret provided by the reusable credential or the element template")
  @JsonIgnore
  public boolean isSigningSecretPresent() {
    return slackSigningSecret() != null && !slackSigningSecret().isBlank();
  }

  public SlackSignature.Verifier signatureVerifier() {
    return new SlackSignature.Verifier(new SlackSignature.Generator(this.slackSigningSecret()));
  }

  public record SlackConnectorPropertiesWrapper(@Valid SlackWebhookProperties inbound) {}

  @Override
  public String toString() {
    return "SlackWebhookProperties{"
        + "context='"
        + context
        + "'"
        + ", slackCredential="
        + slackCredential
        + ", slackSigningSecret=[REDACTED]"
        + ", verificationExpression="
        + verificationExpression
        + "}";
  }
}
