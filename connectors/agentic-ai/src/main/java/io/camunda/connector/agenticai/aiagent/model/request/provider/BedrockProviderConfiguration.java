/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import static io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.BEDROCK_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.util.ConnectorUtils;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = BEDROCK_ID, label = "AWS Bedrock")
public record BedrockProviderConfiguration(@Valid @NotNull BedrockConnection bedrock)
    implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String BEDROCK_ID = "bedrock";

  public record BedrockConnection(
      @NotBlank
          @TemplateProperty(
              group = "provider",
              description = "Specify the AWS region (example: <code>eu-west-1</code>)",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String region,
      @FEEL
          @TemplateProperty(
              group = "provider",
              description = "Optional custom API endpoint",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              optional = true)
          String endpoint,
      @Valid @NotNull AwsAuthentication authentication,
      @Valid TimeoutConfiguration timeouts,
      @Valid @NotNull BedrockModel model) {

    @AssertFalse(message = "AWS default credentials chain is not supported on SaaS")
    @SuppressWarnings("unused")
    public boolean isDefaultCredentialsChainUsedInSaaS() {
      return ConnectorUtils.isSaaS()
          && authentication instanceof AwsAuthentication.AwsDefaultCredentialsChainAuthentication;
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(
        value = AwsAuthentication.AwsStaticCredentialsAuthentication.class,
        name = "credentials"),
    @JsonSubTypes.Type(
        value = AwsAuthentication.AwsDefaultCredentialsChainAuthentication.class,
        name = "defaultCredentialsChain"),
    @JsonSubTypes.Type(value = AwsAuthentication.AwsApiKeyAuthentication.class, name = "apiKey"),
  })
  @TemplateDiscriminatorProperty(
      label = "Authentication",
      group = "provider",
      name = "type",
      defaultValue = "credentials",
      description =
          "Specify the AWS authentication strategy. Learn more at the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-bedrock/#authentication\" target=\"_blank\">documentation page</a>")
  public sealed interface AwsAuthentication {

    @TemplateSubType(id = "credentials", label = "Credentials")
    record AwsStaticCredentialsAuthentication(
        @TemplateProperty(
                group = "provider",
                label = "Access key",
                description =
                    "Provide an IAM access key tailored to a user, equipped with the necessary permissions")
            @NotBlank
            String accessKey,
        @TemplateProperty(
                group = "provider",
                label = "Secret key",
                description =
                    "Provide a secret key of a user with permissions to invoke specified AWS Lambda function")
            @NotBlank
            String secretKey)
        implements AwsAuthentication {

      @Override
      public String toString() {
        return "AwsStaticCredentialsAuthentication{accessKey=[REDACTED], secretKey=[REDACTED]}";
      }
    }

    @TemplateSubType(id = "apiKey", label = "API Key")
    record AwsApiKeyAuthentication(
        @TemplateProperty(
                group = "provider",
                label = "API Key",
                description =
                    "Provide an API Key with permissions to interact with your AWS Bedrock Instance")
            @NotBlank
            String apiKey)
        implements AwsAuthentication {

      @Override
      public String toString() {
        return "AwsApiKeyAuthentication{apiKey=[REDACTED]}";
      }
    }

    @TemplateSubType(
        id = "defaultCredentialsChain",
        label = "Default Credentials Chain (Hybrid/Self-Managed only)")
    record AwsDefaultCredentialsChainAuthentication() implements AwsAuthentication {}
  }

  public record BedrockModel(
      @NotBlank
          @TemplateProperty(
              group = "model",
              label = "Model",
              description =
                  "Specify the model ID. Details in the <a href=\"https://docs.aws.amazon.com/bedrock/latest/userguide/model-ids.html\" target=\"_blank\">documentation</a>.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              defaultValue = "anthropic.claude-3-5-sonnet-20240620-v1:0",
              defaultValueType = TemplateProperty.DefaultValueType.String,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String model,
      @Valid BedrockModel.BedrockModelParameters parameters) {

    public record BedrockModelParameters(
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "Maximum tokens",
                tooltip =
                    "The maximum number of tokens per request to allow in the generated response. <br><br>Details in the <a href=\"https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_InferenceConfiguration.html\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Integer maxTokens,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "Temperature",
                tooltip =
                    "Floating point number between 0 and 1. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_InferenceConfiguration.html\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Double temperature,
        @Min(0)
            @TemplateProperty(
                group = "model",
                label = "top P",
                tooltip =
                    "Floating point number between 0 and 1. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_InferenceConfiguration.html\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.Number,
                feel = FeelMode.required,
                optional = true)
            Double topP) {}
  }
}
