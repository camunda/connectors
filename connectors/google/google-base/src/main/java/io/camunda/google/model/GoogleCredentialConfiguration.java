/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.google.model;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;

@Configuration(id = "io.camunda:google-credential:1", name = "Google Credential")
public record GoogleCredentialConfiguration(
    @Valid @TemplateProperty(group = "authentication") Authentication authentication) {}
