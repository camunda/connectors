/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import static io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.ANTHROPIC_ID;

import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = ANTHROPIC_ID, label = "Anthropic")
public record AnthropicProviderConfiguration(@Valid @NotNull AnthropicConnection anthropic)
    implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String ANTHROPIC_ID = "anthropic";

  public record AnthropicConnection(
      @TemplateProperty(
              group = "provider",
              description = "Optional custom API endpoint",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              optional = true)
          String endpoint,
      @Valid @NotNull AnthropicAuthentication authentication,
      @Valid TimeoutConfiguration timeouts,
      @Valid @NotNull AnthropicModel model) {}

  public record AnthropicAuthentication(
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "Anthropic API key",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String apiKey) {

    @Override
    public String toString() {
      return "AnthropicAuthentication{apiKey=[REDACTED]}";
    }
  }

  public record AnthropicModel(
      @NotBlank
          @TemplateProperty(
              group = "model",
              label = "Model",
              description =
                  "Specify the model ID. Details in the <a href=\"https://docs.anthropic.com/en/docs/about-claude/models/all-models\" target=\"_blank\">documentation</a>.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              defaultValue = "claude-3-5-sonnet-20240620",
              defaultValueType = TemplateProperty.DefaultValueType.String,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String model,
      @Valid AnthropicModel.AnthropicModelParameters parameters) {

    public record AnthropicModelParameters(
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "Maximum tokens",
                tooltip =
                    "The maximum number of tokens per request to generate before stopping. <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-max-tokens\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Integer maxTokens,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "Temperature",
                tooltip =
                    "Floating point number between 0 and 1. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-temperature\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Double temperature,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "top P",
                tooltip =
                    "Floating point number between 0 and 1. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-top-p\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Double topP,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "top K",
                tooltip =
                    "Integer greater than 0. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-top-k\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Integer topK) {}
  }
}
