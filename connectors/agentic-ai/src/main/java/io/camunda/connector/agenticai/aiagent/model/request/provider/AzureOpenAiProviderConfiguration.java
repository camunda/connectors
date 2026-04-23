/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import static io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AZURE_OPENAI_ID;

import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.HttpUrl;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = AZURE_OPENAI_ID, label = "Azure OpenAI")
public record AzureOpenAiProviderConfiguration(@Valid @NotNull AzureOpenAiConnection azureOpenAi)
    implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String AZURE_OPENAI_ID = "azureOpenAi";

  @Override
  public String providerType() {
    return AZURE_OPENAI_ID;
  }

  public record AzureOpenAiConnection(
      @NotBlank
          @HttpUrl
          @FEEL
          @TemplateProperty(
              group = "provider",
              description =
                  "Specify Azure OpenAI endpoint. Details in the <a href=\"https://learn.microsoft.com/en-us/azure/ai-foundry/openai/reference\" target=\"_blank\">documentation</a>.",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String endpoint,
      @Valid @NotNull AzureAuthentication authentication,
      @Valid TimeoutConfiguration timeouts,
      @Valid @NotNull AzureOpenAiModel model) {}

  public record AzureOpenAiModel(
      @NotBlank
          @TemplateProperty(
              group = "model",
              label = "Model deployment name",
              description =
                  "Specify the model deployment name. Details in the <a href=\"https://learn.microsoft.com/en-us/azure/ai-foundry/openai/reference\" target=\"_blank\">documentation</a>.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String deploymentName,
      @Valid AzureOpenAiModel.AzureOpenAiModelParameters parameters) {

    public record AzureOpenAiModelParameters(
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "Maximum tokens",
                tooltip =
                    "The maximum number of tokens per request to generate before stopping. <br><br>Details in the <a href=\"https://learn.microsoft.com/en-us/azure/ai-foundry/openai/reference#request-body\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Integer maxTokens,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "Temperature",
                tooltip =
                    "Floating point number between 0 and 2. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://learn.microsoft.com/en-us/azure/ai-foundry/openai/reference#request-body\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Double temperature,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "top P",
                tooltip =
                    "Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://learn.microsoft.com/en-us/azure/ai-foundry/openai/reference#request-body\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Double topP) {}
  }
}
