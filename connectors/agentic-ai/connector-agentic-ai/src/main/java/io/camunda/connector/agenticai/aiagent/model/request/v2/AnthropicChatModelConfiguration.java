/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.ANTHROPIC_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend.ANTHROPIC_API_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCustomBackend.CUSTOM_ID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.HttpUrl;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CustomEndpointAuthentication;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@TemplateSubType(id = ANTHROPIC_ID, label = "Anthropic")
public record AnthropicChatModelConfiguration(@Valid @NotNull AnthropicConnection anthropic)
    implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String ANTHROPIC_ID = "anthropic";

  @Override
  public String provider() {
    return ANTHROPIC_ID;
  }

  @Override
  public String model() {
    return anthropic.model().model();
  }

  /**
   * Redacts a map's values (which may carry secrets) for logging while keeping its keys visible.
   */
  private static @Nullable Map<String, String> redactValues(@Nullable Map<String, ?> map) {
    if (map == null) {
      return null;
    }
    final var redacted = new LinkedHashMap<String, String>();
    map.keySet().forEach(key -> redacted.put(key, "[REDACTED]"));
    return redacted;
  }

  /** All Anthropic-specific configuration, nested under the {@code anthropic} wire key. */
  public record AnthropicConnection(
      @Valid @NotNull AnthropicBackend backend,
      @Valid @NotNull AnthropicModel model,
      @Valid @Nullable TimeoutConfiguration timeouts) {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = AnthropicBackend.AnthropicApiBackend.class, name = ANTHROPIC_API_ID),
    @JsonSubTypes.Type(value = AnthropicBackend.AnthropicCustomBackend.class, name = CUSTOM_ID)
  })
  @TemplateDiscriminatorProperty(
      label = "Backend",
      group = "provider",
      name = "type",
      defaultValue = ANTHROPIC_API_ID,
      description = "Specify how the Anthropic Messages API is reached.")
  public sealed interface AnthropicBackend {

    /** The backend discriminator string. */
    String type();

    @TemplateSubType(id = ANTHROPIC_API_ID, label = "Anthropic API")
    record AnthropicApiBackend(@Valid @NotNull AnthropicApi anthropic) implements AnthropicBackend {

      @TemplateProperty(ignore = true)
      public static final String ANTHROPIC_API_ID = "anthropic-api";

      @Override
      public String type() {
        return ANTHROPIC_API_ID;
      }

      public record AnthropicApi(
          @NotBlank
              @TemplateProperty(
                  group = "provider",
                  label = "Anthropic API key",
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
                  optional = true)
              @Nullable String endpoint,
          @TemplateProperty(
                  group = "provider",
                  label = "Headers",
                  tooltip = "Map of HTTP headers to add to the request.",
                  type = TemplateProperty.PropertyType.Hidden,
                  feel = FeelMode.disabled,
                  optional = true)
              @Nullable Map<String, String> headers,
          @Valid
              @TemplateProperty(
                  group = "provider",
                  label = "Query parameters",
                  tooltip = "Map of query parameters to add to the request URL.",
                  type = TemplateProperty.PropertyType.Hidden,
                  feel = FeelMode.disabled,
                  optional = true)
              @Nullable Map<@NotBlank String, String> queryParameters,
          @TemplateProperty(
                  group = "model-options",
                  label = "Request parameters",
                  tooltip = "Map of additional parameters to include in the request body.",
                  type = TemplateProperty.PropertyType.Hidden,
                  feel = FeelMode.disabled,
                  optional = true)
              @Nullable Map<String, Object> requestParameters) {

        @Override
        public String toString() {
          return "AnthropicApi{apiKey=[REDACTED], endpoint="
              + endpoint
              + ", headers="
              + redactValues(headers)
              + ", queryParameters="
              + redactValues(queryParameters)
              + ", requestParameters="
              + redactValues(requestParameters)
              + "}";
        }
      }
    }

    @TemplateSubType(id = CUSTOM_ID, label = "Custom / compatible endpoint")
    record AnthropicCustomBackend(@Valid @NotNull CustomBackend custom)
        implements AnthropicBackend {

      @TemplateProperty(ignore = true)
      public static final String CUSTOM_ID = "custom";

      @Override
      public String type() {
        return CUSTOM_ID;
      }

      public record CustomBackend(
          @NotBlank
              @HttpUrl
              @TemplateProperty(
                  group = "provider",
                  label = "API endpoint",
                  description =
                      "Base URL of the Anthropic-compatible API; <code>/v1/messages</code> will be appended.",
                  type = TemplateProperty.PropertyType.String,
                  feel = FeelMode.optional,
                  placeholder = "https://api.anthropic.com",
                  constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
              String endpoint,
          @TemplateProperty(
                  group = "provider",
                  label = "Headers",
                  tooltip = "Map of HTTP headers to add to the request.",
                  feel = FeelMode.required,
                  optional = true)
              @Nullable Map<String, String> headers,
          @Valid
              @TemplateProperty(
                  group = "provider",
                  label = "Query parameters",
                  tooltip = "Map of query parameters to add to the request URL.",
                  feel = FeelMode.required,
                  optional = true)
              @Nullable Map<@NotBlank String, String> queryParameters,
          @TemplateProperty(
                  group = "model-options",
                  label = "Request parameters",
                  tooltip = "Map of additional parameters to include in the request body.",
                  feel = FeelMode.required,
                  optional = true)
              @Nullable Map<String, Object> requestParameters,
          @Valid @NotNull CustomEndpointAuthentication authentication) {

        @Override
        public String toString() {
          return "CustomBackend{endpoint="
              + endpoint
              + ", headers="
              + redactValues(headers)
              + ", queryParameters="
              + redactValues(queryParameters)
              + ", requestParameters="
              + redactValues(requestParameters)
              + ", authentication="
              + authentication
              + "}";
        }
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
              placeholder = "claude-sonnet-5",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String model,
      @Valid @Nullable AnthropicModelParameters parameters) {

    public record AnthropicModelParameters(
        @TemplateProperty(
                group = "model",
                label = "Effort",
                description = "Leave unset to use the model default.",
                tooltip =
                    "Controls how many tokens the model spends when responding, trading thoroughness against speed and cost. Not supported on all models."
                        + "<br><br>See the <a href=\"https://platform.claude.com/docs/en/build-with-claude/effort\" target=\"_blank\">effort documentation</a>.",
                type = TemplateProperty.PropertyType.Dropdown,
                choices = {
                  @DropdownPropertyChoice(value = "low", label = "low"),
                  @DropdownPropertyChoice(value = "medium", label = "medium"),
                  @DropdownPropertyChoice(value = "high", label = "high"),
                  @DropdownPropertyChoice(value = "xhigh", label = "xhigh"),
                  @DropdownPropertyChoice(value = "max", label = "max")
                },
                optional = true)
            @Nullable AnthropicEffort effort,
        @Valid @Nullable AnthropicThinking thinking,
        @Valid @Nullable AnthropicPromptCaching promptCaching,
        @Min(1)
            @TemplateProperty(
                group = "model-options",
                label = "Maximum tokens",
                tooltip =
                    "The maximum number of tokens per request to generate before stopping. <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-max-tokens\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            @Nullable Integer maxTokens,
        @DecimalMin("0.0")
            @DecimalMax("1.0")
            @TemplateProperty(
                group = "model-options",
                label = "Temperature",
                tooltip =
                    "Floating point number between 0 and 1. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-temperature\" target=\"_blank\">documentation</a>.",
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
                    "Floating point number between 0 and 1. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-top-p\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            @Nullable Double topP,
        @Min(1)
            @TemplateProperty(
                group = "model-options",
                label = "top K",
                tooltip =
                    "Integer greater than 0. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-top-k\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            @Nullable Integer topK) {

      @JsonIgnore
      @AssertTrue(message = "thinking.budgetTokens must be less than maxTokens")
      public boolean isThinkingBudgetWithinMaxTokens() {
        if (thinking == null
            || thinking.mode() != ThinkingMode.ENABLED
            || thinking.budgetTokens() == null
            || maxTokens == null) {
          return true;
        }
        return thinking.budgetTokens() < maxTokens;
      }
    }

    /** Anthropic effort levels, trading thoroughness against speed and cost. */
    public enum AnthropicEffort {
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

    /**
     * Anthropic automatic prompt caching. A record rather than a bare boolean so it stays
     * extensible: a cache-type (e.g. explicit breakpoints instead of automatic) or a configurable
     * TTL could be added as further fields without changing this property's wire shape.
     */
    public record AnthropicPromptCaching(
        @TemplateProperty(
                group = "model",
                label = "Enable prompt caching",
                tooltip =
                    "Enables Anthropic automatic prompt caching. See the <a href=\"https://platform.claude.com/docs/en/build-with-claude/prompt-caching#automatic-caching\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Boolean,
                defaultValue = "false",
                defaultValueType = TemplateProperty.DefaultValueType.Boolean,
                optional = true)
            @Nullable Boolean enabled) {}

    /** Anthropic extended-thinking configuration for a single model. */
    public record AnthropicThinking(
        @TemplateProperty(
                group = "model",
                label = "Thinking mode",
                tooltip =
                    "Extended thinking mechanism. Leave blank to use the model default."
                        + "<br><br><code>enabled</code> uses a manual token budget (older models). "
                        + "<code>adaptive</code> is managed by the model (newer models). "
                        + "<code>disabled</code> turns it off."
                        + "<br><br>Support varies by model.",
                type = TemplateProperty.PropertyType.Dropdown,
                choices = {
                  @DropdownPropertyChoice(value = "enabled", label = "enabled"),
                  @DropdownPropertyChoice(value = "adaptive", label = "adaptive"),
                  @DropdownPropertyChoice(value = "disabled", label = "disabled")
                },
                optional = true)
            @Nullable ThinkingMode mode,
        @Min(1024)
            @TemplateProperty(
                group = "model",
                label = "Thinking budget tokens",
                tooltip =
                    "Maximum number of tokens the model may spend on extended thinking (minimum 1024).",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                condition =
                    @TemplateProperty.PropertyCondition(
                        property = "provider.anthropic.model.parameters.thinking.mode",
                        equals = "enabled"))
            @Nullable Integer budgetTokens,
        @TemplateProperty(
                group = "model",
                label = "Thinking display",
                tooltip =
                    "Controls how the model's extended thinking is returned. <code>summarized</code> includes a "
                        + "plain-text summary of the thinking in the response. <code>omitted</code> leaves it out."
                        + "<br><br>Leave unset to use <code>summarized</code>.",
                type = TemplateProperty.PropertyType.Dropdown,
                choices = {
                  @DropdownPropertyChoice(value = "summarized", label = "summarized"),
                  @DropdownPropertyChoice(value = "omitted", label = "omitted")
                },
                defaultValue = "summarized",
                defaultValueType = TemplateProperty.DefaultValueType.String,
                optional = true,
                condition =
                    @TemplateProperty.PropertyCondition(
                        property = "provider.anthropic.model.parameters.thinking.mode",
                        equals = "adaptive"))
            @Nullable ThinkingDisplay display) {}

    /**
     * Anthropic extended-thinking mechanisms a model may support: {@code enabled} (manual token
     * budget, older models), {@code adaptive} (model-managed, newer models) or {@code disabled}.
     */
    public enum ThinkingMode {
      @JsonProperty("enabled")
      ENABLED,
      @JsonProperty("adaptive")
      ADAPTIVE,
      @JsonProperty("disabled")
      DISABLED
    }

    /** Adaptive-thinking output display mode (config-only; Anthropic wire format). */
    public enum ThinkingDisplay {
      @JsonProperty("summarized")
      SUMMARIZED,
      @JsonProperty("omitted")
      OMITTED
    }
  }
}
