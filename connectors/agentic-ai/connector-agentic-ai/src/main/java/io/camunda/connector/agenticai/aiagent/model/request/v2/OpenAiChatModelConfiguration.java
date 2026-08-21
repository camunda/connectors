/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi.COMPLETIONS_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi.RESPONSES_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend.OPENAI_API_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend.CUSTOM_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiFoundryBackend.FOUNDRY_ID;
import static io.camunda.connector.agenticai.aiagent.util.LoggingSupport.redactValues;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.HttpUrl;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.aiagent.util.ConnectorUtils;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@TemplateSubType(id = OpenAiChatModelConfiguration.OPENAI_ID, label = "OpenAI")
public record OpenAiChatModelConfiguration(@Valid @NotNull OpenAiConnection openai)
    implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String OPENAI_ID = "openai";

  @Override
  public String provider() {
    return OPENAI_ID;
  }

  @Override
  public String model() {
    return openai.model().model();
  }

  /** All OpenAI-specific configuration, nested under the {@code openai} wire key. */
  public record OpenAiConnection(
      @Valid @NotNull OpenAiApi api,
      @Valid @NotNull OpenAiBackend backend,
      @Valid @NotNull OpenAiModel model,
      @Valid @Nullable TimeoutConfiguration timeouts) {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = OpenAiApi.OpenAiCompletionsApi.class, name = COMPLETIONS_ID),
    @JsonSubTypes.Type(value = OpenAiApi.OpenAiResponsesApi.class, name = RESPONSES_ID)
  })
  @TemplateDiscriminatorProperty(
      label = "API",
      group = "provider",
      name = "type",
      defaultValue = RESPONSES_ID,
      description = "Specify which OpenAI API to use.")
  public sealed interface OpenAiApi {

    /** The API family discriminator string. */
    String type();

    @TemplateSubType(id = COMPLETIONS_ID, label = "Chat Completions")
    record OpenAiCompletionsApi(@Valid @Nullable CompletionsParameters completions)
        implements OpenAiApi {

      @TemplateProperty(ignore = true)
      public static final String COMPLETIONS_ID = "completions";

      @Override
      public String type() {
        return COMPLETIONS_ID;
      }

      public record CompletionsParameters(
          @Min(1)
              @TemplateProperty(
                  group = "model-options",
                  label = "Max completion tokens",
                  tooltip =
                      "The maximum number of tokens per request to generate before stopping. <br><br>Details in the <a href=\"https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = FeelMode.required,
                  optional = true)
              @Nullable Integer maxCompletionTokens,
          @TemplateProperty(
                  group = "model",
                  label = "Effort",
                  description = "Leave unset to use the model default.",
                  tooltip =
                      "Controls how many tokens the model spends when responding, trading thoroughness against speed and cost. Not supported on all models."
                          + "<br><br>See the <a href=\"https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create\" target=\"_blank\">Chat Completions API reference</a>.",
                  type = TemplateProperty.PropertyType.Dropdown,
                  choices = {
                    @DropdownPropertyChoice(value = "minimal", label = "minimal"),
                    @DropdownPropertyChoice(value = "low", label = "low"),
                    @DropdownPropertyChoice(value = "medium", label = "medium"),
                    @DropdownPropertyChoice(value = "high", label = "high"),
                    @DropdownPropertyChoice(value = "xhigh", label = "xhigh"),
                    @DropdownPropertyChoice(value = "max", label = "max")
                  },
                  optional = true)
              @Nullable OpenAiEffort effort,
          @DecimalMin("0.0")
              @DecimalMax("2.0")
              @TemplateProperty(
                  group = "model-options",
                  label = "Temperature",
                  tooltip =
                      "Floating point number between 0 and 2. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = FeelMode.required,
                  optional = true)
              @Nullable Double temperature,
          @DecimalMin("0.0")
              @DecimalMax("1.0")
              @TemplateProperty(
                  group = "model-options",
                  label = "top P",
                  tooltip =
                      "Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = FeelMode.required,
                  optional = true)
              @Nullable Double topP) {}
    }

    @TemplateSubType(id = RESPONSES_ID, label = "Responses")
    record OpenAiResponsesApi(@Valid @Nullable ResponsesParameters responses) implements OpenAiApi {

      @TemplateProperty(ignore = true)
      public static final String RESPONSES_ID = "responses";

      @Override
      public String type() {
        return RESPONSES_ID;
      }

      public record ResponsesParameters(
          @Min(1)
              @TemplateProperty(
                  group = "model-options",
                  label = "Max output tokens",
                  tooltip =
                      "The maximum number of tokens per request to generate before stopping. <br><br>Details in the <a href=\"https://developers.openai.com/api/reference/resources/responses/methods/create\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = FeelMode.required,
                  optional = true)
              @Nullable Integer maxOutputTokens,
          @TemplateProperty(
                  group = "model",
                  label = "Effort",
                  description = "Leave unset to use the model default.",
                  tooltip =
                      "Controls how many tokens the model spends when responding, trading thoroughness against speed and cost. Not supported on all models."
                          + "<br><br>See the <a href=\"https://developers.openai.com/api/reference/resources/responses/methods/create\" target=\"_blank\">Responses API reference</a>.",
                  type = TemplateProperty.PropertyType.Dropdown,
                  choices = {
                    @DropdownPropertyChoice(value = "minimal", label = "minimal"),
                    @DropdownPropertyChoice(value = "low", label = "low"),
                    @DropdownPropertyChoice(value = "medium", label = "medium"),
                    @DropdownPropertyChoice(value = "high", label = "high"),
                    @DropdownPropertyChoice(value = "xhigh", label = "xhigh"),
                    @DropdownPropertyChoice(value = "max", label = "max")
                  },
                  optional = true)
              @Nullable OpenAiEffort effort,
          @DecimalMin("0.0")
              @DecimalMax("2.0")
              @TemplateProperty(
                  group = "model-options",
                  label = "Temperature",
                  tooltip =
                      "Floating point number between 0 and 2. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://developers.openai.com/api/reference/resources/responses/methods/create\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = FeelMode.required,
                  optional = true)
              @Nullable Double temperature,
          @DecimalMin("0.0")
              @DecimalMax("1.0")
              @TemplateProperty(
                  group = "model-options",
                  label = "top P",
                  tooltip =
                      "Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://developers.openai.com/api/reference/resources/responses/methods/create\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.Number,
                  feel = FeelMode.required,
                  optional = true)
              @Nullable Double topP) {}
    }
  }

  /** OpenAI effort levels, trading thoroughness against speed and cost. */
  public enum OpenAiEffort {
    @JsonProperty("minimal")
    MINIMAL,
    @JsonProperty("low")
    LOW,
    @JsonProperty("medium")
    MEDIUM,
    @JsonProperty("high")
    HIGH,
    @JsonProperty("xhigh")
    XHIGH,
    @JsonProperty("max")
    MAX
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = OpenAiBackend.OpenAiApiBackend.class, name = OPENAI_API_ID),
    @JsonSubTypes.Type(value = OpenAiBackend.OpenAiFoundryBackend.class, name = FOUNDRY_ID),
    @JsonSubTypes.Type(value = OpenAiBackend.OpenAiCustomBackend.class, name = CUSTOM_ID)
  })
  @TemplateDiscriminatorProperty(
      label = "Backend",
      group = "provider",
      name = "type",
      defaultValue = OPENAI_API_ID,
      description = "Specify how the OpenAI API is reached.")
  public sealed interface OpenAiBackend {

    /** The backend discriminator string. */
    String type();

    OpenAiRequestCustomizations requestCustomizations();

    @TemplateSubType(id = OPENAI_API_ID, label = "OpenAI API")
    record OpenAiApiBackend(@Valid @NotNull OpenAiApiConnection openai) implements OpenAiBackend {

      @Override
      public OpenAiRequestCustomizations requestCustomizations() {
        return new OpenAiRequestCustomizations(
            openai.headers(), openai.queryParameters(), openai.bodyProperties());
      }

      @TemplateProperty(ignore = true)
      public static final String OPENAI_API_ID = "openai-api";

      @Override
      public String type() {
        return OPENAI_API_ID;
      }

      public record OpenAiApiConnection(
          @NotBlank
              @TemplateProperty(
                  group = "provider",
                  label = "OpenAI API key",
                  type = TemplateProperty.PropertyType.String,
                  feel = FeelMode.optional,
                  constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
              String apiKey,
          @TemplateProperty(
                  group = "provider",
                  label = "Organization ID",
                  description =
                      "For members of multiple organizations. Details in the <a href=\"https://platform.openai.com/docs/api-reference/authentication\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.String,
                  feel = FeelMode.optional,
                  optional = true)
              @Nullable String organizationId,
          @TemplateProperty(
                  group = "provider",
                  label = "Project ID",
                  description =
                      "For accounts with multiple projects. Details in the <a href=\"https://platform.openai.com/docs/api-reference/authentication\" target=\"_blank\">documentation</a>.",
                  type = TemplateProperty.PropertyType.String,
                  feel = FeelMode.optional,
                  optional = true)
              @Nullable String projectId,
          @HttpUrl
              @TemplateProperty(
                  group = "provider",
                  label = "API endpoint",
                  type = TemplateProperty.PropertyType.Hidden,
                  feel = FeelMode.disabled,
                  optional = true)
              @Nullable String endpoint,
          @TemplateProperty(
                  group = "advanced-provider-options",
                  label = "Headers",
                  tooltip = "Map of HTTP headers to add to the request.",
                  type = TemplateProperty.PropertyType.Hidden,
                  feel = FeelMode.disabled,
                  optional = true)
              @Nullable Map<String, String> headers,
          @Valid
              @TemplateProperty(
                  group = "advanced-provider-options",
                  label = "Query parameters",
                  tooltip = "Map of query parameters to add to the request URL.",
                  type = TemplateProperty.PropertyType.Hidden,
                  feel = FeelMode.disabled,
                  optional = true)
              @Nullable Map<@NotBlank String, String> queryParameters,
          @TemplateProperty(
                  group = "advanced-provider-options",
                  label = "Body properties",
                  tooltip = "Map of additional properties to include in the request body.",
                  type = TemplateProperty.PropertyType.Hidden,
                  feel = FeelMode.disabled,
                  optional = true)
              @Nullable Map<String, Object> bodyProperties) {

        @Override
        public String toString() {
          return "OpenAiApiConnection{apiKey=[REDACTED], organizationId="
              + organizationId
              + ", projectId="
              + projectId
              + ", endpoint="
              + endpoint
              + ", headers="
              + redactValues(headers)
              + ", queryParameters="
              + redactValues(queryParameters)
              + ", bodyProperties="
              + redactValues(bodyProperties)
              + "}";
        }
      }
    }

    @TemplateSubType(id = FOUNDRY_ID, label = "Microsoft Foundry (Azure)")
    record OpenAiFoundryBackend(@Valid @NotNull FoundryBackend foundry) implements OpenAiBackend {

      @TemplateProperty(ignore = true)
      public static final String FOUNDRY_ID = "foundry";

      @Override
      public String type() {
        return FOUNDRY_ID;
      }

      @Override
      public OpenAiRequestCustomizations requestCustomizations() {
        return new OpenAiRequestCustomizations(
            foundry.headers(), foundry.queryParameters(), foundry.bodyProperties());
      }

      public record FoundryBackend(
          @NotBlank
              @HttpUrl
              @TemplateProperty(
                  group = "provider",
                  label = "API endpoint",
                  description = "Base URL of the Microsoft Foundry / Azure OpenAI resource.",
                  tooltip =
                      "The full resource endpoint, e.g. <code>https://your-resource.openai.azure.com</code> "
                          + "or a Foundry endpoint such as <code>https://your-resource.services.ai.azure.com</code>.",
                  type = TemplateProperty.PropertyType.String,
                  feel = FeelMode.optional,
                  placeholder = "https://your-resource.openai.azure.com",
                  constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
              String endpoint,
          @TemplateProperty(
                  group = "advanced-provider-options",
                  label = "API version",
                  description =
                      "Overrides the Azure OpenAI API version. Leave unset to let the "
                          + "automatically detected API surface (unified or legacy) pick its own "
                          + "default.",
                  type = TemplateProperty.PropertyType.Hidden,
                  feel = FeelMode.disabled,
                  optional = true)
              @Nullable String apiVersion,
          @Valid @NotNull FoundryAuthentication authentication,
          @TemplateProperty(
                  group = "advanced-provider-options",
                  label = "HTTP headers",
                  description = "Map of HTTP headers to add to the request.",
                  type = TemplateProperty.PropertyType.Hidden,
                  feel = FeelMode.disabled,
                  optional = true)
              @Nullable Map<String, String> headers,
          @Valid
              @TemplateProperty(
                  group = "advanced-provider-options",
                  label = "Query parameters",
                  description = "Map of query parameters to add to the request URL.",
                  type = TemplateProperty.PropertyType.Hidden,
                  feel = FeelMode.disabled,
                  optional = true)
              @Nullable Map<@NotBlank String, String> queryParameters,
          @TemplateProperty(
                  group = "advanced-provider-options",
                  label = "Body properties",
                  description = "Map of additional properties to include in the request body.",
                  type = TemplateProperty.PropertyType.Hidden,
                  feel = FeelMode.disabled,
                  optional = true)
              @Nullable Map<String, Object> bodyProperties) {

        @Override
        public String toString() {
          return "FoundryBackend{endpoint="
              + endpoint
              + ", apiVersion="
              + apiVersion
              + ", authentication="
              + authentication
              + ", headers="
              + redactValues(headers)
              + ", queryParameters="
              + redactValues(queryParameters)
              + ", bodyProperties="
              + redactValues(bodyProperties)
              + "}";
        }
      }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
      @JsonSubTypes.Type(value = FoundryAuthentication.ApiKeyAuthentication.class, name = "apiKey"),
      @JsonSubTypes.Type(
          value = FoundryAuthentication.ClientCredentialsAuthentication.class,
          name = "clientCredentials"),
      @JsonSubTypes.Type(
          value = FoundryAuthentication.ManagedIdentityAuthentication.class,
          name = "managedIdentity")
    })
    @TemplateDiscriminatorProperty(
        label = "Authentication",
        group = "provider",
        name = "type",
        defaultValue = "apiKey",
        description = "Specify the Microsoft Foundry authentication strategy.")
    sealed interface FoundryAuthentication {

      @TemplateSubType(id = "apiKey", label = "API key")
      record ApiKeyAuthentication(
          @NotBlank
              @TemplateProperty(
                  group = "provider",
                  label = "API key",
                  type = TemplateProperty.PropertyType.String,
                  feel = FeelMode.optional,
                  constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
              String apiKey)
          implements FoundryAuthentication {

        @Override
        public String toString() {
          return "ApiKeyAuthentication{apiKey=[REDACTED]}";
        }
      }

      @TemplateSubType(id = "clientCredentials", label = "Entra ID: Client credentials")
      record ClientCredentialsAuthentication(
          @NotBlank
              @TemplateProperty(
                  group = "provider",
                  label = "Client ID",
                  description = "Microsoft Entra ID application (client) ID.",
                  type = TemplateProperty.PropertyType.String,
                  feel = FeelMode.optional,
                  constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
              String clientId,
          @NotBlank
              @TemplateProperty(
                  group = "provider",
                  label = "Client secret",
                  description = "Microsoft Entra ID application client secret.",
                  type = TemplateProperty.PropertyType.String,
                  feel = FeelMode.optional,
                  constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
              String clientSecret,
          @NotBlank
              @TemplateProperty(
                  group = "provider",
                  label = "Tenant ID",
                  description = "Microsoft Entra ID tenant (directory) ID.",
                  type = TemplateProperty.PropertyType.String,
                  feel = FeelMode.optional,
                  constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
              String tenantId,
          @TemplateProperty(
                  group = "provider",
                  label = "Authority host",
                  description =
                      "Overrides the Microsoft Entra ID authority host, e.g. for sovereign "
                          + "clouds. Leave unset to use the public cloud authority.",
                  type = TemplateProperty.PropertyType.String,
                  feel = FeelMode.optional,
                  optional = true)
              @Nullable String authorityHost)
          implements FoundryAuthentication {

        @Override
        public String toString() {
          return "ClientCredentialsAuthentication{clientId="
              + clientId
              + ", clientSecret=[REDACTED], tenantId="
              + tenantId
              + ", authorityHost="
              + authorityHost
              + "}";
        }
      }

      @TemplateSubType(
          id = "managedIdentity",
          label = "Entra ID: Managed identity (Hybrid/Self-Managed only)")
      record ManagedIdentityAuthentication(
          @TemplateProperty(
                  group = "provider",
                  label = "Client ID",
                  description =
                      "Client ID of a user-assigned managed identity. Leave unset to use the "
                          + "system-assigned managed identity.",
                  type = TemplateProperty.PropertyType.String,
                  feel = FeelMode.optional,
                  optional = true)
              @Nullable String managedIdentityClientId)
          implements FoundryAuthentication {

        @JsonIgnore
        @AssertFalse(message = "Managed identity authentication is not supported on SaaS")
        public boolean isUsedInSaaS() {
          return ConnectorUtils.isSaaS();
        }
      }
    }

    @TemplateSubType(id = CUSTOM_ID, label = "Custom / compatible endpoint")
    record OpenAiCustomBackend(@Valid @NotNull CustomBackend custom) implements OpenAiBackend {

      @TemplateProperty(ignore = true)
      public static final String CUSTOM_ID = "custom";

      @Override
      public String type() {
        return CUSTOM_ID;
      }

      @Override
      public OpenAiRequestCustomizations requestCustomizations() {
        return new OpenAiRequestCustomizations(
            custom.headers(), custom.queryParameters(), custom.bodyProperties());
      }

      public record CustomBackend(
          @NotBlank
              @HttpUrl
              @TemplateProperty(
                  group = "provider",
                  label = "API endpoint",
                  description =
                      "Base URL of the OpenAI-compatible API; <code>/chat/completions</code> or <code>/responses</code> will be appended depending on the selected API.",
                  type = TemplateProperty.PropertyType.String,
                  feel = FeelMode.optional,
                  placeholder = "https://api.openai.com/v1",
                  constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
              String endpoint,
          @TemplateProperty(
                  group = "advanced-provider-options",
                  label = "Headers",
                  description = "Map of HTTP headers to add to the request.",
                  feel = FeelMode.required,
                  optional = true)
              @Nullable Map<String, String> headers,
          @Valid
              @TemplateProperty(
                  group = "advanced-provider-options",
                  label = "Query parameters",
                  description = "Map of query parameters to add to the request URL.",
                  feel = FeelMode.required,
                  optional = true)
              @Nullable Map<@NotBlank String, String> queryParameters,
          @TemplateProperty(
                  group = "advanced-provider-options",
                  label = "Body properties",
                  description = "Map of additional properties to include in the request body.",
                  feel = FeelMode.required,
                  optional = true)
              @Nullable Map<String, Object> bodyProperties,
          @Valid @NotNull OpenAiCustomEndpointAuthentication authentication) {

        @Override
        public String toString() {
          return "CustomBackend{endpoint="
              + endpoint
              + ", headers="
              + redactValues(headers)
              + ", queryParameters="
              + redactValues(queryParameters)
              + ", bodyProperties="
              + redactValues(bodyProperties)
              + ", authentication="
              + authentication
              + "}";
        }
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
              defaultValue = "",
              defaultValueType = TemplateProperty.DefaultValueType.String,
              placeholder = "gpt-5.5",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String model) {}
}
