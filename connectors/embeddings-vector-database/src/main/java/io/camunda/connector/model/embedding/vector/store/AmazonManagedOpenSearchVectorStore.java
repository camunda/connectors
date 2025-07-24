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

@TemplateSubType(
    label = "Amazon Managed OpenSearch",
    id = AmazonManagedOpenSearchVectorStore.STORE_AMAZON_MANAGED_OPENSEARCH)
public record AmazonManagedOpenSearchVectorStore(
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "amazonManagedOpensearch.accessKey",
            label = "Access key",
            description =
                "Provide an IAM access key tailored to a user, equipped with the necessary permissions")
        String accessKey,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "amazonManagedOpensearch.secretKey",
            label = "Secret key",
            description = "Provide a secret key associated with the access key")
        String secretKey,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "amazonManagedOpensearch.serverUrl",
            label = "Server URL",
            description = "Provide a fully qualified server URL")
        String serverUrl,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "amazonManagedOpensearch.region",
            label = "Region",
            description = "Provide an instance region")
        String region,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "amazonManagedOpensearch.indexName",
            label = "Index name",
            description = "Provide an index to store embeddings")
        String indexName)
    implements EmbeddingsVectorStore {
  @TemplateProperty(ignore = true)
  public static final String STORE_AMAZON_MANAGED_OPENSEARCH = "STORE_AMAZON_MANAGED_OPENSEARCH";

  @Override
  public String toString() {
    return "AmazonManagedOpenSearchVectorStore(accessKey=[REDACTED], secretKey=[REDACTED], serverUrl='%s', region='%s', indexName='%s')"
        .formatted(serverUrl, region, indexName);
  }
}
