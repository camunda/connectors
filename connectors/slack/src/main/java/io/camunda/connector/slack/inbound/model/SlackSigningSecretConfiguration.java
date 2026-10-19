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

@Configuration(
    id = "io.camunda.connectors:slack-signing-secret:1",
    version = 1,
    name = "Slack Signing Secret")
public record SlackSigningSecretConfiguration(
    @NotBlank
        @TemplateProperty(
            group = "endpoint",
            label = "Slack signing secret",
            secret = true,
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
