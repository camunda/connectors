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

@TemplateSubType(label = "Elasticsearch", id = ElasticSearchVectorStore.STORE_ELASTICSEARCH)
public record ElasticSearchVectorStore(
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "embeddingsStore.elasticsearch.baseUrl",
            label = "Base URL",
            description = "Elasticsearch base URL, i.e. http(s)://host:port")
        String baseUrl,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "embeddingsStore.elasticsearch.username",
            label = "Username",
            description = "Elasticsearch username")
        String userName,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "embeddingsStore.elasticsearch.password",
            label = "Password",
            description = "Elasticsearch password")
        String password,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "embeddingsStore.elasticsearch.indexName",
            label = "Index name",
            description = "Elasticsearch index")
        String indexName)
    implements EmbeddingsVectorStore {
  @TemplateProperty(ignore = true)
  public static final String STORE_ELASTICSEARCH = "STORE_ELASTICSEARCH";

  @Override
  public String toString() {
    return "ElasticSearchVectorStore{baseUrl='%s', userName='%s', password='[REDACTED]', indexName='%s'}"
        .formatted(baseUrl, userName, indexName);
  }
}
