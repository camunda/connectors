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

/**
 * Reusable credential holding what an incoming JWT is verified against. The role expression and
 * required roles depend on the token's shape, so they stay on the element.
 */
@Configuration(
    id = "io.camunda.connectors:webhook-jwt:1",
    version = 1,
    name = "Webhook JWT Authorization")
public record WebhookJwtConfiguration(
    @NotBlank
        @TemplateProperty(
            group = "authorization",
            label = "JWK URL",
            description = "Well-known URL of JWKs",
            constraints = @PropertyConstraints(notEmpty = true))
        String jwkUrl,
    @NotBlank
        @TemplateProperty(
            group = "authorization",
            label = "Issuer",
            description = "Expected value of the JWT 'iss' claim",
            constraints = @PropertyConstraints(notEmpty = true))
        String issuer,
    @NotBlank
        @TemplateProperty(
            group = "authorization",
            label = "Audience",
            description = "Expected value of the JWT 'aud' claim",
            constraints = @PropertyConstraints(notEmpty = true))
        String audience) {}
