/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.HttpUrl;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.util.ConnectorUtils;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@TemplateSubType(id = BedrockChatModelConfiguration.BEDROCK_ID, label = "Amazon Bedrock")
public record BedrockChatModelConfiguration(@Valid @NotNull BedrockConnection bedrock)
    implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String BEDROCK_ID = "bedrock";

  @Override
  public String provider() {
    return BEDROCK_ID;
  }

  @Override
  public String model() {
    return bedrock.model().model();
  }

  /** All Amazon Bedrock-specific configuration, nested under the {@code bedrock} wire key. */
  public record BedrockConnection(
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "AWS region",
              description = "Specify the AWS region (example: <code>eu-west-1</code>).",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String region,
      @HttpUrl
          @TemplateProperty(
              group = "provider",
              label = "Custom endpoint",
              description =
                  "Custom API endpoint for VPC/PrivateLink configurations or other non-standard "
                      + "deployments. Overrides the default Bedrock Runtime endpoint for the "
                      + "specified region.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              optional = true)
          @Nullable String endpoint,
      @Valid @NotNull AwsAuthentication authentication,
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
      @Valid @Nullable TimeoutConfiguration timeouts,
      @Valid @NotNull BedrockModel model) {

    @JsonIgnore
    @AssertFalse(message = "AWS default credentials chain is not supported on SaaS")
    public boolean isDefaultCredentialsChainUsedInSaaS() {
      return ConnectorUtils.isSaaS()
          && authentication instanceof AwsAuthentication.AwsDefaultCredentialsChainAuthentication;
    }

    @Override
    public String toString() {
      return "BedrockConnection{region="
          + region
          + ", endpoint="
          + endpoint
          + ", authentication="
          + authentication
          + ", headers=[REDACTED], queryParameters=[REDACTED], timeouts="
          + timeouts
          + ", model="
          + model
          + "}";
    }
  }

  public record BedrockModel(
      @NotBlank
          @TemplateProperty(
              group = "model",
              label = "Model",
              description =
                  "Specify the model ID. Details in the <a href=\"https://docs.aws.amazon.com/bedrock/latest/userguide/inference-profiles-support.html\" target=\"_blank\">documentation</a>.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              defaultValue = "",
              defaultValueType = TemplateProperty.DefaultValueType.String,
              placeholder = "us.amazon.nova-2-lite-v1:0",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String model,
      @Valid @Nullable BedrockModelParameters parameters) {

    public record BedrockModelParameters(
        @Valid @Nullable BedrockPromptCaching promptCaching,
        @Min(1)
            @TemplateProperty(
                group = "model-options",
                label = "Maximum tokens",
                tooltip =
                    "The maximum number of tokens per request to generate before stopping. Leave "
                        + "unset to use the model default.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            @Nullable Integer maxTokens,
        @DecimalMin("0.0")
            @TemplateProperty(
                group = "model-options",
                label = "Temperature",
                tooltip =
                    "Floating point number. The higher the number, the more randomness will be "
                        + "injected into the response. Supported ranges vary by model.",
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
                    "Floating point number between 0 and 1. Recommended for advanced use cases "
                        + "only (you usually only need to use temperature).",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            @Nullable Double topP,
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
        return "BedrockModelParameters{promptCaching="
            + promptCaching
            + ", maxTokens="
            + maxTokens
            + ", temperature="
            + temperature
            + ", topP="
            + topP
            + ", requestParameters=[REDACTED]}";
      }
    }

    /**
     * Amazon Bedrock automatic prompt caching. A record rather than a bare boolean so it stays
     * extensible: a TTL field could be added later without changing this property's wire shape.
     */
    public record BedrockPromptCaching(
        @TemplateProperty(
                group = "model",
                label = "Enable prompt caching",
                tooltip =
                    "Enables Amazon Bedrock automatic prompt caching. See the <a "
                        + "href=\"https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html\" "
                        + "target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Boolean,
                defaultValue = "false",
                defaultValueType = TemplateProperty.DefaultValueType.Boolean,
                optional = true)
            @Nullable Boolean enabled) {}
  }
}
