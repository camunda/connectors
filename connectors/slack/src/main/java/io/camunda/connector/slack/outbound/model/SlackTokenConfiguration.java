/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.model;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

@Configuration(id = "io.camunda.connectors:slack-token:1", version = 1, name = "Slack Token")
public record SlackTokenConfiguration(
    @NotBlank
        @TemplateProperty(
            group = "authentication",
            label = "OAuth token",
            secret = true,
            description =
                "The Slack app's OAuth token, used to call the Slack API on the app's behalf.",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String token) {

  @Override
  public String toString() {
    return "SlackTokenConfiguration{token=[REDACTED]}";
  }
}
