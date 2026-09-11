/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GOOGLE_GEMINI_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend.GOOGLE_GEMINI_API_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiVertexAiBackend.GOOGLE_VERTEX_AI_ID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.HttpUrl;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AgenticAiCredentialConfigurations.GoogleGeminiApiCredential;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AgenticAiCredentialConfigurations.VertexAiCredential;
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
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@TemplateSubType(id = GOOGLE_GEMINI_ID, label = "Google Gemini")
public record GeminiChatModelConfiguration(@Valid @NotNull GeminiConnection googleGemini)
    implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String GOOGLE_GEMINI_ID = "google-gemini";

  @Override
  public String provider() {
    return GOOGLE_GEMINI_ID;
  }

  @Override
  public String model() {
    return googleGemini.model().model();
  }

  /** All Gemini-specific configuration, nested under the {@code googleGemini} wire key. */
  public record GeminiConnection(
      @Valid @NotNull GeminiBackend backend,
      @Valid @NotNull GeminiModel model,
      @Valid @Nullable TimeoutConfiguration timeouts) {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = GeminiBackend.GeminiApiBackend.class, name = GOOGLE_GEMINI_API_ID),
    @JsonSubTypes.Type(
        value = GeminiBackend.GeminiVertexAiBackend.class,
        name = GOOGLE_VERTEX_AI_ID)
  })
  @TemplateDiscriminatorProperty(
      label = "Backend",
      group = "provider",
      name = "type",
      defaultValue = GOOGLE_GEMINI_API_ID,
      description = "Specify which Google backend serves the Gemini model.")
  public sealed interface GeminiBackend {

    /** The backend discriminator string. */
    String type();

    @TemplateSubType(id = GOOGLE_GEMINI_API_ID, label = "Google Gemini API")
    record GeminiApiBackend(@Valid @NotNull GoogleGeminiApi googleGeminiApi)
        implements GeminiBackend {

      @TemplateProperty(ignore = true)
      public static final String GOOGLE_GEMINI_API_ID = "google-gemini-api";

      @Override
      public String type() {
        return GOOGLE_GEMINI_API_ID;
      }

      public record GoogleGeminiApi(
          @TemplateProperty(
                  group = "provider",
                  label = "Google Gemini API credential",
                  type = PropertyType.Configuration,
                  constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
                  binding = @TemplateProperty.PropertyBinding(name = "credential"),
                  tooltip = "Choose a reusable Google Gemini API credential.")
              @Valid
              @Nullable GoogleGeminiApiCredential credential,
          @TemplateProperty(ignore = true) @Nullable String apiKey,
          @HttpUrl
              @TemplateProperty(
                  group = "provider",
                  label = "API endpoint",
                  type = TemplateProperty.PropertyType.Hidden,
                  feel = FeelMode.disabled,
                  optional = true)
              @Nullable String endpoint) {

        public GoogleGeminiApi(String apiKey, @Nullable String endpoint) {
          this(null, apiKey, endpoint);
        }

        @JsonIgnore
        @jakarta.validation.constraints.AssertTrue(
            message = "A Gemini API key is required from the credential or element template")
        public boolean isApiKeyPresent() {
          return apiKey() != null && !apiKey().isBlank();
        }

        public @Nullable String apiKey() {
          return credential != null ? credential.apiKey() : apiKey;
        }

        @Override
        public String toString() {
          return "GoogleGeminiApi{apiKey=[REDACTED], endpoint=" + endpoint + "}";
        }
      }
    }

    @TemplateSubType(id = GOOGLE_VERTEX_AI_ID, label = "Enterprise Agent Platform (Vertex AI)")
    record GeminiVertexAiBackend(@Valid @NotNull GoogleVertexAi googleVertexAi)
        implements GeminiBackend {

      @TemplateProperty(ignore = true)
      public static final String GOOGLE_VERTEX_AI_ID = "google-vertex-ai";

      @Override
      public String type() {
        return GOOGLE_VERTEX_AI_ID;
      }

      public record GoogleVertexAi(
          @TemplateProperty(
                  group = "provider",
                  label = "Vertex AI credential",
                  type = PropertyType.Configuration,
                  constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
                  binding = @TemplateProperty.PropertyBinding(name = "credential"),
                  tooltip = "Choose a reusable Vertex AI credential.")
              @Valid
              @Nullable VertexAiCredential credential,
          @TemplateProperty(ignore = true) @Nullable String projectId,
          @TemplateProperty(ignore = true) @Nullable String region,
          // Hidden: never shown in the modeler. Exists solely so e2e tests can point the client
          // at a local WireMock server via HttpOptions.baseUrl(); real deployments never set it.
          // Mirrors GoogleGeminiApi's own hidden endpoint field 1:1 (same rationale).
          @HttpUrl
              @TemplateProperty(
                  group = "provider",
                  label = "API endpoint",
                  type = TemplateProperty.PropertyType.Hidden,
                  feel = FeelMode.disabled,
                  optional = true)
              @Nullable String endpoint,
          @TemplateProperty(type = PropertyType.Hidden, ignore = true)
              @Nullable GoogleVertexAiAuthentication authentication) {

        public GoogleVertexAi(
            String projectId,
            String region,
            @Nullable String endpoint,
            GoogleVertexAiAuthentication authentication) {
          this(null, projectId, region, endpoint, authentication);
        }

        @JsonIgnore
        @jakarta.validation.constraints.AssertTrue(
            message = "A Vertex AI project ID is required from the credential or element template")
        public boolean isProjectIdPresent() {
          String effectiveProjectId = credential != null ? credential.projectId() : projectId;
          return effectiveProjectId != null && !effectiveProjectId.isBlank();
        }

        @JsonIgnore
        @jakarta.validation.constraints.AssertTrue(
            message = "A Vertex AI region is required from the credential or element template")
        public boolean isRegionPresent() {
          String effectiveRegion = credential != null ? credential.region() : region;
          return effectiveRegion != null && !effectiveRegion.isBlank();
        }

        @JsonIgnore
        @jakarta.validation.constraints.AssertTrue(
            message =
                "Vertex AI authentication is required from the credential or element template")
        public boolean isAuthenticationPresent() {
          return credential != null ? credential.authentication() != null : authentication != null;
        }

        public String projectId() {
          return Objects.requireNonNull(credential != null ? credential.projectId() : projectId);
        }

        public String region() {
          return Objects.requireNonNull(credential != null ? credential.region() : region);
        }

        public GoogleVertexAiAuthentication authentication() {
          return Objects.requireNonNull(
              credential != null ? credential.authentication() : authentication);
        }

        @JsonIgnore
        @jakarta.validation.constraints.AssertTrue(
            message = "Vertex AI service account JSON key must not be blank")
        public boolean isAuthenticationValid() {
          GoogleVertexAiAuthentication effectiveAuthentication =
              credential != null ? credential.authentication() : authentication;
          return effectiveAuthentication != null
              && (!(effectiveAuthentication
                      instanceof
                      GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication
                          serviceAccount)
                  || serviceAccount.jsonKey() != null && !serviceAccount.jsonKey().isBlank());
        }

        @JsonIgnore
        @AssertFalse(
            message =
                "Application default credentials for Enterprise Agent Platform (Vertex AI) are not supported on SaaS")
        public boolean isApplicationDefaultCredentialsUsedInSaaS() {
          return ConnectorUtils.isSaaS()
              && (credential != null ? credential.authentication() : authentication)
                  instanceof
                  GoogleVertexAiAuthentication.ApplicationDefaultCredentialsAuthentication;
        }
      }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
      @JsonSubTypes.Type(
          value = GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication.class,
          name = "serviceAccountCredentials"),
      @JsonSubTypes.Type(
          value = GoogleVertexAiAuthentication.ApplicationDefaultCredentialsAuthentication.class,
          name = "applicationDefaultCredentials")
    })
    @TemplateDiscriminatorProperty(
        label = "Authentication",
        group = "provider",
        name = "type",
        defaultValue = "serviceAccountCredentials",
        description = "Specify the Enterprise Agent Platform (Vertex AI) authentication strategy.")
    sealed interface GoogleVertexAiAuthentication {
      @TemplateSubType(id = "serviceAccountCredentials", label = "Service account credentials")
      record ServiceAccountCredentialsAuthentication(
          @NotBlank
              @TemplateProperty(
                  group = "provider",
                  label = "JSON key of the service account",
                  secret = true,
                  description = "This is the key of the service account in JSON format.",
                  feel = FeelMode.optional,
                  constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
              String jsonKey)
          implements GoogleVertexAiAuthentication {
        @Override
        public String toString() {
          return "ServiceAccountCredentialsAuthentication{jsonKey=[REDACTED]}";
        }
      }

      @TemplateSubType(
          id = "applicationDefaultCredentials",
          label = "Application default credentials (Hybrid/Self-Managed only)")
      record ApplicationDefaultCredentialsAuthentication()
          implements GoogleVertexAiAuthentication {}
    }
  }

  public record GeminiModel(
      @NotBlank
          @TemplateProperty(
              group = "model",
              label = "Model",
              description =
                  "Specify the model ID. Details in the <a href=\"https://ai.google.dev/gemini-api/docs/models\" target=\"_blank\">documentation</a>.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              defaultValue = "",
              defaultValueType = TemplateProperty.DefaultValueType.String,
              placeholder = "gemini-3-pro-preview",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String model,
      @Valid @Nullable GeminiModelParameters parameters) {

    public record GeminiModelParameters(
        @Min(1)
            @TemplateProperty(
                group = "model-options",
                label = "Maximum tokens",
                tooltip =
                    "The maximum number of tokens to generate before stopping. <br><br>Details in the <a href=\"https://ai.google.dev/api/generate-content#v1beta.GenerationConfig\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            @Nullable Integer maxTokens,
        @DecimalMin("0.0")
            @DecimalMax("2.0")
            @TemplateProperty(
                group = "model-options",
                label = "Temperature",
                tooltip =
                    "Controls the randomness of the output. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://ai.google.dev/api/generate-content#v1beta.GenerationConfig\" target=\"_blank\">documentation</a>.",
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
                    "Floating point number between 0 and 1. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://ai.google.dev/api/generate-content#v1beta.GenerationConfig\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            @Nullable Double topP,
        @Min(1)
            @TemplateProperty(
                group = "model-options",
                label = "top K",
                tooltip =
                    "Integer greater than 0. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://ai.google.dev/api/generate-content#v1beta.GenerationConfig\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            @Nullable Integer topK,
        @Valid @Nullable GeminiThinking thinking) {}

    /**
     * Gemini extended-thinking configuration for a single model. Gemini 2.5 models use {@code
     * thinkingBudget}; Gemini 3.x models use an explicit {@code thinkingLevel} (unset defaults to
     * {@code MODEL_DEFAULT}, letting the model choose its own reasoning depth). Setting a budget
     * alongside an explicit level is a hard API error on 3.x models &mdash; this is validated below
     * ({@link #isBothThinkingBudgetAndLevelSet()}), not auto-resolved.
     */
    public record GeminiThinking(
        @Min(-1)
            @TemplateProperty(
                group = "model",
                label = "Thinking budget (tokens)",
                tooltip =
                    "Gemini 2.5 models: token budget for extended thinking. -1 = dynamic, 0 = disabled. Mutually exclusive with Thinking level (Gemini 3.x). <br><br>Details in the <a href=\"https://ai.google.dev/gemini-api/docs/thinking\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true,
                condition =
                    @TemplateProperty.PropertyCondition(
                        property = "provider.googleGemini.model.parameters.thinking.thinkingLevel",
                        equals = "modelDefault"))
            @Nullable Integer thinkingBudget,
        @TemplateProperty(
                group = "model",
                label = "Thinking level",
                tooltip =
                    "Gemini 3.x models: qualitative thinking effort. \"default\" lets the model choose its own reasoning depth. Mutually exclusive with Thinking budget (Gemini 2.5). <br><br>Details in the <a href=\"https://ai.google.dev/gemini-api/docs/thinking\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Dropdown,
                choices = {
                  @DropdownPropertyChoice(value = "modelDefault", label = "default"),
                  @DropdownPropertyChoice(value = "minimal", label = "minimal"),
                  @DropdownPropertyChoice(value = "low", label = "low"),
                  @DropdownPropertyChoice(value = "medium", label = "medium"),
                  @DropdownPropertyChoice(value = "high", label = "high")
                },
                defaultValue = "modelDefault")
            @Nullable GeminiThinkingLevel thinkingLevel) {

      public GeminiThinking {
        if (thinkingLevel == null) {
          thinkingLevel = GeminiThinkingLevel.MODEL_DEFAULT;
        }
      }

      @JsonIgnore
      @AssertFalse(
          message = "thinking.thinkingBudget and thinking.thinkingLevel are mutually exclusive")
      public boolean isBothThinkingBudgetAndLevelSet() {
        return thinkingBudget != null && thinkingLevel != GeminiThinkingLevel.MODEL_DEFAULT;
      }
    }

    /** Gemini qualitative thinking-effort levels (Gemini 3.x models). */
    public enum GeminiThinkingLevel {
      @JsonProperty("modelDefault")
      MODEL_DEFAULT,
      @JsonProperty("minimal")
      MINIMAL,
      @JsonProperty("low")
      LOW,
      @JsonProperty("medium")
      MEDIUM,
      @JsonProperty("high")
      HIGH
    }
  }
}
