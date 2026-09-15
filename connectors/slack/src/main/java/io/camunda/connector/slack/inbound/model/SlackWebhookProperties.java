/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.inbound.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.slack.api.app_backend.SlackSignature;
import io.camunda.connector.api.annotation.FEEL;
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
    @FEEL
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

  public SlackWebhookProperties {
    if (slackSigningSecret != null && slackSigningSecret.isBlank()) {
      slackSigningSecret = null;
    }
  }

  public SlackWebhookProperties(
      String context,
      String slackSigningSecret,
      Function<Map<String, Object>, WebhookHttpResponse> verificationExpression) {
    this(context, null, slackSigningSecret, verificationExpression);
  }

  public SlackWebhookProperties(SlackConnectorPropertiesWrapper wrapper) {
    this(
        wrapper.inbound.context,
        wrapper.inbound.slackCredential,
        wrapper.inbound.slackSigningSecret,
        wrapper.inbound.verificationExpression);
  }

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
        + (slackCredential != null ? "[REDACTED]" : "null")
        + ", slackSigningSecret=[REDACTED]"
        + ", verificationExpression="
        + verificationExpression
        + "}";
  }
}
