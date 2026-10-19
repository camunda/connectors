/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.google.gcs.model.request;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;

@Configuration(id = "io.camunda:google-gcs-credential:1", name = "Google Cloud Storage Credential")
public record GcsCredentialConfiguration(
    @Valid @TemplateProperty(group = "authentication") Authentication authentication) {}
