/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.model.request;

import io.camunda.connector.automationanywhere.model.request.auth.Authentication;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration (credential) template for a reusable Automation Anywhere Control Room login: the
 * Control Room URL plus one of the connector's authentication variants. The URL is part of the
 * credential because a login is only meaningful against the Control Room that issued it, and so
 * that the credential can be validated on its own.
 *
 * <p>Not to be confused with {@link Configuration}, the connector's inline connection settings.
 */
@io.camunda.connector.api.annotation.Configuration(
    id = "io.camunda.connectors:automation-anywhere:1",
    version = 1,
    name = "Automation Anywhere Control Room")
public record AutomationAnywhereConfiguration(
    @NotBlank
        @TemplateProperty(
            group = "configuration",
            label = "Control room URL",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String controlRoomUrl,
    @Valid @NotNull @TemplateProperty(group = "authentication", id = "authenticationType")
        Authentication authentication) {}
