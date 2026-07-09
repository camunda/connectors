/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

/**
 * AWS authentication strategies for the wire-format-first chat-model config (Anthropic on Bedrock).
 * Deliberately independent of the legacy {@code BedrockProviderConfiguration.AwsAuthentication} to
 * keep the new config package self-contained and decoupled from the retiring {@code provider}
 * package.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = ChatModelAwsAuthentication.AwsStaticCredentialsAuthentication.class,
      name = "credentials"),
  @JsonSubTypes.Type(
      value = ChatModelAwsAuthentication.AwsDefaultCredentialsChainAuthentication.class,
      name = "defaultCredentialsChain"),
  @JsonSubTypes.Type(
      value = ChatModelAwsAuthentication.AwsApiKeyAuthentication.class,
      name = "apiKey")
})
@TemplateDiscriminatorProperty(
    label = "AWS authentication",
    group = "provider",
    name = "type",
    defaultValue = "credentials",
    description =
        "Specify the AWS authentication strategy. Learn more at the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-bedrock/#authentication\" target=\"_blank\">documentation page</a>")
public sealed interface ChatModelAwsAuthentication {

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
              description = "Provide the secret key for the IAM access key")
          @NotBlank
          String secretKey)
      implements ChatModelAwsAuthentication {

    @Override
    public String toString() {
      return "AwsStaticCredentialsAuthentication{accessKey=[REDACTED], secretKey=[REDACTED]}";
    }
  }

  @TemplateSubType(id = "apiKey", label = "API key")
  record AwsApiKeyAuthentication(
      @TemplateProperty(
              group = "provider",
              label = "API key",
              description =
                  "Provide an API key with permissions to interact with your AWS Bedrock instance")
          @NotBlank
          String apiKey)
      implements ChatModelAwsAuthentication {

    @Override
    public String toString() {
      return "AwsApiKeyAuthentication{apiKey=[REDACTED]}";
    }
  }

  @TemplateSubType(
      id = "defaultCredentialsChain",
      label = "Default credentials chain (Hybrid/Self-Managed only)")
  record AwsDefaultCredentialsChainAuthentication() implements ChatModelAwsAuthentication {}
}
