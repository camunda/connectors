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

/** Reusable, secrets-only credential for the Amazon S3 Vectors vector store. */
@Configuration(
    id = "io.camunda.connectors:embeddings-amazon-s3-vectors-credential:1",
    version = 1,
    name = "Amazon S3 Vectors credential")
public record AmazonS3VectorsCredential(
    @NotBlank
        @TemplateProperty(
            secret = true,
            group = "authentication",
            label = "Access key",
            tooltip =
                "IAM access key of a user allowed to read and write the vector bucket and its indexes")
        String accessKey,
    @NotBlank
        @TemplateProperty(
            secret = true,
            group = "authentication",
            label = "Secret key",
            tooltip = "Secret key associated with the access key")
        String secretKey) {

  @Override
  public String toString() {
    return "AmazonS3VectorsCredential{accessKey=[REDACTED], secretKey=[REDACTED]}";
  }
}
