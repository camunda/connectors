/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.models;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.model.embedding.models.GoogleVertexAiEmbeddingModelProvider.GoogleVertexAiAuthentication;
import jakarta.validation.Valid;

/** Reusable, secrets-only credential for the Google Vertex AI embedding model provider. */
@Configuration(
    id = "io.camunda.connectors:embeddings-google-vertex-ai-credential:1",
    version = 1,
    name = "Google Vertex AI embedding credential")
public record GoogleVertexAiEmbeddingCredential(
    @Valid @TemplateProperty(group = "embeddingModel")
        GoogleVertexAiAuthentication authentication) {}
