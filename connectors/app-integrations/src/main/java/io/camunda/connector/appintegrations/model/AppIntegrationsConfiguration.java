/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotEmpty;

public record AppIntegrationsConfiguration(
    @NotEmpty
        @TemplateProperty(
            group = "configuration",
            label = "Base URL",
            description = "App Integrations backend URL. Use secrets.APP_INTEGRATIONS_BASE_URL.")
        String baseUrl,
    @NotEmpty
        @TemplateProperty(
            group = "configuration",
            label = "Token",
            description = "Bearer token for authentication. Use secrets.APP_INTEGRATIONS_TOKEN.")
        String token) {}
