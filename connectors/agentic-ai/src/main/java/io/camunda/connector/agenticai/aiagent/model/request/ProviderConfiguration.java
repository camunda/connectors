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
  record AnthropicProviderConfiguration(AnthropicConnection anthropic)
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
        @NotNull @Valid AnthropicAuthentication authentication,
        AnthropicModel model) {}

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
        @Valid ModelParameters parameters) {}
  }

  @TemplateSubType(id = BEDROCK_ID, label = "AWS Bedrock")
  record BedrockProviderConfiguration(BedrockConnection bedrock) implements ProviderConfiguration {

    @TemplateProperty(ignore = true)
    public static final String BEDROCK_ID = "bedrock";

    public record BedrockConnection(
        @TemplateProperty(
                group = "model",
                description = "Specify the AWS region",
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
        AwsAuthentication authentication,
        BedrockModel model) {}

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
        @Valid ModelParameters parameters) {}
  }

  @TemplateSubType(id = OPENAI_ID, label = "OpenAI")
  record OpenAiProviderConfiguration(OpenAiConnection openai) implements ProviderConfiguration {

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
        OpenAiAuthentication authentication,
        OpenAiModel model) {}

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
        @Valid ModelParameters parameters) {}
  }

  record ModelParameters(
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
              label = "Temperature",
              type = TemplateProperty.PropertyType.Number,
              feel = Property.FeelMode.required,
              optional = true)
          Double temperature,
      @Min(0)
          @TemplateProperty(
              group = "parameters",
              label = "top P",
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
