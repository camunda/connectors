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

@TemplateSubType(
    label = "Amazon Managed OpenSearch",
    id = AmazonManagedOpenSearchVectorStore.AMAZON_MANAGED_OPENSEARCH_STORE)
public record AmazonManagedOpenSearchVectorStore(
    @Valid @NotNull Configuration amazonManagedOpensearch) implements EmbeddingsVectorStore {

  @TemplateProperty(ignore = true)
  public static final String AMAZON_MANAGED_OPENSEARCH_STORE = "amazonManagedOpenSearchStore";

  public record Configuration(
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Access key",
              description =
                  "Provide an IAM access key tailored to a user, equipped with the necessary permissions")
          String accessKey,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Secret key",
              description = "Provide a secret key associated with the access key")
          String secretKey,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Server URL",
              description = "Provide a fully qualified server URL")
          String serverUrl,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Region",
              description = "Provide an instance region")
          String region,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Index name",
              description = "Provide an index to store embeddings")
          String indexName) {

    @Override
    public String toString() {
      return "Configuration(accessKey=[REDACTED], secretKey=[REDACTED], serverUrl='%s', region='%s', indexName='%s')"
          .formatted(serverUrl, region, indexName);
    }
  }
}
