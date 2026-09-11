/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AgenticAiCredentialConfigurations.BedrockApiKeyCredential;
import io.camunda.connector.aws.model.impl.AwsCredentialConfiguration;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/**
 * Bedrock's two credential families. Legacy {@link AwsAuthentication} variants remain accepted for
 * already-deployed jobs but are excluded from the v2 template.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AwsIamAuthentication.class, name = "awsIam"),
  @JsonSubTypes.Type(value = BedrockApiKeyAuthentication.class, name = "bedrockApiKey"),
  @JsonSubTypes.Type(
      value = AwsAuthentication.AwsStaticCredentialsAuthentication.class,
      name = "credentials"),
  @JsonSubTypes.Type(value = AwsAuthentication.AwsApiKeyAuthentication.class, name = "apiKey"),
  @JsonSubTypes.Type(
      value = AwsAuthentication.AwsDefaultCredentialsChainAuthentication.class,
      name = "defaultCredentialsChain"),
  @JsonSubTypes.Type(
      value = AwsAuthentication.AwsCredentialConfigurationAuthentication.class,
      name = "awsCredential"),
  @JsonSubTypes.Type(
      value = AwsAuthentication.BedrockApiKeyCredentialAuthentication.class,
      name = "bedrockApiKeyCredential")
})
@TemplateDiscriminatorProperty(
    label = "Authentication family",
    group = "provider",
    name = "type",
    defaultValue = "awsIam",
    description = "Choose AWS IAM credentials or an Amazon Bedrock API key.")
public sealed interface BedrockAuthentication
    permits AwsAuthentication.AwsStaticCredentialsAuthentication,
        AwsAuthentication.AwsApiKeyAuthentication,
        AwsAuthentication.AwsDefaultCredentialsChainAuthentication,
        AwsAuthentication.AwsCredentialConfigurationAuthentication,
        AwsAuthentication.BedrockApiKeyCredentialAuthentication,
        AwsIamAuthentication,
        BedrockApiKeyAuthentication {

  default @Nullable AwsCredentialConfiguration awsCredentialConfiguration() {
    return this instanceof AwsAuthentication.AwsCredentialConfigurationAuthentication credential
        ? credential.awsCredential()
        : null;
  }

  default @Nullable AwsAuthentication effectiveIamAuthentication() {
    return this instanceof AwsAuthentication authentication
            && !(authentication instanceof AwsAuthentication.AwsApiKeyAuthentication)
            && !(authentication instanceof AwsAuthentication.BedrockApiKeyCredentialAuthentication)
        ? authentication
        : null;
  }

  default @Nullable String effectiveApiKey() {
    return switch (this) {
      case AwsAuthentication.AwsApiKeyAuthentication authentication -> authentication.apiKey();
      case AwsAuthentication.BedrockApiKeyCredentialAuthentication authentication ->
          authentication.bedrockApiKeyCredential().apiKey();
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
    return effectiveIamAuthentication()
        instanceof AwsAuthentication.AwsDefaultCredentialsChainAuthentication;
  }
}

@TemplateSubType(id = "awsIam", label = "AWS IAM")
record AwsIamAuthentication(
    @TemplateProperty(
            group = "provider",
            label = "AWS credential",
            type = PropertyType.Configuration,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            binding = @TemplateProperty.PropertyBinding(name = "awsCredential"),
            tooltip = "Choose a reusable AWS credential.")
        @Valid
        @Nullable AwsCredentialConfiguration awsCredential,
    @TemplateProperty(type = PropertyType.Hidden, ignore = true)
        @Nullable AwsIamInlineAuthentication inlineAuthentication)
    implements BedrockAuthentication {

  @JsonIgnore
  @AssertTrue(
      message = "AWS IAM authentication is required from the credential or element template")
  public boolean isAuthenticationPresent() {
    return awsCredential != null
        ? awsCredential.authentication() != null
        : inlineAuthentication != null;
  }

  @JsonIgnore
  @Valid
  public @Nullable AwsIamInlineAuthentication getInlineAuthenticationForValidation() {
    return awsCredential == null ? inlineAuthentication : null;
  }

  @Override
  public @Nullable AwsCredentialConfiguration awsCredentialConfiguration() {
    return awsCredential;
  }

  @Override
  public @Nullable AwsAuthentication effectiveIamAuthentication() {
    return switch (inlineAuthentication) {
      case AwsIamInlineAuthentication.StaticCredentials credentials ->
          new AwsAuthentication.AwsStaticCredentialsAuthentication(
              credentials.accessKey(), credentials.secretKey());
      case AwsIamInlineAuthentication.DefaultCredentialsChain ignored ->
          new AwsAuthentication.AwsDefaultCredentialsChainAuthentication();
      case null -> null;
    };
  }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = AwsIamInlineAuthentication.StaticCredentials.class,
      name = "credentials"),
  @JsonSubTypes.Type(
      value = AwsIamInlineAuthentication.DefaultCredentialsChain.class,
      name = "defaultCredentialsChain")
})
@TemplateDiscriminatorProperty(
    label = "AWS IAM authentication",
    group = "provider",
    name = "type",
    defaultValue = "credentials",
    description = "Specify the one-time AWS IAM authentication strategy.")
sealed interface AwsIamInlineAuthentication {

  @TemplateSubType(id = "credentials", label = "Credentials")
  record StaticCredentials(
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "Access key",
              type = PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String accessKey,
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "Secret key",
              type = PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String secretKey)
      implements AwsIamInlineAuthentication {}

  @TemplateSubType(
      id = "defaultCredentialsChain",
      label = "Default Credentials Chain (Hybrid/Self-Managed only)")
  record DefaultCredentialsChain() implements AwsIamInlineAuthentication {}
}

@TemplateSubType(id = "bedrockApiKey", label = "Amazon Bedrock API key")
record BedrockApiKeyAuthentication(
    @TemplateProperty(
            group = "provider",
            label = "Amazon Bedrock API key credential",
            type = PropertyType.Configuration,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            binding = @TemplateProperty.PropertyBinding(name = "bedrockApiKeyCredential"),
            tooltip = "Choose a reusable Amazon Bedrock API key credential.")
        @Valid
        @Nullable BedrockApiKeyCredential bedrockApiKeyCredential,
    @TemplateProperty(ignore = true) @Nullable String apiKey)
    implements BedrockAuthentication {

  @JsonIgnore
  @AssertTrue(
      message = "An Amazon Bedrock API key is required from the credential or element template")
  public boolean isApiKeyPresent() {
    String effectiveApiKey =
        bedrockApiKeyCredential != null ? bedrockApiKeyCredential.apiKey() : apiKey;
    return effectiveApiKey != null && !effectiveApiKey.isBlank();
  }

  public @Nullable String apiKey() {
    return bedrockApiKeyCredential != null ? bedrockApiKeyCredential.apiKey() : apiKey;
  }

  @Override
  public @Nullable String effectiveApiKey() {
    return apiKey();
  }
}
