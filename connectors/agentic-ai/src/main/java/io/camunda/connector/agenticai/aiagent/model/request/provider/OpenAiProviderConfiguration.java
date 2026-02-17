/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import static io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OPENAI_ID;

import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = OPENAI_ID, label = "OpenAI")
public record OpenAiProviderConfiguration(@Valid @NotNull OpenAiConnection openai)
    implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String OPENAI_ID = "openai";

  public record OpenAiConnection(
      @Valid @NotNull OpenAiAuthentication authentication,
      @Valid TimeoutConfiguration timeouts,
      @Valid @NotNull OpenAiModel model) {}

  public record OpenAiAuthentication(
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "OpenAI API key",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String apiKey,
      @TemplateProperty(
              group = "provider",
              label = "Organization ID",
              description =
                  "For members of multiple organizations. Details in the <a href=\"https://platform.openai.com/docs/api-reference/authentication\" target=\"_blank\">documentation</a>.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              optional = true)
          String organizationId,
      @TemplateProperty(
              group = "provider",
              label = "Project ID",
              description =
                  "For accounts with multiple projects. Details in the <a href=\"https://platform.openai.com/docs/api-reference/authentication\" target=\"_blank\">documentation</a>.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              optional = true)
          String projectId) {

    @Override
    public String toString() {
      return "OpenAiAuthentication{apiKey=[REDACTED], organizationId=%s, projectId=%s}"
          .formatted(organizationId, projectId);
    }
  }

  public record OpenAiModel(
      @NotBlank
          @TemplateProperty(
              group = "model",
              label = "Model",
              description =
                  "Specify the model ID. Details in the <a href=\"https://platform.openai.com/docs/models\" target=\"_blank\">documentation</a>.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              defaultValue = "gpt-4o",
              defaultValueType = TemplateProperty.DefaultValueType.String,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String model,
      @Valid OpenAiModel.OpenAiModelParameters parameters) {

    public record OpenAiModelParameters(
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "Maximum completion tokens",
                tooltip =
                    "The maximum number of tokens per request to generate before stopping. <br><br>Details in the <a href=\"https://platform.openai.com/docs/api-reference/chat/create#chat-create-max_completion_tokens\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Integer maxCompletionTokens,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "Temperature",
                tooltip =
                    "Floating point number between 0 and 2. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://platform.openai.com/docs/api-reference/chat/create#chat-create-temperature\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Double temperature,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "top P",
                tooltip =
                    "Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://platform.openai.com/docs/api-reference/chat/create#chat-create-top_p\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Double topP) {}
  }
}
