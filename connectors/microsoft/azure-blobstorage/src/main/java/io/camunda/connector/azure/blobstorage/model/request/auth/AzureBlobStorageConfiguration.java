/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.blobstorage.model.request.auth;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Configuration(
    id = "io.camunda.connectors:azure-blobstorage:1",
    version = 1,
    name = "Azure Blob Storage Credential")
public record AzureBlobStorageConfiguration(
    @Valid
        @NotNull
        @TemplateProperty(
            group = "authentication",
            description = "Choose the authentication mechanism this credential provides.")
        Authentication authentication) {}
