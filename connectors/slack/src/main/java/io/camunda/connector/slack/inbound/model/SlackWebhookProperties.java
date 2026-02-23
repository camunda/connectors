/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.inbound.model;

import com.slack.api.app_backend.SlackSignature;
import io.camunda.connector.api.inbound.webhook.WebhookHttpResponse;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.function.Function;

public record SlackWebhookProperties(
    @TemplateProperty(
            id = "context",
            label = "Webhook ID",
            group = "endpoint",
            description = "The webhook ID is a part of the URL endpoint",
            feel = FeelMode.disabled)
        @NotBlank
        String context,
    @TemplateProperty(
            id = "slackSigningSecret",
            label = "Slack signing secret",
            group = "endpoint",
            description =
                "Slack signing secret. <a href='https://api.slack.com/authentication/verifying-requests-from-slack' target='_blank'>See documentation</a> regarding the Slack signing secret",
            feel = FeelMode.disabled)
        @NotBlank
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
  public SlackWebhookProperties(SlackConnectorPropertiesWrapper wrapper) {
    this(
        wrapper.inbound.context,
        wrapper.inbound.slackSigningSecret,
        wrapper.inbound.verificationExpression);
  }

  public SlackSignature.Verifier signatureVerifier() {
    return new SlackSignature.Verifier(new SlackSignature.Generator(this.slackSigningSecret));
  }

  public record SlackConnectorPropertiesWrapper(SlackWebhookProperties inbound) {}

  @Override
  public String toString() {
    return "SlackWebhookProperties{"
        + "context='"
        + context
        + "'"
        + ", slackSigningSecret=[REDACTED]"
        + ", verificationExpression="
        + verificationExpression
        + "}";
  }
}
