/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.models;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@TemplateSubType(
    label = "Google Vertex AI",
    id = GoogleVertexAiEmbeddingModelProvider.VERTEX_AI_MODEL_PROVIDER)
public record GoogleVertexAiEmbeddingModelProvider(@Valid @NotNull Configuration googleVertexAi)
    implements EmbeddingModelProvider {

  @TemplateProperty(ignore = true)
  public static final String VERTEX_AI_MODEL_PROVIDER = "vertexAiModelProvider";

  @TemplateProperty(ignore = true)
  public static final String VERTEX_AI_DEFAULT_PUBLISHER = "google";

  public record Configuration(
      @NotBlank
          @TemplateProperty(
              group = "embeddingModel",
              label = "Project ID",
              description = "Google Cloud project ID",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String projectId,
      @NotBlank
          @TemplateProperty(
              group = "embeddingModel",
              label = "Region",
              description = "Google Cloud region for Vertex AI",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String region,
      @Valid @NotNull GoogleVertexAiAuthentication authentication,
      @NotBlank
          @TemplateProperty(
              group = "embeddingModel",
              label = "Model name",
              description = "Vertex AI embedding model name",
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String modelName,
      @NotNull
          @Positive
          @TemplateProperty(
              group = "embeddingModel",
              label = "Embedding dimensions",
              description =
                  "The size of the vector used to represent data. Details in the <a href=\"https://cloud.google.com/vertex-ai/generative-ai/docs/embeddings/get-text-embeddings\" target=\"_blank\">documentation</a>.",
              feel = FeelMode.required,
              type = TemplateProperty.PropertyType.Number,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          Integer dimensions,
      @TemplateProperty(
              group = "embeddingModel",
              label = "Publisher",
              description =
                  "The publisher of the Vertex AI model (e.g., 'google', 'third-party'). Optional.",
              defaultValue = VERTEX_AI_DEFAULT_PUBLISHER,
              optional = true)
          String publisher,
      @TemplateProperty(
              group = "embeddingModel",
              label = "Max retries",
              description = "Max retries",
              defaultValueType = TemplateProperty.DefaultValueType.Number,
              defaultValue = "3",
              optional = true)
          Integer maxRetries) {

    @AssertFalse(message = "Google application default credentials is not supported on SaaS")
    public boolean isApplicationDefaultCredentialsUsedInSaaS() {
      return System.getenv().containsKey("CAMUNDA_CONNECTOR_RUNTIME_SAAS")
          && authentication()
              instanceof GoogleVertexAiAuthentication.ApplicationDefaultCredentialsAuthentication;
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(
        value = GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication.class,
        name = "serviceAccountCredentials"),
    @JsonSubTypes.Type(
        value = GoogleVertexAiAuthentication.ApplicationDefaultCredentialsAuthentication.class,
        name = "applicationDefaultCredentials"),
  })
  @TemplateDiscriminatorProperty(
      label = "Authentication",
      group = "embeddingModel",
      name = "type",
      defaultValue = "serviceAccountCredentials",
      description = "Specify the Google Vertex AI authentication strategy.")
  public sealed interface GoogleVertexAiAuthentication {
    @TemplateSubType(id = "serviceAccountCredentials", label = "Service account credentials")
    record ServiceAccountCredentialsAuthentication(
        @NotBlank
            @TemplateProperty(
                group = "embeddingModel",
                label = "JSON key of the service account",
                description = "This is the key of the service account in JSON format.",
                feel = FeelMode.optional,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            String jsonKey)
        implements GoogleVertexAiAuthentication {
      @Override
      public String toString() {
        return "ServiceAccountCredentialsAuthentication{jsonKey=[REDACTED]}";
      }
    }

    @TemplateSubType(
        id = "applicationDefaultCredentials",
        label = "Application default credentials (Hybrid/Self-Managed only)")
    record ApplicationDefaultCredentialsAuthentication() implements GoogleVertexAiAuthentication {}
  }
}
