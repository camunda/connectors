/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.ANTHROPIC_ID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilitiesMode;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilitiesOverride;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicEffort;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.ThinkingMode;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.HttpUrl;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.ChatModelAwsAuthentication;
import io.camunda.connector.agenticai.aiagent.util.ConnectorUtils;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Anthropic Messages wire format. Backends: {@code direct} (API key) and {@code bedrock} (AWS). */
@TemplateSubType(id = ANTHROPIC_ID, label = "Anthropic")
public record AnthropicChatModel(@Valid @NotNull AnthropicConnection anthropic)
    implements V2ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String ANTHROPIC_ID = "anthropic";

  @Override
  public String providerType() {
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

  @Override
  public @Nullable ModelCapabilitiesOverride capabilityOverride() {
    return anthropic.capabilityOverride();
  }

  /** All Anthropic-specific configuration, nested under the {@code anthropic} wire key. */
  public record AnthropicConnection(
      @Valid @NotNull AnthropicBackend backend,
      @TemplateProperty(
              group = "capabilities",
              label = "Model capabilities",
              description =
                  "How model capabilities are resolved. 'Auto' uses the built-in capability data for the selected model; 'Custom' lets you supply the overrides below.",
              type = TemplateProperty.PropertyType.Dropdown,
              defaultValue = "auto",
              choices = {
                @DropdownPropertyChoice(value = "auto", label = "Auto"),
                @DropdownPropertyChoice(value = "custom", label = "Custom")
              },
              optional = true)
          @Nullable ModelCapabilitiesMode capabilityMode,
      @Valid
          @TemplateProperty(
              group = "capabilities",
              label = "Model capability overrides",
              description =
                  "Optional sparse capability override (FEEL context) deep-merged as the highest-precedence layer over the resolved model capabilities. Use for unknown/custom models.",
              type = TemplateProperty.PropertyType.Text,
              feel = FeelMode.required,
              placeholder = "{contextWindow: 200000, maxOutputTokens: 8192}",
              optional = true,
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "provider.anthropic.capabilityMode",
                      equals = "custom"))
          @Nullable ModelCapabilitiesOverride capabilityOverride,
      @Valid @NotNull AnthropicModel model,
      @Valid @Nullable TimeoutConfiguration timeouts,
      @FEEL
          @TemplateProperty(
              group = "skills",
              label = "Skills",
              description =
                  "Anthropic Agent Skills as <code>type:skill:version</code> strings, e.g. <code>pptx</code> or <code>custom:my-skill:v2</code>.",
              tooltip =
                  "Skills made available to the model. Type and version default to <code>anthropic</code>/<code>latest</code>. Custom skills must be uploaded to Anthropic before they can be referenced here; Anthropic-provided skills (such as <code>pptx</code>) need no upload. Configuring skills automatically enables the <code>code_execution</code> tool and the required beta headers. Maximum of 8 skills. See the <a href=\"https://platform.claude.com/docs/en/build-with-claude/skills-guide\" target=\"_blank\">Agent Skills documentation</a>.",
              type = TemplateProperty.PropertyType.Text,
              feel = FeelMode.required,
              placeholder =
                  "[\"pptx\", \"xlsx:20260710\", \"custom:skill_01AbCdEfGhIjKlMnOpQrStUv:latest\"]",
              optional = true)
          @Nullable List<@NotBlank String> skills,
      @TemplateProperty(
              group = "skills",
              label = "Enable code execution",
              tooltip =
                  "Enables Anthropic's built-in <code>code_execution</code> server tool, letting the model run code in a sandboxed container. Already enabled automatically when Skills are configured above (no duplicate tool/beta header is emitted in that case).",
              type = TemplateProperty.PropertyType.Boolean,
              defaultValue = "false",
              defaultValueType = TemplateProperty.DefaultValueType.Boolean,
              optional = true)
          @Nullable Boolean enableCodeExecution,
      @TemplateProperty(
              group = "skills",
              label = "Code execution tool version",
              tooltip =
                  "Anthropic <code>code_execution</code> tool version string (wire <code>type</code>). "
                      + "Defaults to the latest GA revision <code>code_execution_20260521</code>, which "
                      + "needs no beta header and is required (version <code>20260120</code> or later) for "
                      + "the default dynamic-filtering web tools to run in the same request. This version "
                      + "also applies to the <code>code_execution</code> tool that Skills provision "
                      + "automatically. The legacy <code>code_execution_20250522</code> revision is "
                      + "Python-only and additionally sends the <code>code-execution-2025-05-22</code> "
                      + "beta header.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              defaultValue = "code_execution_20260521",
              optional = true,
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "provider.anthropic.enableCodeExecution",
                      equalsBoolean = TemplateProperty.EqualsBoolean.TRUE))
          @Nullable String codeExecutionVersion,
      @TemplateProperty(
              group = "skills",
              label = "Enable web search",
              tooltip =
                  "Enables Anthropic's built-in <code>web_search</code> server tool, letting the model search the web for up-to-date information.",
              type = TemplateProperty.PropertyType.Boolean,
              defaultValue = "false",
              defaultValueType = TemplateProperty.DefaultValueType.Boolean,
              optional = true)
          @Nullable Boolean enableWebSearch,
      @TemplateProperty(
              group = "skills",
              label = "Web search tool version",
              tooltip =
                  "Anthropic <code>web_search</code> tool version string (wire <code>type</code>). "
                      + "The default <code>web_search_20260318</code> uses dynamic filtering (the tool "
                      + "runs inside code execution) and works alongside Skills and code execution when "
                      + "the code execution tool is version <code>20260120</code> or later (the default). "
                      + "It requires a programmatic-tool-calling model and is not ZDR-eligible. Downgrade "
                      + "to a basic/direct revision such as <code>web_search_20250305</code> for ZDR or "
                      + "older/non-programmatic models.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              defaultValue = "web_search_20260318",
              optional = true,
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "provider.anthropic.enableWebSearch",
                      equalsBoolean = TemplateProperty.EqualsBoolean.TRUE))
          @Nullable String webSearchVersion,
      @TemplateProperty(
              group = "skills",
              label = "Enable web fetch",
              tooltip =
                  "Enables Anthropic's built-in <code>web_fetch</code> server tool, letting the model retrieve the full content of a URL.",
              type = TemplateProperty.PropertyType.Boolean,
              defaultValue = "false",
              defaultValueType = TemplateProperty.DefaultValueType.Boolean,
              optional = true)
          @Nullable Boolean enableWebFetch,
      @TemplateProperty(
              group = "skills",
              label = "Web fetch tool version",
              tooltip =
                  "Anthropic <code>web_fetch</code> tool version string (wire <code>type</code>). "
                      + "The default <code>web_fetch_20260318</code> uses dynamic filtering (the tool "
                      + "runs inside code execution) and works alongside Skills and code execution when "
                      + "the code execution tool is version <code>20260120</code> or later (the default). "
                      + "It requires a programmatic-tool-calling model and is not ZDR-eligible. Downgrade "
                      + "to a basic/direct revision such as <code>web_fetch_20250910</code> for ZDR or "
                      + "older/non-programmatic models.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              defaultValue = "web_fetch_20260318",
              optional = true,
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "provider.anthropic.enableWebFetch",
                      equalsBoolean = TemplateProperty.EqualsBoolean.TRUE))
          @Nullable String webFetchVersion,
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
          @Nullable Boolean enablePromptCaching) {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = AnthropicBackend.AnthropicDirectBackend.class, name = "direct"),
    @JsonSubTypes.Type(value = AnthropicBackend.AnthropicBedrockBackend.class, name = "bedrock")
  })
  @TemplateDiscriminatorProperty(
      label = "Backend",
      group = "provider",
      name = "type",
      defaultValue = "direct",
      description = "Specify how the Anthropic Messages API is reached.")
  public sealed interface AnthropicBackend {

    /** The backend discriminator string. */
    String type();

    @TemplateSubType(id = "direct", label = "Anthropic (direct)")
    record AnthropicDirectBackend(
        @HttpUrl
            @TemplateProperty(
                id = "direct.endpoint",
                binding = @TemplateProperty.PropertyBinding(name = "endpoint"),
                group = "provider",
                label = "Custom API endpoint",
                description = "Optional custom API endpoint",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
                optional = true)
            @Nullable String endpoint,
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
        return "direct";
      }

      @Override
      public String toString() {
        return "AnthropicDirectBackend{endpoint=%s, apiKey=[REDACTED]}".formatted(endpoint);
      }
    }

    @TemplateSubType(id = "bedrock", label = "AWS Bedrock")
    record AnthropicBedrockBackend(
        @NotBlank
            @TemplateProperty(
                group = "provider",
                label = "Region",
                description = "Specify the AWS region (example: <code>eu-west-1</code>)",
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            String region,
        @HttpUrl
            @TemplateProperty(
                id = "bedrock.endpoint",
                binding = @TemplateProperty.PropertyBinding(name = "endpoint"),
                group = "provider",
                label = "Custom API endpoint",
                description =
                    "Custom API endpoint for VPC/PrivateLink configurations, AWS GovCloud, or other non-standard deployments.",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
                optional = true)
            @Nullable String endpoint,
        @Valid @NotNull ChatModelAwsAuthentication authentication)
        implements AnthropicBackend {

      @Override
      public String type() {
        return "bedrock";
      }

      @AssertFalse(message = "AWS default credentials chain is not supported on SaaS")
      @SuppressWarnings("unused")
      public boolean isDefaultCredentialsChainUsedInSaaS() {
        return ConnectorUtils.isSaaS()
            && authentication
                instanceof ChatModelAwsAuthentication.AwsDefaultCredentialsChainAuthentication;
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
                group = "capabilities",
                label = "Maximum tokens",
                tooltip =
                    "The maximum number of tokens per request to generate before stopping. <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-max-tokens\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            @Nullable Integer maxTokens,
        @Min(0)
            @TemplateProperty(
                group = "capabilities",
                label = "Temperature",
                tooltip =
                    "Floating point number between 0 and 1. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-temperature\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            @Nullable Double temperature,
        @Min(0)
            @TemplateProperty(
                group = "capabilities",
                label = "top P",
                tooltip =
                    "Floating point number between 0 and 1. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-top-p\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            @Nullable Double topP,
        @Min(0)
            @TemplateProperty(
                group = "capabilities",
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
