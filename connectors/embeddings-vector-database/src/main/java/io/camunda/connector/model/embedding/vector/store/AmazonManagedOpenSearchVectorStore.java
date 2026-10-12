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
    label = "Amazon Managed OpenSearch",
    id = AmazonManagedOpenSearchVectorStore.AMAZON_MANAGED_OPENSEARCH_STORE)
public record AmazonManagedOpenSearchVectorStore(
    @Valid @NotNull Configuration amazonManagedOpensearch) implements EmbeddingsVectorStore {

  @TemplateProperty(ignore = true)
  public static final String AMAZON_MANAGED_OPENSEARCH_STORE = "amazonManagedOpenSearchStore";

  public record Configuration(
      // Declared first so the chooser renders before the inline fallback fields it gates below.
      @FEEL
          @Valid
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Amazon Managed OpenSearch credential",
              type = TemplateProperty.PropertyType.Configuration,
              optional = true,
              description =
                  "Choose a reusable Amazon Managed OpenSearch credential, or configure one-time access keys below.")
          AmazonManagedOpenSearchCredential amazonManagedOpenSearchCredential,
      @TemplateProperty(
              group = "embeddingsStore",
              label = "Access key",
              description =
                  "Provide an IAM access key tailored to a user, equipped with the necessary permissions",
              constraints = @PropertyConstraints(notEmpty = true),
              condition =
                  @PropertyCondition(
                      property =
                          "vectorStore.amazonManagedOpensearch.amazonManagedOpenSearchCredential",
                      isEmpty = NullableBoolean.TRUE))
          String accessKey,
      @TemplateProperty(
              group = "embeddingsStore",
              label = "Secret key",
              description = "Provide a secret key associated with the access key",
              constraints = @PropertyConstraints(notEmpty = true),
              condition =
                  @PropertyCondition(
                      property =
                          "vectorStore.amazonManagedOpensearch.amazonManagedOpenSearchCredential",
                      isEmpty = NullableBoolean.TRUE))
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

    /** The bound credential's access key takes precedence; the inline accessKey is the fallback. */
    @Override
    public String accessKey() {
      return amazonManagedOpenSearchCredential != null
          ? amazonManagedOpenSearchCredential.accessKey()
          : accessKey;
    }

    /** The bound credential's secret key takes precedence; the inline secretKey is the fallback. */
    @Override
    public String secretKey() {
      return amazonManagedOpenSearchCredential != null
          ? amazonManagedOpenSearchCredential.secretKey()
          : secretKey;
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
      return "Configuration(accessKey=[REDACTED], secretKey=[REDACTED], serverUrl='%s', region='%s', indexName='%s')"
          .formatted(serverUrl, region, indexName);
    }
  }
}
