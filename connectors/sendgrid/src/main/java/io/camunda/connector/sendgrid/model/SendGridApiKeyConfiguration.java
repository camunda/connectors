/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sendgrid.model;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

@Configuration(
    id = "io.camunda.connectors:sendgrid-api-key:1",
    version = 1,
    name = "SendGrid API Key")
public record SendGridApiKeyConfiguration(
    @NotBlank
        @TemplateProperty(
            group = "authentication",
            label = "SendGrid API key",
            secret = true,
            description = "The SendGrid API key used to send emails.",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String apiKey) {

  @Override
  public String toString() {
    return "SendGridApiKeyConfiguration{apiKey=[REDACTED]}";
  }
}
