/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import static io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OPENAI_ID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.HttpUrl;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@TemplateSubType(id = OPENAI_ID, label = "OpenAI")
public record OpenAiProviderConfiguration(@Valid @NotNull OpenAiConnection openai)
    implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String OPENAI_ID = "openai";

  @Override
  public String providerType() {
    return OPENAI_ID;
  }

  public enum OpenAiBackend {
    @JsonProperty("openai")
    OPENAI,

    @JsonProperty("foundry")
    FOUNDRY,

    @JsonProperty("custom")
    CUSTOM
  }

  public record OpenAiConnection(
      @TemplateProperty(ignore = true) OpenAiBackend backend,
      @Valid @NotNull OpenAiAuthentication authentication,
      @Valid TimeoutConfiguration timeouts,
      @Valid @NotNull OpenAiModel model,
      @TemplateProperty(
              group = "provider",
              label = "API",
              description =
                  "Which OpenAI API family to use. Chat Completions is the default for backward compatibility; Responses is the newer endpoint with structured input/output items.",
              type = TemplateProperty.PropertyType.Dropdown,
              choices = {
                @TemplateProperty.DropdownPropertyChoice(
                    label = "Chat Completions (default)",
                    value = "completions"),
                @TemplateProperty.DropdownPropertyChoice(label = "Responses", value = "responses")
              },
              feel = FeelMode.disabled,
              optional = true,
              defaultValue = "completions",
              defaultValueType = TemplateProperty.DefaultValueType.String)
          ApiFamily apiFamily,
      @HttpUrl
          @TemplateProperty(
              group = "provider",
              label = "Endpoint",
              description =
                  "Optional. Override the default OpenAI base URL (e.g. for an OpenAI proxy or gateway). Leave blank to use the SDK default.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              optional = true)
          String endpoint,
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
          Map<@NotBlank String, String> queryParameters) {

    public OpenAiConnection {
      if (backend == null) {
        backend = OpenAiBackend.OPENAI;
      }
      if (apiFamily == null) {
        apiFamily = ApiFamily.COMPLETIONS;
      }
    }

    /** Convenience constructor used by existing call sites that pre-date the backend field. */
    public OpenAiConnection(
        OpenAiAuthentication authentication, TimeoutConfiguration timeouts, OpenAiModel model) {
      this(null, authentication, timeouts, model, ApiFamily.COMPLETIONS, null, null, null);
    }

    @AssertFalse(
        message = "Client credentials authentication is only supported for the FOUNDRY backend")
    public boolean isClientCredentialsUsedWithNonFoundryBackend() {
      return authentication instanceof OpenAiAuthentication.OpenAiClientCredentialsAuthentication
          && backend != OpenAiBackend.FOUNDRY;
    }

    @AssertFalse(message = "Endpoint is required for FOUNDRY and CUSTOM backends")
    public boolean isEndpointMissingForBackendThatRequiresIt() {
      return (backend == OpenAiBackend.FOUNDRY || backend == OpenAiBackend.CUSTOM)
          && (endpoint == null || endpoint.isBlank());
    }
  }

  public enum ApiFamily {
    @com.fasterxml.jackson.annotation.JsonProperty("completions")
    COMPLETIONS,
    @com.fasterxml.jackson.annotation.JsonProperty("responses")
    RESPONSES
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(
        value = OpenAiAuthentication.OpenAiApiKeyAuthentication.class,
        name = "apiKey"),
    @JsonSubTypes.Type(
        value = OpenAiAuthentication.OpenAiClientCredentialsAuthentication.class,
        name = "clientCredentials")
  })
  @TemplateDiscriminatorProperty(
      label = "Authentication",
      group = "provider",
      name = "type",
      defaultValue = "apiKey",
      description = "Specify the OpenAI authentication strategy.")
  public sealed interface OpenAiAuthentication {

    @TemplateSubType(id = "apiKey", label = "API key")
    record OpenAiApiKeyAuthentication(
        @TemplateProperty(
                group = "provider",
                label = "API key",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
                optional = true)
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
            String projectId)
        implements OpenAiAuthentication {

      @Override
      public String toString() {
        return "OpenAiApiKeyAuthentication{apiKey=[REDACTED], organizationId=%s, projectId=%s}"
            .formatted(organizationId, projectId);
      }
    }

    @TemplateSubType(id = "clientCredentials", label = "Client credentials")
    record OpenAiClientCredentialsAuthentication(
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
        implements OpenAiAuthentication {

      @Override
      public String toString() {
        return "OpenAiClientCredentialsAuthentication{clientId=%s, clientSecret=[REDACTED], tenantId=%s, authorityHost=%s}"
            .formatted(clientId, tenantId, authorityHost);
      }
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
