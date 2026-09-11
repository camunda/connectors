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
import io.camunda.connector.agenticai.aiagent.model.request.v2.AgenticAiCredentialConfigurations.AiGatewayCredential;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AgenticAiCredentialConfigurations.MicrosoftFoundryCredential;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AgenticAiCredentialConfigurations.OpenAiApiCredential;
import io.camunda.connector.agenticai.aiagent.util.ConnectorUtils;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Objects;
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
    @JsonSubTypes.Type(value = OpenAiApi.OpenAiResponsesApi.class, name = RESPONSES_ID),
    @JsonSubTypes.Type(value = OpenAiApi.OpenAiCompletionsApi.class, name = COMPLETIONS_ID)
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
                  tooltip =
                      "Controls how many tokens the model spends when responding, trading thoroughness against speed and cost. Not supported on all models."
                          + "<br><br>See the <a href=\"https://developers.openai.com/api/reference/resources/responses/methods/create\" target=\"_blank\">Responses API reference</a>.",
                  type = TemplateProperty.PropertyType.Dropdown,
                  choices = {
                    @DropdownPropertyChoice(value = "modelDefault", label = "default"),
                    @DropdownPropertyChoice(value = "minimal", label = "minimal"),
                    @DropdownPropertyChoice(value = "low", label = "low"),
                    @DropdownPropertyChoice(value = "medium", label = "medium"),
                    @DropdownPropertyChoice(value = "high", label = "high"),
                    @DropdownPropertyChoice(value = "xhigh", label = "xhigh"),
                    @DropdownPropertyChoice(value = "max", label = "max")
                  },
                  defaultValue = "modelDefault")
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
                  tooltip =
                      "Controls how many tokens the model spends when responding, trading thoroughness against speed and cost. Not supported on all models."
                          + "<br><br>See the <a href=\"https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create\" target=\"_blank\">Chat Completions API reference</a>.",
                  type = TemplateProperty.PropertyType.Dropdown,
                  choices = {
                    @DropdownPropertyChoice(value = "modelDefault", label = "default"),
                    @DropdownPropertyChoice(value = "minimal", label = "minimal"),
                    @DropdownPropertyChoice(value = "low", label = "low"),
                    @DropdownPropertyChoice(value = "medium", label = "medium"),
                    @DropdownPropertyChoice(value = "high", label = "high"),
                    @DropdownPropertyChoice(value = "xhigh", label = "xhigh"),
                    @DropdownPropertyChoice(value = "max", label = "max")
                  },
                  defaultValue = "modelDefault")
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
  }

  /** OpenAI effort levels, trading thoroughness against speed and cost. */
  public enum OpenAiEffort {
    @JsonProperty("modelDefault")
    MODEL_DEFAULT,
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
          @TemplateProperty(
                  group = "provider",
                  label = "OpenAI API credential",
                  type = PropertyType.Configuration,
                  constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
                  binding = @TemplateProperty.PropertyBinding(name = "credential"),
                  tooltip = "Choose a reusable OpenAI API credential.")
              @Valid
              @Nullable OpenAiApiCredential credential,
          @TemplateProperty(ignore = true) @Nullable String apiKey,
          @TemplateProperty(ignore = true) @Nullable String organizationId,
          @TemplateProperty(ignore = true) @Nullable String projectId,
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

        public OpenAiApiConnection(
            String apiKey,
            @Nullable String organizationId,
            @Nullable String projectId,
            @Nullable String endpoint,
            @Nullable Map<String, String> headers,
            @Nullable Map<String, String> queryParameters,
            @Nullable Map<String, Object> bodyProperties) {
          this(
              null,
              apiKey,
              organizationId,
              projectId,
              endpoint,
              headers,
              queryParameters,
              bodyProperties);
        }

        @JsonIgnore
        @jakarta.validation.constraints.AssertTrue(
            message = "An OpenAI API key is required from the credential or element template")
        public boolean isApiKeyPresent() {
          return apiKey() != null && !apiKey().isBlank();
        }

        public @Nullable String apiKey() {
          return credential != null ? credential.apiKey() : apiKey;
        }

        public @Nullable String organizationId() {
          return credential != null ? credential.organizationId() : organizationId;
        }

        public @Nullable String projectId() {
          return credential != null ? credential.projectId() : projectId;
        }

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
          @TemplateProperty(
                  group = "provider",
                  label = "Microsoft Foundry credential",
                  type = PropertyType.Configuration,
                  constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
                  binding = @TemplateProperty.PropertyBinding(name = "credential"),
                  tooltip = "Choose a reusable Microsoft Foundry credential.")
              @Valid
              @Nullable MicrosoftFoundryCredential credential,
          @TemplateProperty(ignore = true) @Nullable String endpoint,
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
          @TemplateProperty(type = PropertyType.Hidden, ignore = true)
              @Nullable FoundryAuthentication authentication,
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

        public FoundryBackend(
            String endpoint,
            @Nullable String apiVersion,
            FoundryAuthentication authentication,
            @Nullable Map<String, String> headers,
            @Nullable Map<String, String> queryParameters,
            @Nullable Map<String, Object> bodyProperties) {
          this(
              null, endpoint, apiVersion, authentication, headers, queryParameters, bodyProperties);
        }

        @JsonIgnore
        @jakarta.validation.constraints.AssertTrue(
            message =
                "A Microsoft Foundry endpoint is required from the credential or element template")
        public boolean isEndpointPresent() {
          String effectiveEndpoint = credential != null ? credential.endpoint() : endpoint;
          return effectiveEndpoint != null && !effectiveEndpoint.isBlank();
        }

        @JsonIgnore
        @jakarta.validation.constraints.AssertTrue(
            message =
                "Microsoft Foundry authentication is required from the credential or element template")
        public boolean isAuthenticationPresent() {
          return credential != null ? credential.authentication() != null : authentication != null;
        }

        public String endpoint() {
          return Objects.requireNonNull(credential != null ? credential.endpoint() : endpoint);
        }

        public FoundryAuthentication authentication() {
          return Objects.requireNonNull(
              credential != null ? credential.authentication() : authentication);
        }

        @JsonIgnore
        @jakarta.validation.constraints.AssertTrue(
            message = "Microsoft Foundry authentication fields must not be blank")
        public boolean isAuthenticationValid() {
          FoundryAuthentication effectiveAuthentication =
              credential != null ? credential.authentication() : authentication;
          if (effectiveAuthentication == null) {
            return false;
          }
          return switch (effectiveAuthentication) {
            case FoundryAuthentication.ApiKeyAuthentication apiKey ->
                apiKey.apiKey() != null && !apiKey.apiKey().isBlank();
            case FoundryAuthentication.ClientCredentialsAuthentication clientCredentials ->
                clientCredentials.clientId() != null
                    && !clientCredentials.clientId().isBlank()
                    && clientCredentials.clientSecret() != null
                    && !clientCredentials.clientSecret().isBlank()
                    && clientCredentials.tenantId() != null
                    && !clientCredentials.tenantId().isBlank();
            case FoundryAuthentication.ManagedIdentityAuthentication ignored -> true;
          };
        }

        @JsonIgnore
        @AssertFalse(message = "Managed identity authentication is not supported on SaaS")
        public boolean isManagedIdentityUsedInSaaS() {
          return ConnectorUtils.isSaaS()
              && (credential != null ? credential.authentication() : authentication)
                  instanceof FoundryAuthentication.ManagedIdentityAuthentication;
        }

        @JsonIgnore
        @jakarta.validation.constraints.AssertTrue(message = "Must be an HTTP or HTTPS URL")
        public boolean isEndpointHttpUrl() {
          String effectiveEndpoint = credential != null ? credential.endpoint() : endpoint;
          return effectiveEndpoint == null
              || effectiveEndpoint.isBlank()
              || effectiveEndpoint.matches("^https?://.+");
        }

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
                  secret = true,
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
                  secret = true,
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
              @Nullable String authorityHost,
          @TemplateProperty(
                  group = "provider",
                  label = "Entra ID scope",
                  description =
                      "Overrides the Microsoft Entra ID token scope requested for this "
                          + "authentication flow. Leave unset to let the automatically detected "
                          + "Azure cloud pick its own default.",
                  type = TemplateProperty.PropertyType.Hidden,
                  feel = FeelMode.disabled,
                  optional = true)
              @Nullable String entraIdScope)
          implements FoundryAuthentication {

        @Override
        public String toString() {
          return "ClientCredentialsAuthentication{clientId="
              + clientId
              + ", clientSecret=[REDACTED], tenantId="
              + tenantId
              + ", authorityHost="
              + authorityHost
              + ", entraIdScope="
              + entraIdScope
              + "}";
        }
      }

      @TemplateSubType(
          id = "managedIdentity",
          label = "Entra ID: Managed identity (Hybrid/Self-Managed only)")
      record ManagedIdentityAuthentication(
          @TemplateProperty(
                  id = "managedIdentity.clientId",
                  group = "provider",
                  label = "Client ID",
                  description =
                      "Client ID of a user-assigned managed identity. Leave unset to use the "
                          + "system-assigned managed identity.",
                  type = TemplateProperty.PropertyType.String,
                  feel = FeelMode.optional,
                  optional = true)
              @Nullable String clientId,
          @TemplateProperty(
                  id = "managedIdentity.entraIdScope",
                  group = "provider",
                  label = "Entra ID scope",
                  description =
                      "Overrides the Microsoft Entra ID token scope requested for this "
                          + "authentication flow. Leave unset to let the automatically detected "
                          + "Azure cloud pick its own default.",
                  type = TemplateProperty.PropertyType.Hidden,
                  feel = FeelMode.disabled,
                  optional = true)
              @Nullable String entraIdScope)
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
          @TemplateProperty(
                  group = "provider",
                  label = "AI Gateway credential",
                  type = PropertyType.Configuration,
                  constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
                  binding = @TemplateProperty.PropertyBinding(name = "credential"),
                  tooltip = "Choose a reusable AI Gateway credential.")
              @Valid
              @Nullable AiGatewayCredential credential,
          @TemplateProperty(
                  group = "provider",
                  label = "API endpoint override",
                  tooltip =
                      "Overrides the credential's gateway endpoint. <code>/chat/completions</code> or <code>/responses</code> is appended depending on the selected API.",
                  type = TemplateProperty.PropertyType.String,
                  feel = FeelMode.optional,
                  placeholder = "https://api.openai.com/v1",
                  optional = true)
              @Nullable String endpoint,
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
          @TemplateProperty(type = PropertyType.Hidden, ignore = true)
              @Nullable OpenAiCustomEndpointAuthentication authentication) {

        public CustomBackend(
            String endpoint,
            @Nullable Map<String, String> headers,
            @Nullable Map<String, String> queryParameters,
            @Nullable Map<String, Object> bodyProperties,
            OpenAiCustomEndpointAuthentication authentication) {
          this(null, endpoint, headers, queryParameters, bodyProperties, authentication);
        }

        @JsonIgnore
        @jakarta.validation.constraints.AssertTrue(
            message = "An AI Gateway endpoint is required from the credential or element template")
        public boolean isEndpointPresent() {
          String effectiveEndpoint =
              endpoint != null && !endpoint.isBlank()
                  ? endpoint
                  : credential != null ? credential.endpoint() : null;
          return effectiveEndpoint != null && !effectiveEndpoint.isBlank();
        }

        @JsonIgnore
        @jakarta.validation.constraints.AssertTrue(
            message =
                "AI Gateway API-key authentication is required from the credential or element template")
        public boolean isAuthenticationPresent() {
          return credential != null || authentication != null;
        }

        public String endpoint() {
          return Objects.requireNonNull(
              endpoint != null && !endpoint.isBlank()
                  ? endpoint
                  : credential != null ? credential.endpoint() : endpoint);
        }

        public OpenAiCustomEndpointAuthentication authentication() {
          return Objects.requireNonNull(
              credential != null
                  ? new OpenAiCustomEndpointAuthentication.ApiKeyAuthentication(credential.apiKey())
                  : authentication);
        }

        @JsonIgnore
        @jakarta.validation.constraints.AssertTrue(message = "Must be an HTTP or HTTPS URL")
        public boolean isEndpointHttpUrl() {
          return endpoint() == null || endpoint().isBlank() || endpoint().matches("^https?://.+");
        }

        @JsonIgnore
        @jakarta.validation.constraints.AssertTrue(
            message = "AI Gateway API-key authentication must not be blank")
        public boolean isAuthenticationValid() {
          OpenAiCustomEndpointAuthentication effectiveAuthentication =
              credential != null
                  ? new OpenAiCustomEndpointAuthentication.ApiKeyAuthentication(credential.apiKey())
                  : authentication;
          return effectiveAuthentication
                  instanceof OpenAiCustomEndpointAuthentication.ApiKeyAuthentication apiKey
              && apiKey.apiKey() != null
              && !apiKey.apiKey().isBlank();
        }

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
