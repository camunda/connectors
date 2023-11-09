/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.model.request.auth;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(id = "apiKeyAuthentication", label = "Authenticate (username and API key)")
public record ApiKeyAuthentication(
    @NotBlank @TemplateProperty(label = "Username", id = "apiUsername", group = "authentication")
        String username,
    @NotBlank @TemplateProperty(label = "API key", group = "authentication") String apiKey)
    implements Authentication {}
