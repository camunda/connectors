/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations.model;

import io.camunda.connector.appintegrations.model.auth.AppIntegrationsAuthentication;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record AppIntegrationsConfiguration(
    @NotEmpty
        @TemplateProperty(
            group = "configuration",
            label = "Base URL",
            description = "App Integrations backend URL.",
            tooltip =
                "Tip: store the URL as a secret, e.g. <code>= secrets.APP_INTEGRATIONS_BASE_URL</code>.")
        String baseUrl,
    @NotNull @Valid AppIntegrationsAuthentication authentication) {}
