/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import static io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.ANTHROPIC_ID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.HttpUrl;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = ANTHROPIC_ID, label = "Anthropic")
public record AnthropicProviderConfiguration(@Valid @NotNull AnthropicConnection anthropic)
    implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String ANTHROPIC_ID = "anthropic";

  @Override
  public String providerType() {
    return ANTHROPIC_ID;
  }

  public enum AnthropicBackend {
    @JsonProperty("direct")
    DIRECT,

    @JsonProperty("bedrock")
    BEDROCK,

    @JsonProperty("vertex")
    VERTEX,

    @JsonProperty("foundry")
    FOUNDRY
  }

  public record AnthropicConnection(
      @HttpUrl
          @TemplateProperty(
              group = "provider",
              description = "Optional custom API endpoint",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              optional = true)
          String endpoint,
      @TemplateProperty(ignore = true) AnthropicBackend backend,
      @Valid @NotNull AnthropicAuthentication authentication,
      @Valid TimeoutConfiguration timeouts,
      @Valid @NotNull AnthropicModel model) {

    public AnthropicConnection {
      if (backend == null) {
        backend = AnthropicBackend.DIRECT;
      }
    }

    @AssertFalse(
        message = "Client credentials authentication is only supported for the FOUNDRY backend")
    public boolean isClientCredentialsUsedWithNonFoundryBackend() {
      return authentication
              instanceof AnthropicAuthentication.AnthropicClientCredentialsAuthentication
          && backend != AnthropicBackend.FOUNDRY;
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(
        value = AnthropicAuthentication.AnthropicApiKeyAuthentication.class,
        name = "apiKey"),
    @JsonSubTypes.Type(
        value = AnthropicAuthentication.AnthropicClientCredentialsAuthentication.class,
        name = "clientCredentials")
  })
  @TemplateDiscriminatorProperty(
      label = "Authentication",
      group = "provider",
      name = "type",
      defaultValue = "apiKey",
      description = "Specify the Anthropic authentication strategy.")
  public sealed interface AnthropicAuthentication {
    @TemplateSubType(id = "apiKey", label = "API key")
    record AnthropicApiKeyAuthentication(
        @NotBlank
            @TemplateProperty(
                group = "provider",
                label = "Anthropic API key",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            String apiKey)
        implements AnthropicAuthentication {

      @Override
      public @NotNull String toString() {
        return "AnthropicApiKeyAuthentication{apiKey=[REDACTED]}";
      }
    }

    @TemplateSubType(id = "clientCredentials", label = "Client credentials")
    record AnthropicClientCredentialsAuthentication(
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
        implements AnthropicAuthentication {

      @Override
      public String toString() {
        return "AnthropicClientCredentialsAuthentication{clientId=%s, clientSecret=[REDACTED], tenantId=%s, authorityHost=%s}"
            .formatted(clientId, tenantId, authorityHost);
      }
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
              defaultValue = "",
              defaultValueType = TemplateProperty.DefaultValueType.String,
              placeholder = "claude-sonnet-4-6",
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
