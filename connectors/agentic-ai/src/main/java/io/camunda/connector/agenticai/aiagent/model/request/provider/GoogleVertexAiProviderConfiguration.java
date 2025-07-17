/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import static io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
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
              feel = Property.FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String projectId,
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "Location",
              description = "Specify the region where AI inference should take place",
              type = TemplateProperty.PropertyType.String,
              feel = Property.FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String location,
      @Valid @NotNull GoogleVertexAiProviderConfiguration.GoogleVertexAiModel model) {}

  public record GoogleVertexAiModel(
      @NotBlank
          @TemplateProperty(
              group = "model",
              label = "Model",
              description =
                  "Specify the model ID. Details in the <a href=\"https://cloud.google.com/vertex-ai/docs/generative-ai/models\" target=\"_blank\">documentation</a>.",
              type = TemplateProperty.PropertyType.String,
              feel = Property.FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String model,
      @Valid GoogleVertexAiModelParameters parameters) {

    public record GoogleVertexAiModelParameters(
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "Maximum tokens",
                tooltip =
                    "Maximum number of tokens that can be generated in the response. <br><br>Details in the <a href=\"https://cloud.google.com/vertex-ai/generative-ai/docs/model-reference/inference\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = Property.FeelMode.required,
                optional = true)
            Integer maxTokens,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "Temperature",
                tooltip =
                    "Floating point number between 0 and 1. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://cloud.google.com/vertex-ai/generative-ai/docs/model-reference/inference\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = Property.FeelMode.required,
                optional = true)
            Float temperature,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "top P",
                tooltip =
                    "Floating point number between 0 and 1. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://cloud.google.com/vertex-ai/generative-ai/docs/model-reference/inference\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = Property.FeelMode.required,
                optional = true)
            Float topP) {}
  }
}
