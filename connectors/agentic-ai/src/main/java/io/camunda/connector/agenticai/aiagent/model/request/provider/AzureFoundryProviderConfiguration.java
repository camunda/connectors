/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import static io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AZURE_AI_FOUNDRY_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.OpenAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.HttpUrl;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = AZURE_AI_FOUNDRY_ID, label = "Azure AI Foundry")
public record AzureFoundryProviderConfiguration(
    @Valid @NotNull AzureAiFoundryConnection azureAiFoundry) implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String AZURE_AI_FOUNDRY_ID = "azureAiFoundry";

  @Override
  public String providerType() {
    return AZURE_AI_FOUNDRY_ID;
  }

  public record AzureAiFoundryConnection(
      @NotBlank
          @HttpUrl
          @FEEL
          @TemplateProperty(
              group = "provider",
              description =
                  "Azure AI Foundry resource endpoint, e.g. <code>https://&lt;resource&gt;.services.ai.azure.com</code>. "
                      + "Anthropic models require the <code>services.ai.azure.com</code> FQDN. "
                      + "Details in the <a href=\"https://learn.microsoft.com/en-us/azure/foundry/\" target=\"_blank\">documentation</a>.",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String endpoint,
      @Valid @NotNull AzureAuthentication authentication,
      @Valid TimeoutConfiguration timeouts,
      @Valid @NotNull AzureAiFoundryModel model) {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "family")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = AnthropicModel.class, name = "anthropic"),
    @JsonSubTypes.Type(value = OpenAiModel.class, name = "openai")
  })
  @TemplateDiscriminatorProperty(
      label = "Model family",
      group = "model",
      name = "family",
      defaultValue = "anthropic",
      description = "Select which model family is deployed behind this Foundry endpoint.")
  public sealed interface AzureAiFoundryModel permits AnthropicModel, OpenAiModel {

    @TemplateSubType(id = "anthropic", label = "Anthropic (Claude)")
    record AnthropicModel(
        @NotBlank
            @TemplateProperty(
                id = "anthropic.deploymentName",
                group = "model",
                label = "Deployment name",
                description =
                    "The Azure Foundry deployment name (defaults to the Claude model ID like "
                        + "<code>claude-sonnet-4-6</code> unless customized).",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
                placeholder = "claude-sonnet-4-6",
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            String deploymentName,
        @Valid AnthropicModelParameters parameters)
        implements AzureAiFoundryModel {

      public record AnthropicModelParameters(
          @Min(0)
              @TemplateProperty(
                  id = "anthropic.maxTokens",
                  group = "model",
                  label = "Maximum tokens",
                  tooltip = "The maximum number of tokens per request to generate before stopping.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = FeelMode.required,
                  optional = true)
              Integer maxTokens,
          @Min(0)
              @TemplateProperty(
                  id = "anthropic.temperature",
                  group = "model",
                  label = "Temperature",
                  tooltip =
                      "Floating point number between 0 and 1. The higher the number, the more randomness will be injected into the response.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = FeelMode.required,
                  optional = true)
              Double temperature,
          @Min(0)
              @TemplateProperty(
                  id = "anthropic.topP",
                  group = "model",
                  label = "top P",
                  tooltip =
                      "Recommended for advanced use cases only (you usually only need to use temperature).",
                  type = TemplateProperty.PropertyType.Number,
                  feel = FeelMode.required,
                  optional = true)
              Double topP,
          @Min(0)
              @TemplateProperty(
                  group = "model",
                  label = "top K",
                  tooltip = "Integer greater than 0. Recommended for advanced use cases only.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = FeelMode.required,
                  optional = true)
              Integer topK) {}
    }

    @TemplateSubType(id = "openai", label = "OpenAI (GPT)")
    record OpenAiModel(
        @NotBlank
            @TemplateProperty(
                id = "openai.deploymentName",
                group = "model",
                label = "Deployment name",
                description = "The Azure Foundry deployment name for your OpenAI model.",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            String deploymentName,
        @Valid OpenAiModelParameters parameters)
        implements AzureAiFoundryModel {

      public record OpenAiModelParameters(
          @Min(0)
              @TemplateProperty(
                  id = "openai.maxTokens",
                  group = "model",
                  label = "Maximum tokens",
                  tooltip = "The maximum number of tokens per request to generate before stopping.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = FeelMode.required,
                  optional = true)
              Integer maxTokens,
          @Min(0)
              @TemplateProperty(
                  id = "openai.temperature",
                  group = "model",
                  label = "Temperature",
                  tooltip =
                      "Floating point number between 0 and 2. The higher the number, the more randomness will be injected into the response.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = FeelMode.required,
                  optional = true)
              Double temperature,
          @Min(0)
              @TemplateProperty(
                  id = "openai.topP",
                  group = "model",
                  label = "top P",
                  tooltip = "Recommended for advanced use cases only.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = FeelMode.required,
                  optional = true)
              Double topP) {}
    }
  }
}
