/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel;

import static io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OPENAI_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesOverride;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.HttpUrl;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
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
 * OpenAI wire formats ({@code completions}/{@code responses}); backends {@code direct}/{@code
 * compatible}.
 */
@TemplateSubType(id = OPENAI_ID, label = "OpenAI")
public record OpenAiChatModel(@Valid @NotNull OpenAiConnection openai)
    implements V2ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String OPENAI_ID = "openai";

  @Override
  public String providerType() {
    return OPENAI_ID;
  }

  @Override
  public String model() {
    return openai.model().model();
  }

  @Override
  public String backend() {
    return openai.backend().type();
  }

  @Override
  public @Nullable ModelCapabilitiesOverride capabilityOverride() {
    return openai.capabilityOverride();
  }

  /**
   * The capability-matrix api-family key ({@code openai-completions} / {@code openai-responses}).
   */
  public String apiFamilyKey() {
    return openai.apiFamily().familyKey();
  }

  /** All OpenAI-specific configuration, nested under the {@code openai} wire key. */
  public record OpenAiConnection(
      @NotNull
          @TemplateProperty(
              group = "provider",
              label = "API family",
              description = "OpenAI wire format to use.",
              type = TemplateProperty.PropertyType.Dropdown,
              defaultValue = "completions",
              choices = {
                @TemplateProperty.DropdownPropertyChoice(
                    value = "completions",
                    label = "Chat Completions"),
                @TemplateProperty.DropdownPropertyChoice(value = "responses", label = "Responses")
              })
          OpenAiApiFamily apiFamily,
      @Valid @NotNull OpenAiBackend backend,
      @Valid @NotNull OpenAiModel model,
      @TemplateProperty(
              group = "provider",
              label = "Enable web search",
              tooltip = "Enable the OpenAI web_search server tool (Responses API only).",
              type = TemplateProperty.PropertyType.Boolean,
              optional = true,
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "configuration.openai.apiFamily",
                      equals = "responses"))
          @Nullable Boolean enableWebSearch,
      @TemplateProperty(
              group = "provider",
              label = "Enable code interpreter",
              tooltip = "Enable the OpenAI code_interpreter server tool (Responses API only).",
              type = TemplateProperty.PropertyType.Boolean,
              optional = true,
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "configuration.openai.apiFamily",
                      equals = "responses"))
          @Nullable Boolean enableCodeInterpreter,
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
          @Nullable ModelCapabilitiesOverride capabilityOverride) {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = OpenAiBackend.OpenAiDirectBackend.class, name = "direct"),
    @JsonSubTypes.Type(value = OpenAiBackend.OpenAiCompatibleBackend.class, name = "compatible")
  })
  @TemplateDiscriminatorProperty(
      label = "Backend",
      group = "provider",
      name = "type",
      defaultValue = "direct",
      description = "Specify how the OpenAI-compatible API is reached.")
  public sealed interface OpenAiBackend {

    /** The backend discriminator string. */
    String type();

    @TemplateSubType(id = "direct", label = "OpenAI (direct)")
    record OpenAiDirectBackend(
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
            @Nullable String projectId)
        implements OpenAiBackend {

      @Override
      public String type() {
        return "direct";
      }

      @Override
      public String toString() {
        return "OpenAiDirectBackend{apiKey=[REDACTED], organizationId=%s, projectId=%s}"
            .formatted(organizationId, projectId);
      }
    }

    @TemplateSubType(id = "compatible", label = "OpenAI Compatible")
    record OpenAiCompatibleBackend(
        @NotBlank
            @HttpUrl
            @TemplateProperty(
                group = "provider",
                label = "API endpoint",
                tooltip = "Specify an endpoint to use the connector with an OpenAI compatible API.",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
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
        @Valid @NotNull CompatibleAuthentication authentication)
        implements OpenAiBackend {

      @Override
      public String type() {
        return "compatible";
      }
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(
        value = CompatibleAuthentication.CompatibleNoAuthentication.class,
        name = "none"),
    @JsonSubTypes.Type(
        value = CompatibleAuthentication.CompatibleApiKeyAuthentication.class,
        name = "apiKey")
  })
  @TemplateDiscriminatorProperty(
      label = "Authentication",
      group = "provider",
      name = "type",
      defaultValue = "none",
      description =
          "Authentication for the OpenAI-compatible gateway. Extensible: more schemes can be added later without breaking existing configs.")
  public sealed interface CompatibleAuthentication {

    @TemplateSubType(id = "none", label = "None")
    record CompatibleNoAuthentication() implements CompatibleAuthentication {}

    @TemplateSubType(id = "apiKey", label = "API key")
    record CompatibleApiKeyAuthentication(
        @NotBlank
            @TemplateProperty(
                group = "provider",
                label = "API key",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            String apiKey)
        implements CompatibleAuthentication {

      @Override
      public String toString() {
        return "CompatibleApiKeyAuthentication{apiKey=[REDACTED]}";
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
              defaultValue = "gpt-5.4",
              defaultValueType = TemplateProperty.DefaultValueType.String,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String model,
      @Valid @Nullable OpenAiModelParameters parameters) {

    public record OpenAiModelParameters(
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "Maximum completion tokens",
                tooltip =
                    "The maximum number of tokens per request to generate before stopping. <br><br>Details in the <a href=\"https://platform.openai.com/docs/api-reference/chat/create#chat-create-max_completion_tokens\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            @Nullable Integer maxCompletionTokens,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "Temperature",
                tooltip =
                    "Floating point number between 0 and 2. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://platform.openai.com/docs/api-reference/chat/create#chat-create-temperature\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            @Nullable Double temperature,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "top P",
                tooltip =
                    "Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://platform.openai.com/docs/api-reference/chat/create#chat-create-top_p\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            @Nullable Double topP,
        @TemplateProperty(
                group = "model",
                label = "Reasoning effort",
                tooltip =
                    "Reasoning effort for reasoning-capable models. " + "Unset ⇒ model default.",
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
            @Nullable OpenAiEffort effort) {}
  }
}
