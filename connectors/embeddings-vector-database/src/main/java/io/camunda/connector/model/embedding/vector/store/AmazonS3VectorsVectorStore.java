/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.vector.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(
    label = "Amazon S3 Vectors",
    id = AmazonS3VectorsVectorStore.AMAZON_S3_VECTORS_STORE)
public record AmazonS3VectorsVectorStore(@Valid @NotNull Configuration amazonS3Vectors)
    implements EmbeddingsVectorStore {

  @TemplateProperty(ignore = true)
  public static final String AMAZON_S3_VECTORS_STORE = "amazonS3VectorsStore";

  public record Configuration(
      // Declared first so the chooser renders before the inline fallback fields it gates below.
      @FEEL
          @Valid
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Amazon S3 Vectors credential",
              type = TemplateProperty.PropertyType.Configuration,
              optional = true,
              description =
                  "Choose a reusable Amazon S3 Vectors credential, or configure one-time access keys below.")
          AmazonS3VectorsCredential amazonS3VectorsCredential,
      @TemplateProperty(
              group = "embeddingsStore",
              label = "Access key",
              tooltip =
                  "IAM access key of a user allowed to read and write the vector bucket and its indexes",
              constraints = @PropertyConstraints(notEmpty = true),
              condition =
                  @PropertyCondition(
                      property = "vectorStore.amazonS3Vectors.amazonS3VectorsCredential",
                      isEmpty = NullableBoolean.TRUE))
          String accessKey,
      @TemplateProperty(
              group = "embeddingsStore",
              label = "Secret key",
              tooltip = "Secret key associated with the access key",
              constraints = @PropertyConstraints(notEmpty = true),
              condition =
                  @PropertyCondition(
                      property = "vectorStore.amazonS3Vectors.amazonS3VectorsCredential",
                      isEmpty = NullableBoolean.TRUE))
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
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Index name",
              tooltip =
                  "Vector index that stores the embeddings. If it does not exist, it is created on"
                      + " the first embed with cosine distance and the embedding model's dimensions.",
              placeholder = "embeddings")
          String indexName) {

    /** The bound credential's access key takes precedence; the inline accessKey is the fallback. */
    @Override
    public String accessKey() {
      return amazonS3VectorsCredential != null ? amazonS3VectorsCredential.accessKey() : accessKey;
    }

    /** The bound credential's secret key takes precedence; the inline secretKey is the fallback. */
    @Override
    public String secretKey() {
      return amazonS3VectorsCredential != null ? amazonS3VectorsCredential.secretKey() : secretKey;
    }

    @AssertTrue(message = "No access key provided by the credential or the element template")
    @JsonIgnore
    public boolean isAccessKeyPresent() {
      return accessKey() != null && !accessKey().isBlank();
    }

    @AssertTrue(message = "No secret key provided by the credential or the element template")
    @JsonIgnore
    public boolean isSecretKeyPresent() {
      return secretKey() != null && !secretKey().isBlank();
    }

    @Override
    public String toString() {
      return "Configuration(accessKey=[REDACTED], secretKey=[REDACTED], region='%s', vectorBucketName='%s', indexName='%s')"
          .formatted(region, vectorBucketName, indexName);
    }
  }
}
