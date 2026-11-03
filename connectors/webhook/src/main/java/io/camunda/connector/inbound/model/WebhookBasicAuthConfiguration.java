/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import jakarta.validation.constraints.NotBlank;

/** Reusable credential holding the username and password an incoming request must present. */
@Configuration(
    id = "io.camunda.connectors:webhook-basic-auth:1",
    version = 1,
    name = "Webhook Basic Authorization")
public record WebhookBasicAuthConfiguration(
    @NotBlank
        @TemplateProperty(
            group = "authorization",
            label = "Username",
            description = "Username expected on incoming requests",
            constraints = @PropertyConstraints(notEmpty = true))
        String username,
    @NotBlank
        @TemplateProperty(
            group = "authorization",
            label = "Password",
            description = "Password expected on incoming requests",
            secret = true,
            constraints = @PropertyConstraints(notEmpty = true))
        String password) {

  @Override
  public String toString() {
    return "WebhookBasicAuthConfiguration{username='" + username + "', password=[REDACTED]}";
  }
}
