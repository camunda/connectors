/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.models;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DefaultValueType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@TemplateSubType(
    label = "Azure OpenAI",
    id = AzureOpenAiEmbeddingModelProvider.AZURE_OPEN_AI_MODEL_PROVIDER)
public record AzureOpenAiEmbeddingModelProvider(@Valid @NotNull Configuration azureOpenAi)
    implements EmbeddingModelProvider {

  @TemplateProperty(ignore = true)
  public static final String AZURE_OPEN_AI_MODEL_PROVIDER = "azureOpenAiModelProvider";

  public record Configuration(
      @NotBlank
          @TemplateProperty(
              group = "embeddingModel",
              label = "Endpoint",
              description =
                  "Specify Azure OpenAI endpoint. Details in the <a href=\"https://learn.microsoft.com/en-us/azure/ai-foundry/openai/reference\" target=\"_blank\">documentation</a>.",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String endpoint,
      @Valid @NotNull AzureAuthentication authentication,
      @NotBlank
          @TemplateProperty(
              group = "embeddingModel",
              label = "Model deployment name",
              description =
                  "Specify the model deployment name. Details in the <a href=\"https://learn.microsoft.com/en-us/azure/ai-foundry/openai/reference\" target=\"_blank\">documentation</a>.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String deploymentName,
      @TemplateProperty(
              group = "embeddingModel",
              label = "Embedding dimensions",
              description =
                  "The size of the vector used to represent data. If not specified, the default model dimensions are used. Details in the <a href=\"https://platform.openai.com/docs/guides/embeddings\" target=\"_blank\">documentation</a>.",
              feel = FeelMode.required,
              type = TemplateProperty.PropertyType.Number,
              optional = true)
          Integer dimensions,
      @TemplateProperty(
              group = "embeddingModel",
              label = "Max retries",
              description = "Max retries",
              defaultValueType = DefaultValueType.Number,
              defaultValue = "3",
              optional = true)
          Integer maxRetries,
      @FEEL
          @TemplateProperty(
              group = "embeddingModel",
              label = "Custom headers",
              description = "Map of custom HTTP headers to add to the request.",
              feel = FeelMode.required,
              optional = true)
          Map<String, String> customHeaders) {}

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
      group = "embeddingModel",
      name = "type",
      defaultValue = "apiKey",
      description = "Specify the Azure OpenAI authentication strategy.")
  public sealed interface AzureAuthentication {
    @TemplateSubType(id = "apiKey", label = "API key")
    record AzureApiKeyAuthentication(
        @NotBlank
            @TemplateProperty(
                group = "embeddingModel",
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
                group = "embeddingModel",
                label = "Client ID",
                description = "ID of a Microsoft Entra application",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            String clientId,
        @NotBlank
            @TemplateProperty(
                group = "embeddingModel",
                label = "Client secret",
                description = "Secret of a Microsoft Entra application",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            String clientSecret,
        @NotBlank
            @TemplateProperty(
                group = "embeddingModel",
                label = "Tenant ID",
                description =
                    "ID of a Microsoft Entra tenant. Details in the <a href=\"https://learn.microsoft.com/en-us/entra/fundamentals/how-to-find-tenant\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional)
            String tenantId,
        @TemplateProperty(
                group = "embeddingModel",
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
}
