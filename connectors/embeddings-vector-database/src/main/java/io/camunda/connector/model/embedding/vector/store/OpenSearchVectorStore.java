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

@TemplateSubType(label = "OpenSearch", id = OpenSearchVectorStore.OPEN_SEARCH_STORE)
public record OpenSearchVectorStore(@Valid @NotNull Configuration openSearch)
    implements EmbeddingsVectorStore {

  @TemplateProperty(ignore = true)
  public static final String OPEN_SEARCH_STORE = "openSearchStore";

  public record Configuration(
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Base URL",
              description = "OpenSearch base URL, i.e. http(s)://host:port")
          String baseUrl,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Username",
              description = "OpenSearch username")
          String userName,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Password",
              description = "OpenSearch password")
          String password,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Index name",
              description = "OpenSearch index")
          String indexName) {

    @Override
    public String toString() {
      return "Configuration{baseUrl='%s', userName='%s', password='[REDACTED]', indexName='%s'}"
          .formatted(baseUrl, userName, indexName);
    }
  }
}
