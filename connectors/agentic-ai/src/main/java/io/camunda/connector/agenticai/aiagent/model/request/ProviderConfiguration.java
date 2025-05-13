/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import static io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.AnthropicProviderConfiguration.ANTHROPIC_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.BedrockProviderConfiguration.BEDROCK_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.OpenAiProviderConfiguration.OPENAI_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.aws.model.impl.AwsAuthentication;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = ProviderConfiguration.AnthropicProviderConfiguration.class,
      name = ANTHROPIC_ID),
  @JsonSubTypes.Type(
      value = ProviderConfiguration.BedrockProviderConfiguration.class,
      name = BEDROCK_ID),
  @JsonSubTypes.Type(
      value = ProviderConfiguration.OpenAiProviderConfiguration.class,
      name = OPENAI_ID)
})
@TemplateDiscriminatorProperty(
    label = "Model Provider",
    group = "model",
    name = "type",
    description = "Specify the LLM provider to use.",
    defaultValue = ANTHROPIC_ID)
public sealed interface ProviderConfiguration
    permits ProviderConfiguration.AnthropicProviderConfiguration,
        ProviderConfiguration.BedrockProviderConfiguration,
        ProviderConfiguration.OpenAiProviderConfiguration {

  @TemplateSubType(id = ANTHROPIC_ID, label = "Anthropic")
  record AnthropicProviderConfiguration(@Valid @NotNull AnthropicConnection anthropic)
      implements ProviderConfiguration {

    @TemplateProperty(ignore = true)
    public static final String ANTHROPIC_ID = "anthropic";

    public record AnthropicConnection(
        @TemplateProperty(
                group = "model",
                description = "Specify endpoint if need to use a custom API endpoint",
                type = TemplateProperty.PropertyType.Hidden,
                feel = Property.FeelMode.disabled,
                optional = true)
            String endpoint,
        @Valid @NotNull AnthropicAuthentication authentication,
        @Valid @NotNull AnthropicModel model) {}

    public record AnthropicAuthentication(
        @NotBlank
            @TemplateProperty(
                group = "authentication",
                label = "Anthropic API Key",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                constraints = @PropertyConstraints(notEmpty = true))
            String apiKey) {}

    public record AnthropicModel(
        @NotBlank
            @TemplateProperty(
                group = "model",
                label = "Model",
                description =
                    "Specify the model ID. Details in the <a href=\"https://docs.anthropic.com/en/docs/about-claude/models/all-models\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                defaultValue = "claude-3-5-sonnet-20240620",
                defaultValueType = TemplateProperty.DefaultValueType.String,
                constraints = @PropertyConstraints(notEmpty = true))
            String model,
        @Valid AnthropicModelParameters parameters) {

      public record AnthropicModelParameters(
          @Min(0)
              @TemplateProperty(
                  group = "parameters",
                  label = "Maximum Tokens",
                  description =
                      "The maximum number of tokens per request to generate before stopping. Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-max-tokens\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Integer maxTokens,
          @Min(0)
              @TemplateProperty(
                  group = "parameters",
                  label = "Temperature",
                  tooltip =
                      "Floating point number between <code>0</code> and <code>1</code>. The higher the number, the more randomness will be injected into the response. Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-temperature\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Double temperature,
          @Min(0)
              @TemplateProperty(
                  group = "parameters",
                  label = "top P",
                  tooltip =
                      "Floating point number between <code>0</code> and <code>1</code>. Recommended for advanced use cases only, you usually only need to use <code>temperature</code>. Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-top-p\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Double topP,
          @Min(0)
              @TemplateProperty(
                  group = "parameters",
                  label = "top K",
                  tooltip =
                      "Integer greater than <code>0</code>. Recommended for advanced use cases only, you usually only need to use <code>temperature</code>. Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-top-k\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Integer topK) {}
    }
  }

  @TemplateSubType(id = BEDROCK_ID, label = "AWS Bedrock")
  record BedrockProviderConfiguration(@Valid @NotNull BedrockConnection bedrock)
      implements ProviderConfiguration {

    @TemplateProperty(ignore = true)
    public static final String BEDROCK_ID = "bedrock";

    public record BedrockConnection(
        @NotBlank
            @TemplateProperty(
                group = "model",
                description = "Specify the AWS region (example: <code>eu-west-1</code>)",
                constraints = @PropertyConstraints(notEmpty = true))
            String region,
        @FEEL
            @TemplateProperty(
                group = "model",
                description = "Specify endpoint if need to use a custom API endpoint",
                type = TemplateProperty.PropertyType.Hidden,
                feel = Property.FeelMode.disabled,
                optional = true)
            String endpoint,
        @Valid @NotNull AwsAuthentication authentication,
        @Valid @NotNull BedrockModel model) {}

    public record BedrockModel(
        @NotBlank
            @TemplateProperty(
                group = "model",
                label = "Model",
                description =
                    "Specify the model ID. Details in the <a href=\"https://docs.aws.amazon.com/bedrock/latest/userguide/model-ids.html\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                defaultValue = "anthropic.claude-3-5-sonnet-20240620-v1:0",
                defaultValueType = TemplateProperty.DefaultValueType.String,
                constraints = @PropertyConstraints(notEmpty = true))
            String model,
        @Valid BedrockModelParameters parameters) {

      public record BedrockModelParameters(
          @Min(0)
              @TemplateProperty(
                  group = "parameters",
                  label = "Maximum Tokens",
                  tooltip =
                      "The maximum number of tokens to allow in the generated response. Details in the <a href=\"https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_InferenceConfiguration.html\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Integer maxTokens,
          @Min(0)
              @TemplateProperty(
                  group = "parameters",
                  label = "Temperature",
                  tooltip =
                      "Floating point number between <code>0</code> and <code>1</code>. The higher the number, the more randomness will be injected into the response. Details in the <a href=\"https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_InferenceConfiguration.html\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Double temperature,
          @Min(0)
              @TemplateProperty(
                  group = "parameters",
                  label = "top P",
                  tooltip =
                      "Floating point number between <code>0</code> and <code>1</code>. Recommended for advanced use cases only, you usually only need to use <code>temperature</code>. Details in the <a href=\"https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_InferenceConfiguration.html\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Double topP) {}
    }
  }

  @TemplateSubType(id = OPENAI_ID, label = "OpenAI")
  record OpenAiProviderConfiguration(@Valid @NotNull OpenAiConnection openai)
      implements ProviderConfiguration {

    @TemplateProperty(ignore = true)
    public static final String OPENAI_ID = "openai";

    public record OpenAiConnection(
        @TemplateProperty(
                group = "model",
                description = "Specify endpoint if need to use a custom API endpoint",
                type = TemplateProperty.PropertyType.Hidden,
                feel = Property.FeelMode.disabled,
                optional = true)
            String endpoint,
        @Valid @NotNull OpenAiAuthentication authentication,
        @Valid @NotNull OpenAiModel model) {}

    public record OpenAiAuthentication(
        @NotBlank
            @TemplateProperty(
                group = "authentication",
                label = "OpenAI API Key",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                constraints = @PropertyConstraints(notEmpty = true))
            String apiKey,
        @TemplateProperty(
                group = "authentication",
                label = "Organization",
                description =
                    "For members of multiple organizations. Details in the <a href=\"https://platform.openai.com/docs/api-reference/requesting-organization\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                optional = true)
            String organization,
        @TemplateProperty(
                group = "authentication",
                label = "Project",
                description = "For members with multiple projects.",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                optional = true)
            String project) {}

    public record OpenAiModel(
        @NotBlank
            @TemplateProperty(
                group = "model",
                label = "Model",
                description =
                    "Specify the model ID. Details in the <a href=\"https://platform.openai.com/docs/models\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                defaultValue = "gpt-4o",
                defaultValueType = TemplateProperty.DefaultValueType.String,
                constraints = @PropertyConstraints(notEmpty = true))
            String model,
        @Valid OpenAiModelParameters parameters) {

      public record OpenAiModelParameters(
          @Min(0)
              @TemplateProperty(
                  group = "parameters",
                  label = "Maximum Output Tokens",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Integer maxOutputTokens,
          @Min(0)
              @TemplateProperty(
                  group = "parameters",
                  label = "Maximum Completion Tokens",
                  description =
                      "The maximum number of tokens per request to generate before stopping. Details in the <a href=\"https://platform.openai.com/docs/api-reference/chat/create#chat-create-max_completion_tokens\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Integer maxCompletionTokens,
          @Min(0)
              @TemplateProperty(
                  group = "parameters",
                  label = "Temperature",
                  tooltip =
                      "Floating point number between <code>0</code> and <code>2</code>. The higher the number, the more randomness will be injected into the response. Details in the <a href=\"https://platform.openai.com/docs/api-reference/chat/create#chat-create-temperature\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Double temperature,
          @Min(0)
              @TemplateProperty(
                  group = "parameters",
                  label = "top P",
                  tooltip =
                      "Recommended for advanced use cases only, you usually only need to use <code>temperature</code>. Details in the <a href=\"https://platform.openai.com/docs/api-reference/chat/create#chat-create-top_p\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Double topP,
          @Min(0)
              @TemplateProperty(
                  group = "parameters",
                  label = "top K",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Integer topK) {}
    }
  }
}
