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
      @FEEL
          @Valid
          @TemplateProperty(
              group = "capabilities",
              label = "Model capability overrides",
              description =
                  "Optional sparse capability override (FEEL context) deep-merged as the highest-precedence layer over the resolved model capabilities. Use for unknown/custom models.",
              type = TemplateProperty.PropertyType.Text,
              feel = FeelMode.required,
              optional = true)
          @Nullable ModelCapabilitiesOverride capabilityOverride) {}

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
            @FEEL
            @TemplateProperty(
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
