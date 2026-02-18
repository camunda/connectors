/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import static io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration.OPENAI_COMPATIBLE_ID;

import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@TemplateSubType(id = OPENAI_COMPATIBLE_ID, label = "OpenAI Compatible")
public record OpenAiCompatibleProviderConfiguration(
    @Valid @NotNull OpenAiCompatibleConnection openaiCompatible) implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String OPENAI_COMPATIBLE_ID = "openaiCompatible";

  public record OpenAiCompatibleConnection(
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "API endpoint",
              tooltip = "Specify an endpoint to use the connector with an OpenAI compatible API. ",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String endpoint,
      @Valid OpenAiCompatibleAuthentication authentication,
      @FEEL
          @TemplateProperty(
              group = "provider",
              label = "Headers",
              description = "Map of HTTP headers to add to the request.",
              feel = FeelMode.required,
              optional = true)
          Map<String, String> headers,
      @FEEL
          @TemplateProperty(
              group = "provider",
              label = "Query Parameters",
              description = "Map of query parameters to add to the request URL.",
              feel = FeelMode.required,
              optional = true)
          @Valid
          Map<@NotBlank String, String> queryParameters,
      @Valid TimeoutConfiguration timeouts,
      @Valid @NotNull OpenAiCompatibleModel model) {}

  public record OpenAiCompatibleAuthentication(
      @TemplateProperty(
              group = "provider",
              label = "API key",
              tooltip =
                  "Leave blank if using HTTP headers for authentication.<br>If an Authorization header is specified in the headers, then the API key is ignored.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              optional = true)
          String apiKey) {

    @Override
    public String toString() {
      return "OpenAiCompatibleAuthentication{apiKey=[REDACTED]}";
    }
  }

  public record OpenAiCompatibleModel(
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
      @Valid OpenAiCompatibleModel.OpenAiCompatibleModelParameters parameters) {

    public record OpenAiCompatibleModelParameters(
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
            Double topP,
        @FEEL
            @TemplateProperty(
                group = "model",
                label = "Custom parameters",
                description = "Map of additional request parameters to include.",
                feel = FeelMode.required,
                optional = true)
            Map<String, Object> customParameters) {}
  }
}
