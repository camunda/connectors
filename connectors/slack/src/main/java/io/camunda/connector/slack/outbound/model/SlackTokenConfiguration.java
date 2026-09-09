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

/**
 * Configuration (credential) template for a reusable Slack OAuth token, consumed by the Slack
 * outbound connector.
 *
 * <p>Deliberately a separate configuration type from the inbound connector's signing secret, rather
 * than one Slack credential carrying both secrets. The two directions need different secrets — the
 * outbound connector calls the Slack API with a bot token, the inbound connector verifies incoming
 * requests with the app's signing secret — and neither is a variant of the other, so they cannot be
 * narrowed out of a shared type the way {@code RestAuthenticationConfiguration} narrows its
 * authentication union per consumer. A single type carrying both would make every
 * signing-secret-only instance selectable on an outbound task, where the missing token surfaces
 * only at job activation; Modeler filters the chooser by configuration id, so keeping them separate
 * makes that mis-selection unrepresentable at modelling time.
 */
@Configuration(id = "io.camunda.connectors:slack-token:1", version = 1, name = "Slack Token")
public record SlackTokenConfiguration(
    @NotBlank
        @TemplateProperty(
            group = "authentication",
            label = "OAuth token",
            description =
                "The Slack app's OAuth token, used to call the Slack API on the app's behalf.",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String token) {

  @Override
  public String toString() {
    return "SlackTokenConfiguration{token=[REDACTED]}";
  }
}
