/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.inbound.model;

import com.slack.api.app_backend.SlackSignature;
import io.camunda.connector.api.inbound.webhook.VerifiableWebhook;
import java.util.Map;
import java.util.function.Function;

public record SlackWebhookProperties(
    String context,
    String slackSigningSecret,
    Function<Map<String, Object>, VerifiableWebhook.WebhookHttpVerificationResult>
        verificationExpression) {
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
}
