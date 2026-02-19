/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import static io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AZURE_OPENAI_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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

@TemplateSubType(id = AZURE_OPENAI_ID, label = "Azure OpenAI")
public record AzureOpenAiProviderConfiguration(@Valid @NotNull AzureOpenAiConnection azureOpenAi)
    implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String AZURE_OPENAI_ID = "azureOpenAi";

  public record AzureOpenAiConnection(
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

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(
        value = AzureAuthentication.AzureApiKeyAuthentication.class,
        name = "apiKey"),
    @JsonSubTypes.Type(
        value = AzureAuthentication.AzureClientCredentialsAuthentication.class,
        name = "clientCredentials")
  })
  @TemplateDiscriminatorProperty(
      label = "Authentication",
      group = "provider",
      name = "type",
      defaultValue = "apiKey",
      description = "Specify the Azure OpenAI authentication strategy.")
  public sealed interface AzureAuthentication {
    @TemplateSubType(id = "apiKey", label = "API key")
    record AzureApiKeyAuthentication(
        @NotBlank
            @TemplateProperty(
                group = "provider",
                label = "API key",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
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
                feel = FeelMode.optional,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            String clientId,
        @NotBlank
            @TemplateProperty(
                group = "provider",
                label = "Client secret",
                description = "Secret of a Microsoft Entra application",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            String clientSecret,
        @NotBlank
            @TemplateProperty(
                group = "provider",
                label = "Tenant ID",
                description =
                    "ID of a Microsoft Entra tenant. Details in the <a href=\"https://learn.microsoft.com/en-us/entra/fundamentals/how-to-find-tenant\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional)
            String tenantId,
        @TemplateProperty(
                group = "provider",
                label = "Authority host",
                description =
                    "Authority host URL for the Microsoft Entra application. Defaults to <code>https://login.microsoftonline.com</code>. This can also contain an OAuth 2.0 token endpoint.",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
                optional = true)
            String authorityHost)
        implements AzureAuthentication {

      @Override
      public String toString() {
        return "AzureClientCredentialsAuthentication{clientId=%s, clientSecret=[REDACTED], tenantId=%s, authorityHost=%s}"
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
