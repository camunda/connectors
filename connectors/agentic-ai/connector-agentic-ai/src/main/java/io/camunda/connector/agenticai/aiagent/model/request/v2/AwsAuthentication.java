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
import io.camunda.connector.aws.model.impl.AwsCredentialConfiguration;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/**
 * Two authentication families: AWS IAM (a reusable AWS credential, matching the one other AWS
 * connectors already use, or static keys/default-chain entered inline) and AWS Bedrock's own bearer
 * API key. Kept as two separate top-level variants rather than one flat list of options - {@link
 * AwsCredentialConfiguration} already carries its own static-keys-vs-default-chain choice, so
 * nesting that choice inside the IAM family avoids the same choice existing at two levels at once.
 * See "AWS credentials" in native-providers.md.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AwsAuthentication.AwsIamAuthentication.class, name = "awsIam"),
  @JsonSubTypes.Type(value = AwsAuthentication.AwsApiKeyAuthentication.class, name = "apiKey")
})
@TemplateDiscriminatorProperty(
    label = "Authentication",
    group = "provider",
    name = "type",
    defaultValue = "awsIam",
    description = "Choose AWS IAM credentials or an AWS Bedrock API key.")
public sealed interface AwsAuthentication {

  @TemplateSubType(id = "awsIam", label = "AWS IAM")
  record AwsIamAuthentication(
      @Valid
          @TemplateProperty(
              group = "provider",
              label = "AWS credential",
              type = TemplateProperty.PropertyType.Configuration,
              optional = true,
              binding = @TemplateProperty.PropertyBinding(name = "awsCredential"),
              description = "Select a saved AWS credential, or enter authentication details below.")
          @Nullable AwsCredentialConfiguration awsCredential,
      @NestedProperties(
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "awsCredential",
                      isEmpty = TemplateProperty.NullableBoolean.TRUE))
          @Nullable AwsIamAuthenticationMethod method)
      implements AwsAuthentication {

    /** The one-time {@link #method}, but only when no credential is bound. */
    @JsonIgnore
    @Valid
    public @Nullable AwsIamAuthenticationMethod getMethodWhenNoCredentialBound() {
      return awsCredential != null ? null : method;
    }

    @JsonIgnore
    @AssertTrue(
        message = "AWS IAM authentication is required from the credential or element template")
    public boolean isAuthenticationPresent() {
      return awsCredential != null || method != null;
    }

    @JsonIgnore
    public boolean usesDefaultCredentialsChain() {
      if (awsCredential != null) {
        return awsCredential.authentication()
            instanceof
            io.camunda.connector.aws.model.impl.AwsAuthentication
                .AwsDefaultCredentialsChainAuthentication;
      }
      return method instanceof AwsIamAuthenticationMethod.AwsDefaultCredentialsChainAuthentication;
    }

    @Override
    public String toString() {
      return "AwsIamAuthentication{awsCredential=" + awsCredential + ", method=" + method + "}";
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(
        value = AwsIamAuthenticationMethod.AwsStaticCredentialsAuthentication.class,
        name = "credentials"),
    @JsonSubTypes.Type(
        value = AwsIamAuthenticationMethod.AwsDefaultCredentialsChainAuthentication.class,
        name = "defaultCredentialsChain")
  })
  @TemplateDiscriminatorProperty(
      label = "AWS IAM authentication",
      group = "provider",
      name = "type",
      defaultValue = "credentials",
      description = "Specify the one-time AWS IAM authentication strategy.")
  sealed interface AwsIamAuthenticationMethod {

    @TemplateSubType(id = "credentials", label = "Credentials")
    record AwsStaticCredentialsAuthentication(
        @NotBlank
            @TemplateProperty(
                group = "provider",
                label = "Access key",
                description = "AWS IAM access key.",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
                secret = true,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            String accessKey,
        @NotBlank
            @TemplateProperty(
                group = "provider",
                label = "Secret key",
                description = "AWS IAM secret key.",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
                secret = true,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            String secretKey)
        implements AwsIamAuthenticationMethod {

      @Override
      public String toString() {
        return "AwsStaticCredentialsAuthentication{accessKey=[REDACTED], secretKey=[REDACTED]}";
      }
    }

    @TemplateSubType(
        id = "defaultCredentialsChain",
        label = "Default Credentials Chain (Hybrid/Self-Managed only)")
    record AwsDefaultCredentialsChainAuthentication() implements AwsIamAuthenticationMethod {}
  }

  @TemplateSubType(id = "apiKey", label = "API key")
  record AwsApiKeyAuthentication(
      @Valid
          @TemplateProperty(
              group = "provider",
              label = "Bedrock API key credential",
              type = TemplateProperty.PropertyType.Configuration,
              optional = true,
              binding = @TemplateProperty.PropertyBinding(name = "bedrockApiKeyCredential"),
              description =
                  "Select a saved AWS Bedrock API key credential, or enter an API key below.")
          @Nullable BedrockApiKeyCredential bedrockApiKeyCredential,
      @TemplateProperty(
              group = "provider",
              label = "API key",
              description = "Bearer API key for AWS Bedrock.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              secret = true,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "bedrockApiKeyCredential",
                      isEmpty = TemplateProperty.NullableBoolean.TRUE))
          @Nullable String apiKey)
      implements AwsAuthentication {

    /** The Bedrock API key: from the bound credential if present, else the inline value. */
    @JsonIgnore
    public @Nullable String effectiveApiKey() {
      return bedrockApiKeyCredential != null ? bedrockApiKeyCredential.apiKey() : apiKey;
    }

    @JsonIgnore
    @AssertTrue(
        message = "An AWS Bedrock API key is required from the credential or element template")
    public boolean isApiKeyPresent() {
      String effective = effectiveApiKey();
      return effective != null && !effective.isBlank();
    }

    @Override
    public String toString() {
      return "AwsApiKeyAuthentication{bedrockApiKeyCredential="
          + bedrockApiKeyCredential
          + ", apiKey=[REDACTED]}";
    }
  }
}
