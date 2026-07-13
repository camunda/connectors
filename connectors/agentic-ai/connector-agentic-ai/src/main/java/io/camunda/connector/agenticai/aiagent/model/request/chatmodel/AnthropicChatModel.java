/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel;

import static io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.ANTHROPIC_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesOverride;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.shared.ChatModelAwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.HttpUrl;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Anthropic Messages wire format. Backends: {@code direct} (API key) and {@code bedrock} (AWS). */
@TemplateSubType(id = ANTHROPIC_ID, label = "Anthropic")
public record AnthropicChatModel(@Valid @NotNull AnthropicConnection anthropic)
    implements LlmProviderConfiguration {

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
      @Valid @NotNull AnthropicModel model,
      @Valid @Nullable TimeoutConfiguration timeouts,
      @Valid
          @TemplateProperty(
              group = "capabilities",
              label = "Model capability overrides",
              description =
                  "Optional sparse capability override (FEEL context) deep-merged as the highest-precedence layer over the resolved model capabilities. Use for unknown/custom models.",
              type = TemplateProperty.PropertyType.Text,
              feel = FeelMode.required,
              placeholder = "={contextWindow: 200000, maxOutputTokens: 8192}",
              optional = true)
          @Nullable ModelCapabilitiesOverride capabilityOverride,
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
                  "=[\"pptx\", \"xlsx:20260710\", \"custom:skill_01AbCdEfGhIjKlMnOpQrStUv:latest\"]",
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
                      + "The default <code>web_search_20250305</code> calls directly and works alongside "
                      + "Skills and code execution in the same request. Newer versions "
                      + "(<code>web_search_20260209</code> and later) enable dynamic filtering (the tool "
                      + "runs inside code execution) and are <b>not yet</b> compatible with Skills or code "
                      + "execution in the same request.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              defaultValue = "web_search_20250305",
              optional = true,
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "configuration.anthropic.enableWebSearch",
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
                      + "The default <code>web_fetch_20250910</code> calls directly and works alongside "
                      + "Skills and code execution in the same request. Newer versions "
                      + "(<code>web_fetch_20260209</code> and later) enable dynamic filtering (the tool "
                      + "runs inside code execution) and are <b>not yet</b> compatible with Skills or code "
                      + "execution in the same request.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              defaultValue = "web_fetch_20250910",
              optional = true,
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "configuration.anthropic.enableWebFetch",
                      equalsBoolean = TemplateProperty.EqualsBoolean.TRUE))
          @Nullable String webFetchVersion) {}

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
            @Nullable Integer topK) {}
  }
}
