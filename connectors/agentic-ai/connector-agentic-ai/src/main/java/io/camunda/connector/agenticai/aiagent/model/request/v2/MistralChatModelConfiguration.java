/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralBackend.MistralApiBackend.MISTRAL_API_ID;
import static io.camunda.connector.agenticai.aiagent.util.LoggingSupport.redactValues;

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
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Native (non-LangChain4j) v2 configuration for the Mistral AI provider. Mistral's Chat Completions
 * surface is OpenAI-shaped, so the wire mapping itself reuses the OpenAI provider's Chat
 * Completions converters (see {@code MistralChatModelFactory}); this record only carries Mistral's
 * own connection details and the handful of parameters its API actually accepts.
 *
 * <p>The backend is modeled as a sealed union with a single member ({@link
 * MistralBackend.MistralApiBackend}) rather than a flat connection record, matching the convention
 * every other v2 provider follows ({@code ai-agent.md} §25.1): adding a second Mistral backend
 * later (e.g. Microsoft Foundry or AWS Bedrock Mantle) is then purely additive, with no change to
 * any existing template property path.
 */
@TemplateSubType(id = MistralChatModelConfiguration.MISTRAL_ID, label = "Mistral AI")
public record MistralChatModelConfiguration(@Valid @NotNull MistralConnection mistral)
    implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String MISTRAL_ID = "mistral";

  @Override
  public String provider() {
    return MISTRAL_ID;
  }

  @Override
  public String model() {
    return mistral.model().model();
  }

  @Override
  public String descriptiveProvider() {
    return "%s/%s".formatted(provider(), mistral.backend().type());
  }

  /** All Mistral-specific configuration, nested under the {@code mistral} wire key. */
  public record MistralConnection(
      @Valid @NotNull MistralBackend backend,
      @Valid @NotNull MistralModel model,
      @Valid @Nullable MistralParameters parameters,
      @Valid @Nullable TimeoutConfiguration timeouts) {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = MistralBackend.MistralApiBackend.class, name = MISTRAL_API_ID)
  })
  @TemplateDiscriminatorProperty(
      label = "Connection",
      group = "provider",
      name = "type",
      defaultValue = MISTRAL_API_ID,
      description = "Specify how the Mistral API is reached.")
  public sealed interface MistralBackend {

    /** The backend discriminator string. */
    String type();

    OpenAiRequestCustomizations requestCustomizations();

    @TemplateSubType(id = MISTRAL_API_ID, label = "Mistral API")
    record MistralApiBackend(@Valid @NotNull MistralApiConnection mistral)
        implements MistralBackend {

      @TemplateProperty(ignore = true)
      public static final String MISTRAL_API_ID = "mistral-api";

      @Override
      public String type() {
        return MISTRAL_API_ID;
      }

      @Override
      public OpenAiRequestCustomizations requestCustomizations() {
        return new OpenAiRequestCustomizations(
            mistral.headers(), mistral.queryParameters(), mistral.bodyProperties());
      }

      public record MistralApiConnection(
          @NotBlank
              @TemplateProperty(
                  group = "provider",
                  label = "Mistral API key",
                  type = TemplateProperty.PropertyType.String,
                  feel = FeelMode.optional,
                  constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
              String apiKey,
          @HttpUrl
              @TemplateProperty(
                  group = "provider",
                  label = "API endpoint",
                  type = TemplateProperty.PropertyType.Hidden,
                  feel = FeelMode.disabled,
                  optional = true,
                  defaultValue = "https://api.mistral.ai/v1",
                  defaultValueType = TemplateProperty.DefaultValueType.String)
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
          return "MistralApiConnection{apiKey=[REDACTED], endpoint="
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
  }

  public record MistralModel(
      @NotBlank
          @TemplateProperty(
              group = "model",
              label = "Model",
              tooltip =
                  "Specify the model ID. See the <a href=\"https://docs.mistral.ai/getting-started/models/models_overview/\" target=\"_blank\">models documentation</a>.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              defaultValue = "",
              defaultValueType = TemplateProperty.DefaultValueType.String,
              placeholder = "mistral-medium-latest",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String model) {}

  public record MistralParameters(
      @Min(1)
          @TemplateProperty(
              group = "model-options",
              label = "Max tokens",
              tooltip =
                  "The maximum number of tokens per request to generate before stopping. <br><br>Details in the <a href=\"https://docs.mistral.ai/api/endpoint/chat\" target=\"_blank\">documentation</a>.",
              type = TemplateProperty.PropertyType.Number,
              feel = FeelMode.required,
              optional = true)
          @Nullable Integer maxTokens,
      @TemplateProperty(
              group = "model",
              label = "Effort",
              tooltip =
                  "Controls how many tokens the model spends thinking before responding. Only supported on reasoning-capable models."
                      + "<br><br>See the <a href=\"https://docs.mistral.ai/capabilities/reasoning/\" target=\"_blank\">Mistral reasoning documentation</a>.",
              type = TemplateProperty.PropertyType.Dropdown,
              choices = {
                @DropdownPropertyChoice(value = "modelDefault", label = "default"),
                @DropdownPropertyChoice(value = "none", label = "none"),
                @DropdownPropertyChoice(value = "high", label = "high")
              },
              defaultValue = "modelDefault")
          @Nullable MistralEffort effort,
      @DecimalMin("0.0")
          @DecimalMax("1.5")
          @TemplateProperty(
              group = "model-options",
              label = "Temperature",
              tooltip =
                  "Floating point number between 0 and 1.5. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://docs.mistral.ai/api/endpoint/chat\" target=\"_blank\">documentation</a>.",
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
                  "Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://docs.mistral.ai/api/endpoint/chat\" target=\"_blank\">documentation</a>.",
              type = TemplateProperty.PropertyType.Number,
              feel = FeelMode.required,
              optional = true)
          @Nullable Double topP) {}

  /**
   * Mistral's reasoning-effort dial. The API's {@code ReasoningEffort} type declares six values
   * ({@code none/minimal/low/medium/high/xhigh}), but {@code mistral-medium-3-5} -- the only model
   * in the real-provider acceptance matrix claiming the {@code REASONING} capability -- rejects
   * every value except {@code none} and {@code high} with an HTTP 400 (confirmed against the live
   * API), matching the two-way toggle Mistral's own playground exposes for reasoning effort. This
   * enum only offers the values that actually work.
   */
  public enum MistralEffort {
    @JsonProperty("modelDefault")
    MODEL_DEFAULT,
    @JsonProperty("none")
    NONE,
    @JsonProperty("high")
    HIGH
  }
}
