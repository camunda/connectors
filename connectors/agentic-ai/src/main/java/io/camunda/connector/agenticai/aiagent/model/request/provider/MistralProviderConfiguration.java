/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import static io.camunda.connector.agenticai.aiagent.model.request.provider.MistralProviderConfiguration.MISTRAL_ID;

import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.HttpUrl;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = MISTRAL_ID, label = "Mistral AI")
public record MistralProviderConfiguration(@Valid @NotNull MistralConnection mistral)
    implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String MISTRAL_ID = "mistral";

  public record MistralConnection(
      @HttpUrl
          @TemplateProperty(
              group = "provider",
              label = "Base URL",
              description =
                  "The Mistral API base URL. Default: https://api.mistral.ai/v1",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              defaultValue = "https://api.mistral.ai/v1",
              defaultValueType = TemplateProperty.DefaultValueType.String,
              optional = true)
          String endpoint,
      @Valid @NotNull MistralAuthentication authentication,
      @Valid TimeoutConfiguration timeouts,
      @Valid @NotNull MistralModel model) {}

  public record MistralAuthentication(
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "Mistral API key",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String apiKey) {

    @Override
    public String toString() {
      return "MistralAuthentication{apiKey=[REDACTED]}";
    }
  }

  public record MistralModel(
      @NotBlank
          @TemplateProperty(
              group = "model",
              label = "Model",
              description =
                  "Specify the model ID. Popular models: mistral-large-latest, mistral-small-latest, mistral-medium-latest, codestral-latest, open-mistral-nemo, pixtral-large-latest. Details in the <a href=\"https://docs.mistral.ai/getting-started/models/\" target=\"_blank\">documentation</a>.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              defaultValue = "mistral-large-latest",
              defaultValueType = TemplateProperty.DefaultValueType.String,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String model,
      @Valid MistralModel.MistralModelParameters parameters) {

    public record MistralModelParameters(
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "Maximum tokens",
                tooltip =
                    "The maximum number of tokens to generate in the completion. <br><br>Details in the <a href=\"https://docs.mistral.ai/api/\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Integer maxTokens,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "Temperature",
                tooltip =
                    "What sampling temperature to use, between 0.0 and 1.0. Higher values like 0.8 will make the output more random, while lower values like 0.2 will make it more focused and deterministic. <br><br>Details in the <a href=\"https://docs.mistral.ai/api/\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Double temperature,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "top P",
                tooltip =
                    "Nucleus sampling probability. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://docs.mistral.ai/api/\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Double topP,
        @TemplateProperty(
                group = "model",
                label = "Safe prompt",
                tooltip =
                    "Whether to inject a safety prompt before all conversations. <br><br>Details in the <a href=\"https://docs.mistral.ai/capabilities/guardrailing/\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Boolean,
                feel = FeelMode.optional,
                optional = true)
            Boolean safePrompt,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "Random seed",
                tooltip =
                    "The seed to use for random sampling. If set, different calls will generate deterministic results. <br><br>Details in the <a href=\"https://docs.mistral.ai/api/\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Integer randomSeed) {}
  }
}
