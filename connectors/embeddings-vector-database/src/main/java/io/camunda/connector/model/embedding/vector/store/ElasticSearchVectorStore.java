/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.vector.store;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(label = "Elasticsearch", id = ElasticSearchVectorStore.STORE_ELASTICSEARCH)
public record ElasticSearchVectorStore(@Valid @NotNull Configuration elasticSearch)
    implements EmbeddingsVectorStore {

  @TemplateProperty(ignore = true)
  public static final String STORE_ELASTICSEARCH = "STORE_ELASTICSEARCH";

  public record Configuration(
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Base URL",
              description = "Elasticsearch base URL, i.e. http(s)://host:port")
          String baseUrl,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Username",
              description = "Elasticsearch username")
          String userName,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Password",
              description = "Elasticsearch password")
          String password,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Index name",
              description = "Elasticsearch index")
          String indexName) {
    @Override
    public String toString() {
      return "Configuration{baseUrl='%s', userName='%s', password='[REDACTED]', indexName='%s'}"
          .formatted(baseUrl, userName, indexName);
    }
  }
}
