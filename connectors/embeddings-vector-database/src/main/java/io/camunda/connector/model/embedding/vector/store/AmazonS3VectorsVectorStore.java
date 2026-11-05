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
import jakarta.validation.constraints.Pattern;

@TemplateSubType(
    label = "Amazon S3 Vectors",
    id = AmazonS3VectorsVectorStore.AMAZON_S3_VECTORS_STORE)
public record AmazonS3VectorsVectorStore(@Valid @NotNull Configuration amazonS3Vectors)
    implements EmbeddingsVectorStore {

  @TemplateProperty(ignore = true)
  public static final String AMAZON_S3_VECTORS_STORE = "amazonS3VectorsStore";

  public record Configuration(
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Access key",
              tooltip =
                  "IAM access key of a user allowed to call s3vectors:GetIndex, PutVectors,"
                      + " QueryVectors and GetVectors, plus CreateIndex if the connector should"
                      + " create the index")
          String accessKey,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Secret key",
              tooltip = "Secret key associated with the access key")
          String secretKey,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Region",
              tooltip = "AWS region of the vector bucket",
              placeholder = "us-east-1")
          String region,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Vector bucket name",
              tooltip = "Name of an existing S3 vector bucket",
              placeholder = "my-vector-bucket")
          String vectorBucketName,
      @NotBlank
          @Pattern(
              regexp = "^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$",
              message =
                  "must be 3 to 63 lowercase letters, digits, hyphens or dots, and start and end"
                      + " with a letter or digit")
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Index name",
              tooltip =
                  "Vector index that stores the embeddings. If it does not exist, it is created on"
                      + " the first embed with cosine distance and the embedding model's dimensions."
                      + " An existing index must declare '_page_content' as a non-filterable"
                      + " metadata key.",
              placeholder = "embeddings")
          String indexName) {

    @Override
    public String toString() {
      return "Configuration(accessKey=[REDACTED], secretKey=[REDACTED], region='%s', vectorBucketName='%s', indexName='%s')"
          .formatted(region, vectorBucketName, indexName);
    }
  }
}
