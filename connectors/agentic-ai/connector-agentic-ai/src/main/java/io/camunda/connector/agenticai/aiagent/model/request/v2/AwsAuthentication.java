/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.aws.model.impl.AwsCredentialConfiguration;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AwsIamAuthentication.class, name = "awsIam"),
  @JsonSubTypes.Type(value = BedrockApiKeyAuthentication.class, name = "bedrockApiKey"),
  @JsonSubTypes.Type(
      value = AwsAuthentication.AwsStaticCredentialsAuthentication.class,
      name = "credentials"),
  @JsonSubTypes.Type(value = AwsAuthentication.AwsApiKeyAuthentication.class, name = "apiKey"),
  @JsonSubTypes.Type(
      value = AwsAuthentication.AwsCredentialConfigurationAuthentication.class,
      name = "awsCredential"),
  @JsonSubTypes.Type(
      value = AwsAuthentication.BedrockApiKeyCredentialAuthentication.class,
      name = "bedrockApiKeyCredential"),
  @JsonSubTypes.Type(
      value = AwsAuthentication.AwsDefaultCredentialsChainAuthentication.class,
      name = "defaultCredentialsChain")
})
@TemplateDiscriminatorProperty(
    label = "Authentication family",
    group = "provider",
    name = "type",
    defaultValue = "awsIam",
    description = "Choose AWS IAM credentials or an Amazon Bedrock API key.")
public sealed interface AwsAuthentication
    permits AwsAuthentication.AwsStaticCredentialsAuthentication,
        AwsAuthentication.AwsApiKeyAuthentication,
        AwsAuthentication.AwsCredentialConfigurationAuthentication,
        AwsAuthentication.BedrockApiKeyCredentialAuthentication,
        AwsAuthentication.AwsDefaultCredentialsChainAuthentication,
        AwsIamAuthentication,
        BedrockApiKeyAuthentication {

  default @Nullable AwsCredentialConfiguration awsCredentialConfiguration() {
    return this instanceof AwsCredentialConfigurationAuthentication credential
        ? credential.awsCredential()
        : null;
  }

  default @Nullable AwsAuthentication effectiveIamAuthentication() {
    return switch (this) {
      case AwsStaticCredentialsAuthentication ignored -> this;
      case AwsDefaultCredentialsChainAuthentication ignored -> this;
      case AwsCredentialConfigurationAuthentication ignored -> this;
      default -> null;
    };
  }

  default @Nullable String effectiveApiKey() {
    return switch (this) {
      case AwsApiKeyAuthentication authentication -> authentication.apiKey();
      case BedrockApiKeyCredentialAuthentication authentication ->
          authentication.bedrockApiKeyCredential() != null
              ? authentication.bedrockApiKeyCredential().apiKey()
              : null;
      default -> null;
    };
  }

  default @Nullable String credentialRegion() {
    var credential = awsCredentialConfiguration();
    if (credential != null) {
      return credential.region();
    }
    return switch (this) {
      case BedrockApiKeyCredentialAuthentication authentication ->
          authentication.bedrockApiKeyCredential() != null
              ? authentication.bedrockApiKeyCredential().region()
              : null;
      case BedrockApiKeyAuthentication authentication ->
          authentication.bedrockApiKeyCredential() != null
              ? authentication.bedrockApiKeyCredential().region()
              : null;
      default -> null;
    };
  }

  default boolean usesDefaultCredentialsChain() {
    var credential = awsCredentialConfiguration();
    if (credential != null) {
      return credential.authentication()
          instanceof
          io.camunda.connector.aws.model.impl.AwsAuthentication
              .AwsDefaultCredentialsChainAuthentication;
    }
    return effectiveIamAuthentication() instanceof AwsDefaultCredentialsChainAuthentication;
  }

  @TemplateSubType(id = "credentials", label = "Credentials", ignore = true)
  record AwsStaticCredentialsAuthentication(
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "Access key",
              description = "AWS IAM access key.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String accessKey,
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "Secret key",
              description = "AWS IAM secret key.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String secretKey)
      implements AwsAuthentication {

    @Override
    public String toString() {
      return "AwsStaticCredentialsAuthentication{accessKey=[REDACTED], secretKey=[REDACTED]}";
    }
  }

  @TemplateSubType(id = "apiKey", label = "API key", ignore = true)
  record AwsApiKeyAuthentication(
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "API key",
              description = "Bearer API key for AWS Bedrock.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String apiKey)
      implements AwsAuthentication {

    @Override
    public String toString() {
      return "AwsApiKeyAuthentication{apiKey=[REDACTED]}";
    }
  }

  @TemplateSubType(id = "awsCredential", label = "AWS credential", ignore = true)
  record AwsCredentialConfigurationAuthentication(
      @TemplateProperty(
              group = "provider",
              label = "AWS credential",
              type = TemplateProperty.PropertyType.Configuration,
              feel = FeelMode.optional,
              description = "Choose a reusable AWS credential with an optional default region.")
          @jakarta.validation.Valid
          @NotNull
          AwsCredentialConfiguration awsCredential)
      implements AwsAuthentication {}

  @TemplateSubType(
      id = "bedrockApiKeyCredential",
      label = "Amazon Bedrock API key credential",
      ignore = true)
  record BedrockApiKeyCredentialAuthentication(
      @TemplateProperty(
              group = "provider",
              label = "Amazon Bedrock API key credential",
              type = TemplateProperty.PropertyType.Configuration,
              feel = FeelMode.optional,
              description = "Choose a reusable Amazon Bedrock API key credential.")
          @jakarta.validation.Valid
          @NotNull
          AgenticAiCredentialConfigurations.BedrockApiKeyCredential bedrockApiKeyCredential)
      implements AwsAuthentication {}

  @TemplateSubType(
      id = "defaultCredentialsChain",
      label = "Default Credentials Chain (Hybrid/Self-Managed only)",
      ignore = true)
  record AwsDefaultCredentialsChainAuthentication() implements AwsAuthentication {}
}
