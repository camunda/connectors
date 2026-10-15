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

/** Reusable, secrets-only credential for the OpenSearch vector store. */
@Configuration(
    id = "io.camunda.connectors:embeddings-opensearch-credential:1",
    version = 1,
    name = "OpenSearch credential")
public record OpenSearchCredential(
    @NotBlank
        @TemplateProperty(
            group = "authentication",
            label = "Username",
            description = "OpenSearch username")
        String userName,
    @NotBlank
        @TemplateProperty(
            secret = true,
            group = "authentication",
            label = "Password",
            description = "OpenSearch password")
        String password) {

  @Override
  public String toString() {
    return "OpenSearchCredential{userName='%s', password='[REDACTED]'}".formatted(userName);
  }
}
