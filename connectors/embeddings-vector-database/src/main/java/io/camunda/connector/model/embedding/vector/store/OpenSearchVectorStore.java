/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.vector.store;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(label = "OpenSearch", id = OpenSearchVectorStore.STORE_OPENSEARCH)
public record OpenSearchVectorStore(
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "embeddingsStore.opensearch.baseUrl",
            label = "Base URL",
            description = "OpenSearch base URL, i.e. http(s)://host:port")
        String baseUrl,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "embeddingsStore.opensearch.username",
            label = "Username",
            description = "OpenSearch username")
        String userName,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "embeddingsStore.opensearch.password",
            label = "Password",
            description = "OpenSearch password")
        String password,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "embeddingsStore.opensearch.indexName",
            label = "Index name",
            description = "OpenSearch index")
        String indexName)
    implements EmbeddingsVectorStore {
  @TemplateProperty(ignore = true)
  public static final String STORE_OPENSEARCH = "STORE_OPENSEARCH";
}
