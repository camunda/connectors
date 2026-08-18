/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GOOGLE_GEMINI_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend.GOOGLE_GEMINI_API_ID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.HttpUrl;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.TimeoutConfiguration;
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
    @JsonSubTypes.Type(value = GeminiBackend.GeminiApiBackend.class, name = GOOGLE_GEMINI_API_ID)
  })
  @TemplateDiscriminatorProperty(
      label = "Backend",
      group = "provider",
      name = "type",
      defaultValue = GOOGLE_GEMINI_API_ID,
      description = "Specify how the Gemini Developer API is reached.")
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
          @NotBlank
              @TemplateProperty(
                  group = "provider",
                  label = "Gemini API key",
                  type = TemplateProperty.PropertyType.String,
                  feel = FeelMode.optional,
                  constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
              String apiKey,
          // Hidden: never shown in the modeler. Exists solely so e2e tests can point the client
          // at a local WireMock server via HttpOptions.baseUrl(); real deployments never set it.
          // Mirrors AnthropicApiBackend's own hidden endpoint field 1:1 (same rationale).
          @HttpUrl
              @TemplateProperty(
                  group = "provider",
                  label = "API endpoint",
                  type = TemplateProperty.PropertyType.Hidden,
                  feel = FeelMode.disabled,
                  optional = true)
              @Nullable String endpoint) {

        @Override
        public String toString() {
          return "GoogleGeminiApi{apiKey=[REDACTED], endpoint=" + endpoint + "}";
        }
      }
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
     * thinkingBudget}; Gemini 3.x models use {@code thinkingLevel}. Setting both is a hard API
     * error on 3.x models &mdash; this is validated below ({@link
     * #isBothThinkingBudgetAndLevelSet()}), not auto-resolved.
     */
    public record GeminiThinking(
        @TemplateProperty(
                group = "model-options",
                label = "Enable thinking",
                tooltip =
                    "Enables Gemini's extended-thinking mode. Configure a token budget (Gemini 2.5) or a qualitative level (Gemini 3.x) below once enabled. <br><br>Details in the <a href=\"https://ai.google.dev/gemini-api/docs/thinking\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Boolean,
                optional = true)
            Boolean enabled,
        @TemplateProperty(
                group = "model-options",
                label = "Thinking budget (tokens)",
                tooltip =
                    "Gemini 2.5 models: token budget for extended thinking. -1 = dynamic, 0 = disabled. Mutually exclusive with Thinking level (Gemini 3.x). <br><br>Details in the <a href=\"https://ai.google.dev/gemini-api/docs/thinking\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true,
                condition =
                    @TemplateProperty.PropertyCondition(
                        property = "provider.googleGemini.model.parameters.thinking.enabled",
                        equalsBoolean = TemplateProperty.EqualsBoolean.TRUE))
            @Nullable Integer thinkingBudget,
        @TemplateProperty(
                group = "model-options",
                label = "Thinking level",
                tooltip =
                    "Gemini 3.x models: qualitative thinking effort. \"Model default\" lets the model choose its own reasoning depth. Mutually exclusive with Thinking budget (Gemini 2.5). <br><br>Details in the <a href=\"https://ai.google.dev/gemini-api/docs/thinking\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Dropdown,
                choices = {
                  @DropdownPropertyChoice(value = "modelDefault", label = "Model default"),
                  @DropdownPropertyChoice(value = "minimal", label = "minimal"),
                  @DropdownPropertyChoice(value = "low", label = "low"),
                  @DropdownPropertyChoice(value = "medium", label = "medium"),
                  @DropdownPropertyChoice(value = "high", label = "high")
                },
                defaultValue = "modelDefault",
                optional = true,
                condition =
                    @TemplateProperty.PropertyCondition(
                        property = "provider.googleGemini.model.parameters.thinking.enabled",
                        equalsBoolean = TemplateProperty.EqualsBoolean.TRUE))
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
