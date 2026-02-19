/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import static io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.util.ConnectorUtils;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = GOOGLE_VERTEX_AI_ID, label = "Google Vertex AI")
public record GoogleVertexAiProviderConfiguration(
    @Valid @NotNull GoogleVertexAiConnection googleVertexAi) implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String GOOGLE_VERTEX_AI_ID = "google-vertex-ai";

  public record GoogleVertexAiConnection(
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "Project ID",
              description = "Specify Google Cloud project ID",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String projectId,
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "Region",
              description = "Specify the region where AI inference should take place",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String region,
      @Valid @NotNull GoogleVertexAiAuthentication authentication,
      @Valid @NotNull GoogleVertexAiProviderConfiguration.GoogleVertexAiModel model) {

    @AssertFalse(message = "Google Vertex AI is not supported on SaaS")
    public boolean isUsedInSaaS() {
      return ConnectorUtils.isSaaS()
          && authentication
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
      group = "provider",
      name = "type",
      defaultValue = "serviceAccountCredentials",
      description = "Specify the Google Vertex AI authentication strategy.")
  public sealed interface GoogleVertexAiAuthentication {
    @TemplateSubType(id = "serviceAccountCredentials", label = "Service account credentials")
    record ServiceAccountCredentialsAuthentication(
        @NotBlank
            @TemplateProperty(
                group = "provider",
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

  public record GoogleVertexAiModel(
      @NotBlank
          @TemplateProperty(
              group = "model",
              label = "Model",
              description =
                  "Specify the model ID. Details in the <a href=\"https://cloud.google.com/vertex-ai/docs/generative-ai/models\" target=\"_blank\">documentation</a>.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String model,
      @Valid GoogleVertexAiModelParameters parameters) {

    public record GoogleVertexAiModelParameters(
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "Maximum output tokens",
                tooltip =
                    "Maximum number of tokens that can be generated in the response. <br><br>Details in the <a href=\"https://cloud.google.com/vertex-ai/generative-ai/docs/model-reference/inference\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Integer maxOutputTokens,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "Temperature",
                tooltip =
                    "Controls the degree of randomness in token selection. <br><br>Details in the <a href=\"https://cloud.google.com/vertex-ai/generative-ai/docs/model-reference/inference\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Float temperature,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "top P",
                tooltip =
                    "Floating point number between 0 and 1. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://cloud.google.com/vertex-ai/generative-ai/docs/model-reference/inference\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Float topP,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "top K",
                tooltip =
                    "Integer greater than 0. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://cloud.google.com/vertex-ai/generative-ai/docs/model-reference/inference\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Integer topK) {}
  }
}
