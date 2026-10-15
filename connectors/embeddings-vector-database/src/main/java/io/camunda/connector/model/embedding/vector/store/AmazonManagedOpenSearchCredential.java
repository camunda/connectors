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

/** Reusable, secrets-only credential for the Amazon Managed OpenSearch vector store. */
@Configuration(
    id = "io.camunda.connectors:embeddings-amazon-managed-opensearch-credential:1",
    version = 1,
    name = "Amazon Managed OpenSearch credential")
public record AmazonManagedOpenSearchCredential(
    @NotBlank
        @TemplateProperty(
            secret = true,
            group = "authentication",
            label = "Access key",
            description =
                "Provide an IAM access key tailored to a user, equipped with the necessary permissions")
        String accessKey,
    @NotBlank
        @TemplateProperty(
            secret = true,
            group = "authentication",
            label = "Secret key",
            description = "Provide a secret key associated with the access key")
        String secretKey) {

  @Override
  public String toString() {
    return "AmazonManagedOpenSearchCredential{accessKey=[REDACTED], secretKey=[REDACTED]}";
  }
}
