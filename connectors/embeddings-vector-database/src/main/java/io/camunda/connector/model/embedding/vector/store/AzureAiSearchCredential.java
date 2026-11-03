/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.vector.store;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;

/** Reusable, secrets-only credential for the Azure AI Search vector store. */
@Configuration(
    id = "io.camunda.connectors:embeddings-azure-ai-search-credential:1",
    version = 1,
    name = "Azure AI Search credential")
public record AzureAiSearchCredential(
    @Valid @TemplateProperty(group = "embeddingsStore") AzureAuthentication authentication) {}
