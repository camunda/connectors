/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import static io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.AnthropicProviderConfiguration.ANTHROPIC_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.AzureOpenAiProviderConfiguration.AZURE_OPENAI_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.BedrockProviderConfiguration.BEDROCK_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.OpenAiProviderConfiguration.OPENAI_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = ProviderConfiguration.AnthropicProviderConfiguration.class,
      name = ANTHROPIC_ID),
  @JsonSubTypes.Type(
      value = ProviderConfiguration.AzureOpenAiProviderConfiguration.class,
      name = AZURE_OPENAI_ID),
  @JsonSubTypes.Type(
      value = ProviderConfiguration.BedrockProviderConfiguration.class,
      name = BEDROCK_ID),
  @JsonSubTypes.Type(
      value = ProviderConfiguration.OpenAiProviderConfiguration.class,
      name = OPENAI_ID)
})
@TemplateDiscriminatorProperty(
    label = "Provider",
    group = "provider",
    name = "type",
    description = "Specify the LLM provider to use.",
    defaultValue = ANTHROPIC_ID)
public sealed interface ProviderConfiguration {

  @TemplateSubType(id = ANTHROPIC_ID, label = "Anthropic")
  record AnthropicProviderConfiguration(@Valid @NotNull AnthropicConnection anthropic)
      implements ProviderConfiguration {

    @TemplateProperty(ignore = true)
    public static final String ANTHROPIC_ID = "anthropic";

    public record AnthropicConnection(
        @TemplateProperty(
                group = "provider",
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
                group = "provider",
                label = "Anthropic API key",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                constraints = @PropertyConstraints(notEmpty = true))
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
                feel = Property.FeelMode.optional,
                defaultValue = "claude-3-5-sonnet-20240620",
                defaultValueType = TemplateProperty.DefaultValueType.String,
                constraints = @PropertyConstraints(notEmpty = true))
            String model,
        @Valid AnthropicModelParameters parameters) {

      public record AnthropicModelParameters(
          @Min(0)
              @TemplateProperty(
                  group = "model",
                  label = "Maximum tokens",
                  tooltip =
                      "The maximum number of tokens per request to generate before stopping. <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-max-tokens\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Integer maxTokens,
          @Min(0)
              @TemplateProperty(
                  group = "model",
                  label = "Temperature",
                  tooltip =
                      "Floating point number between 0 and 1. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-temperature\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Double temperature,
          @Min(0)
              @TemplateProperty(
                  group = "model",
                  label = "top P",
                  tooltip =
                      "Floating point number between 0 and 1. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-top-p\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Double topP,
          @Min(0)
              @TemplateProperty(
                  group = "model",
                  label = "top K",
                  tooltip =
                      "Integer greater than 0. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-top-k\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Integer topK) {}
    }
  }

  @TemplateSubType(id = AZURE_OPENAI_ID, label = "Azure OpenAI")
  record AzureOpenAiProviderConfiguration(@Valid @NotNull AzureOpenAiConnection azureOpenAi)
      implements ProviderConfiguration {

    @TemplateProperty(ignore = true)
    public static final String AZURE_OPENAI_ID = "azureOpenAi";

    public record AzureOpenAiConnection(
        @FEEL
            @TemplateProperty(
                group = "provider",
                description =
                    "Specify Azure OpenAI endpoint. Details in the <a href=\"https://learn.microsoft.com/en-us/azure/ai-foundry/openai/reference\" target=\"_blank\">documentation</a>.",
                constraints = @PropertyConstraints(notEmpty = true))
            String endpoint,
        @Valid @NotNull AzureAuthentication authentication,
        @Valid @NotNull AzureOpenAiModel model) {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
      @JsonSubTypes.Type(
          value =
              AzureOpenAiProviderConfiguration.AzureAuthentication.AzureApiKeyAuthentication.class,
          name = "apiKey"),
      @JsonSubTypes.Type(
          value =
              AzureOpenAiProviderConfiguration.AzureAuthentication
                  .AzureClientCredentialsAuthentication.class,
          name = "clientCredentials")
    })
    @TemplateDiscriminatorProperty(
        label = "Authentication",
        group = "provider",
        name = "type",
        defaultValue = "apiKey",
        description =
            "Specify the Azure OpenAI authentication strategy. Learn more at the <a href=\"https://docs.langchain4j.dev/integrations/language-models/azure-open-ai\" target=\"_blank\">documentation page</a>")
    public sealed interface AzureAuthentication {
      @TemplateSubType(id = "apiKey", label = "API key")
      record AzureApiKeyAuthentication(
          @NotBlank
              @TemplateProperty(
                  group = "provider",
                  label = "API key",
                  type = TemplateProperty.PropertyType.String,
                  feel = Property.FeelMode.optional,
                  constraints = @PropertyConstraints(notEmpty = true))
              String apiKey)
          implements AzureAuthentication {

        @Override
        public @NotNull String toString() {
          return "AzureApiKeyAuthentication{apiKey=[REDACTED]}";
        }
      }

      @TemplateSubType(id = "clientCredentials", label = "Client credentials")
      record AzureClientCredentialsAuthentication(
          @NotBlank
              @TemplateProperty(
                  group = "provider",
                  label = "Client ID",
                  description = "ID of a Microsoft Entra application",
                  type = TemplateProperty.PropertyType.String,
                  feel = Property.FeelMode.optional,
                  constraints = @PropertyConstraints(notEmpty = true))
              String clientId,
          @NotBlank
              @TemplateProperty(
                  group = "provider",
                  label = "Client secret",
                  description = "Secret of a Microsoft Entra application",
                  type = TemplateProperty.PropertyType.String,
                  feel = Property.FeelMode.optional,
                  constraints = @PropertyConstraints(notEmpty = true))
              String clientSecret,
          @NotBlank
              @TemplateProperty(
                  group = "provider",
                  label = "Tenant ID",
                  description =
                      "ID of a Microsoft Entra tenant. Details in the <a href=\"https://learn.microsoft.com/en-us/entra/fundamentals/how-to-find-tenant\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.String,
                  feel = Property.FeelMode.optional)
              String tenantId,
          @TemplateProperty(
                  group = "provider",
                  label = "Authority host",
                  description =
                      "Authority host URL for the Microsoft Entra application. Defaults to <code>https://login.microsoftonline.com</code>. This can also contain an OAuth 2.0 token endpoint.",
                  type = TemplateProperty.PropertyType.String,
                  feel = Property.FeelMode.optional,
                  optional = true)
              String authorityHost)
          implements AzureAuthentication {

        @Override
        public String toString() {
          return "AzureClientCredentialsAuthentication{clientId=%s, clientSecret=[REDACTED]}, tenantId=%s, authorityHost=%s}"
              .formatted(clientId, tenantId, authorityHost);
        }
      }
    }

    public record AzureOpenAiModel(
        @NotBlank
            @TemplateProperty(
                group = "model",
                label = "Model deployment name",
                description =
                    "Specify the model deployment name. Details in the <a href=\"https://learn.microsoft.com/en-us/azure/ai-foundry/openai/reference\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                constraints = @PropertyConstraints(notEmpty = true))
            String deploymentName,
        @Valid
            ProviderConfiguration.AzureOpenAiProviderConfiguration.AzureOpenAiModel
                    .AzureOpenAiModelParameters
                parameters) {

      public record AzureOpenAiModelParameters(
          @Min(0)
              @TemplateProperty(
                  group = "model",
                  label = "Maximum tokens",
                  tooltip =
                      "The maximum number of tokens per request to generate before stopping. <br><br>Details in the <a href=\"https://learn.microsoft.com/en-us/azure/ai-foundry/openai/reference#request-body\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Integer maxTokens,
          @Min(0)
              @TemplateProperty(
                  group = "model",
                  label = "Temperature",
                  tooltip =
                      "Floating point number between 0 and 2. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://learn.microsoft.com/en-us/azure/ai-foundry/openai/reference#request-body\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Double temperature,
          @Min(0)
              @TemplateProperty(
                  group = "model",
                  label = "top P",
                  tooltip =
                      "Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://learn.microsoft.com/en-us/azure/ai-foundry/openai/reference#request-body\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Double topP) {}
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
                group = "provider",
                description = "Specify the AWS region (example: <code>eu-west-1</code>)",
                constraints = @PropertyConstraints(notEmpty = true))
            String region,
        @FEEL
            @TemplateProperty(
                group = "provider",
                description = "Specify endpoint if need to use a custom API endpoint",
                type = TemplateProperty.PropertyType.Hidden,
                feel = Property.FeelMode.disabled,
                optional = true)
            String endpoint,
        @Valid @NotNull AwsAuthentication authentication,
        @Valid @NotNull BedrockModel model) {

      @AssertFalse(message = "AWS default credentials chain is not supported on SaaS")
      public boolean isDefaultCredentialsChainUsedInSaaS() {
        return System.getenv().containsKey("CAMUNDA_CONNECTOR_RUNTIME_SAAS")
            && authentication instanceof AwsAuthentication.AwsDefaultCredentialsChainAuthentication;
      }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
      @JsonSubTypes.Type(
          value = AwsAuthentication.AwsStaticCredentialsAuthentication.class,
          name = "credentials"),
      @JsonSubTypes.Type(
          value = AwsAuthentication.AwsDefaultCredentialsChainAuthentication.class,
          name = "defaultCredentialsChain"),
    })
    @TemplateDiscriminatorProperty(
        label = "Authentication",
        group = "provider",
        name = "type",
        defaultValue = "credentials",
        description =
            "Specify the AWS authentication strategy. Learn more at the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-bedrock/#authentication\" target=\"_blank\">documentation page</a>")
    public sealed interface AwsAuthentication {
      @TemplateSubType(id = "credentials", label = "Credentials")
      record AwsStaticCredentialsAuthentication(
          @TemplateProperty(
                  group = "provider",
                  label = "Access key",
                  description =
                      "Provide an IAM access key tailored to a user, equipped with the necessary permissions")
              @NotBlank
              String accessKey,
          @TemplateProperty(
                  group = "provider",
                  label = "Secret key",
                  description =
                      "Provide a secret key of a user with permissions to invoke specified AWS Lambda function")
              @NotBlank
              String secretKey)
          implements AwsAuthentication {
        @Override
        public String toString() {
          return "AwsStaticCredentialsAuthentication{accessKey=[REDACTED], secretKey=[REDACTED]}";
        }
      }

      @TemplateSubType(
          id = "defaultCredentialsChain",
          label = "Default Credentials Chain (Hybrid/Self-Managed only)")
      record AwsDefaultCredentialsChainAuthentication() implements AwsAuthentication {}
    }

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
                  group = "model",
                  label = "Maximum tokens",
                  tooltip =
                      "The maximum number of tokens per request to allow in the generated response. <br><br>Details in the <a href=\"https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_InferenceConfiguration.html\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Integer maxTokens,
          @Min(0)
              @TemplateProperty(
                  group = "model",
                  label = "Temperature",
                  tooltip =
                      "Floating point number between 0 and 1. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_InferenceConfiguration.html\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Double temperature,
          @Min(0)
              @TemplateProperty(
                  group = "model",
                  label = "top P",
                  tooltip =
                      "Floating point number between 0 and 1. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_InferenceConfiguration.html\" target=\"_blank\">documentation</a>.",
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
        @Valid @NotNull OpenAiAuthentication authentication,
        @TemplateProperty(
                group = "provider",
                label = "Custom API endpoint",
                description = "Optional custom API endpoint.",
                tooltip =
                    "Configure a custom OpenAI compatible API endpoint to use the connector with an OpenAI compatible API. "
                        + "Typically ends in <code>/v1</code>.",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                optional = true)
            String endpoint,
        @FEEL
            @TemplateProperty(
                group = "provider",
                label = "Custom headers",
                description = "Map of custom HTTP headers to add to the request.",
                feel = Property.FeelMode.required,
                optional = true)
            Map<String, String> headers,
        @Valid @NotNull OpenAiModel model) {}

    public record OpenAiAuthentication(
        @NotBlank
            @TemplateProperty(
                group = "provider",
                label = "OpenAI API key",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                constraints = @PropertyConstraints(notEmpty = true))
            String apiKey,
        @TemplateProperty(
                group = "provider",
                label = "Organization ID",
                description =
                    "For members of multiple organizations. Details in the <a href=\"https://platform.openai.com/docs/api-reference/authentication\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                optional = true)
            String organizationId,
        @TemplateProperty(
                group = "provider",
                label = "Project ID",
                description =
                    "For accounts with multiple projects. Details in the <a href=\"https://platform.openai.com/docs/api-reference/authentication\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
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
                feel = Property.FeelMode.optional,
                defaultValue = "gpt-4o",
                defaultValueType = TemplateProperty.DefaultValueType.String,
                constraints = @PropertyConstraints(notEmpty = true))
            String model,
        @Valid OpenAiModelParameters parameters) {

      public record OpenAiModelParameters(
          @Min(0)
              @TemplateProperty(
                  group = "model",
                  label = "Maximum completion tokens",
                  tooltip =
                      "The maximum number of tokens per request to generate before stopping. <br><br>Details in the <a href=\"https://platform.openai.com/docs/api-reference/chat/create#chat-create-max_completion_tokens\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Integer maxCompletionTokens,
          @Min(0)
              @TemplateProperty(
                  group = "model",
                  label = "Temperature",
                  tooltip =
                      "Floating point number between 0 and 2. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://platform.openai.com/docs/api-reference/chat/create#chat-create-temperature\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Double temperature,
          @Min(0)
              @TemplateProperty(
                  group = "model",
                  label = "top P",
                  tooltip =
                      "Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://platform.openai.com/docs/api-reference/chat/create#chat-create-top_p\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = Property.FeelMode.required,
                  optional = true)
              Double topP) {}
    }
  }
}
