/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.inbound.model;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Configuration (credential) template for a reusable Slack signing secret, consumed by the Slack
 * inbound (webhook) connector.
 *
 * <p>Separate from {@code SlackTokenConfiguration} for the reasons recorded there: the signing
 * secret verifies requests arriving from Slack, the OAuth token authenticates calls made to Slack,
 * and a single type carrying both would offer token-only instances in the inbound chooser (and vice
 * versa) with the mismatch only surfacing at runtime.
 */
@Configuration(
    id = "io.camunda.connectors:slack-signing-secret:1",
    version = 1,
    name = "Slack Signing Secret")
public record SlackSigningSecretConfiguration(
    @NotBlank
        @TemplateProperty(
            group = "endpoint",
            label = "Slack signing secret",
            description =
                "Used to verify that incoming requests originate from Slack. See <a"
                    + " href='https://api.slack.com/authentication/verifying-requests-from-slack'"
                    + " target='_blank'>Verifying requests from Slack</a>",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String signingSecret) {

  @Override
  public String toString() {
    return "SlackSigningSecretConfiguration{signingSecret=[REDACTED]}";
  }
}
