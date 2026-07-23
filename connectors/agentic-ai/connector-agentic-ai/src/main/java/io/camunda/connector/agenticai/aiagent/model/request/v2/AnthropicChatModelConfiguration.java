/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.ANTHROPIC_ID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicEffort;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.ThinkingMode;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.HttpUrl;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CompatibleAuthentication;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Anthropic Messages wire format. Backends this PR: {@code anthropic-api} (direct, API key) and
 * {@code compatible} (Anthropic-compatible endpoint, optional API key auth).
 */
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

  @Override
  public String backend() {
    return anthropic.backend().type();
  }

  /** All Anthropic-specific configuration, nested under the {@code anthropic} wire key. */
  public record AnthropicConnection(
      @Valid @NotNull AnthropicBackend backend,
      @TemplateProperty(
              group = "provider",
              label = "API",
              type = TemplateProperty.PropertyType.Hidden,
              feel = FeelMode.disabled,
              defaultValue = "messages",
              defaultValueType = TemplateProperty.DefaultValueType.String)
          AnthropicApi api,
      @Valid @NotNull AnthropicModel model,
      @Valid @Nullable TimeoutConfiguration timeouts,
      @TemplateProperty(
              group = "model",
              label = "Enable prompt caching",
              tooltip =
                  "Enables Anthropic automatic prompt caching by adding a top-level "
                      + "<code>cache_control: {\"type\": \"ephemeral\"}</code> to each request. The API "
                      + "automatically caches the longest stable prefix (system prompt, tool definitions "
                      + "and earlier conversation messages) and reuses it across requests made within the "
                      + "cache lifetime (5 minutes). Cache hits require a byte-identical prefix; the "
                      + "system prompt and tools stay stable across turns, but note that once the message "
                      + "window starts evicting the oldest messages the message-history portion of the "
                      + "prefix shifts each turn. See the <a href=\"https://platform.claude.com/docs/en/build-with-claude/prompt-caching#automatic-caching\" target=\"_blank\">automatic caching documentation</a>.",
              type = TemplateProperty.PropertyType.Boolean,
              defaultValue = "false",
              defaultValueType = TemplateProperty.DefaultValueType.Boolean,
              optional = true)
          @Nullable Boolean enablePromptCaching) {

    /**
     * Convenience constructor for callers that do not need to set the (currently single-valued)
     * {@code api} field explicitly; it defaults to {@link AnthropicApi#MESSAGES}.
     */
    public AnthropicConnection(
        AnthropicBackend backend,
        AnthropicModel model,
        @Nullable TimeoutConfiguration timeouts,
        @Nullable Boolean enablePromptCaching) {
      this(backend, AnthropicApi.MESSAGES, model, timeouts, enablePromptCaching);
    }

    public AnthropicConnection {
      if (api == null) {
        api = AnthropicApi.MESSAGES;
      }
    }
  }

  /**
   * Anthropic wire-format API selector. Single value ({@code messages}) this PR; the property is
   * hidden in the template until a second value is introduced.
   */
  public enum AnthropicApi {
    @JsonProperty("messages")
    MESSAGES
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = AnthropicBackend.AnthropicApiBackend.class, name = "anthropic-api"),
    @JsonSubTypes.Type(
        value = AnthropicBackend.AnthropicCompatibleBackend.class,
        name = "compatible")
  })
  @TemplateDiscriminatorProperty(
      label = "Connection",
      group = "provider",
      name = "type",
      defaultValue = "anthropic-api",
      description = "Specify how the Anthropic Messages API is reached.")
  public sealed interface AnthropicBackend {

    /** The backend discriminator string. */
    String type();

    @TemplateSubType(id = "anthropic-api", label = "Anthropic API")
    record AnthropicApiBackend(
        @NotBlank
            @TemplateProperty(
                group = "provider",
                label = "Anthropic API key",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            String apiKey)
        implements AnthropicBackend {

      @Override
      public String type() {
        return "anthropic-api";
      }

      @Override
      public String toString() {
        return "AnthropicApiBackend{apiKey=[REDACTED]}";
      }
    }

    @TemplateSubType(id = "compatible", label = "Custom / compatible endpoint")
    record AnthropicCompatibleBackend(
        @NotBlank
            @HttpUrl
            @TemplateProperty(
                group = "provider",
                label = "API endpoint",
                description =
                    "Base URL of the Anthropic-compatible Messages API (e.g. <code>https://api.anthropic.com</code>).",
                tooltip = "The connector appends <code>/v1/messages</code>.",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
                placeholder = "https://api.anthropic.com",
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            String endpoint,
        @TemplateProperty(
                group = "provider",
                label = "Headers",
                description = "Map of HTTP headers to add to the request.",
                feel = FeelMode.required,
                optional = true)
            @Nullable Map<String, String> headers,
        @Valid
            @TemplateProperty(
                group = "provider",
                label = "Query parameters",
                description = "Map of query parameters to add to the request URL.",
                feel = FeelMode.required,
                optional = true)
            @Nullable Map<@NotBlank String, String> queryParameters,
        @TemplateProperty(
                group = "provider",
                label = "Request parameters",
                description = "Map of additional request (body) parameters to include.",
                feel = FeelMode.required,
                optional = true)
            @Nullable Map<String, Object> requestParameters,
        @Valid @NotNull CompatibleAuthentication compatibleAuthentication)
        implements AnthropicBackend {

      @Override
      public String type() {
        return "compatible";
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
      @Valid @Nullable AnthropicModelParameters parameters) {

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
            @Nullable Integer maxTokens,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "Temperature",
                tooltip =
                    "Floating point number between 0 and 1. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-temperature\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            @Nullable Double temperature,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "top P",
                tooltip =
                    "Floating point number between 0 and 1. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-top-p\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            @Nullable Double topP,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "top K",
                tooltip =
                    "Integer greater than 0. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-top-k\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            @Nullable Integer topK,
        @TemplateProperty(
                group = "model",
                label = "Effort",
                description = "Leave unset to use the model default.",
                tooltip =
                    "Controls how many tokens the model spends when responding, trading thoroughness against speed and cost. It affects all output — text, tool calls and extended thinking. <code>low</code> is the most efficient (fewest tokens, fastest, some capability reduction); <code>medium</code> balances speed, cost and quality; <code>high</code> is full capability; <code>xhigh</code> targets long-running coding and agentic work; <code>max</code> gives maximum capability with no token constraints. Not supported on all models. See the <a href=\"https://platform.claude.com/docs/en/build-with-claude/effort\" target=\"_blank\">effort documentation</a>.",
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
        @Valid @Nullable AnthropicThinking thinking) {}

    /** Anthropic extended-thinking configuration for a single model. */
    public record AnthropicThinking(
        @TemplateProperty(
                group = "model",
                label = "Thinking mode",
                tooltip =
                    "Extended thinking mechanism. Leave blank to use the model default. "
                        + "'enabled' = manual token budget (older models); 'adaptive' = model-managed "
                        + "(newer models); 'disabled' = off. Support varies by model.",
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
                    "Max tokens the model may spend on extended thinking. Required and used only when "
                        + "thinking mode is 'enabled' (min 1024).",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true,
                condition =
                    @TemplateProperty.PropertyCondition(
                        property = "provider.anthropic.model.parameters.thinking.mode",
                        equals = "enabled"))
            @Nullable Integer budgetTokens,
        @TemplateProperty(
                group = "model",
                label = "Thinking display",
                tooltip =
                    "Controls how the model's extended thinking is returned: <code>summarized</code> includes a plain-text summary of the thinking in the response; <code>omitted</code> leaves it out.",
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

    /** Adaptive-thinking output display mode (config-only; Anthropic wire format). */
    public enum ThinkingDisplay {
      @JsonProperty("summarized")
      SUMMARIZED,
      @JsonProperty("omitted")
      OMITTED
    }
  }
}
