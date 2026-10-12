/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.vector.store;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

/** Reusable, secrets-only credential for the Elasticsearch vector store. */
@Configuration(
    id = "io.camunda.connectors:embeddings-elasticsearch-credential:1",
    version = 1,
    name = "Elasticsearch credential")
public record ElasticsearchCredential(
    @NotBlank
        @TemplateProperty(
            group = "authentication",
            label = "Username",
            description = "Elasticsearch username")
        String userName,
    @NotBlank
        @TemplateProperty(
            group = "authentication",
            label = "Password",
            description = "Elasticsearch password")
        String password) {

  @Override
  public String toString() {
    return "ElasticsearchCredential{userName='%s', password='[REDACTED]'}".formatted(userName);
  }
}
