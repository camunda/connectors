/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import java.util.List;
import java.util.Map;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.auth.scheme.NoAuthAuthScheme;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;

/**
 * Functional interface to customize the AWS Bedrock Runtime client.
 *
 * <p>Used to provide authentication mechanism to the BedrockRuntimeClient instance.
 */
@FunctionalInterface
public interface AwsBedrockRuntimeAuthenticationCustomizer {

  static AwsBedrockRuntimeAuthenticationCustomizer createFor(
      BedrockProviderConfiguration.BedrockConnection bedrockConnection) {

    return switch (bedrockConnection.authentication()) {
      case BedrockProviderConfiguration.AwsAuthentication.DefaultCredentialsChainAuthentication
              defaultAuthentication ->
          defaultCredentials();
      case BedrockProviderConfiguration.AwsAuthentication.StaticCredentialsAuthentication
              staticAuthentication ->
          staticCredentials(staticAuthentication);
      case BedrockProviderConfiguration.AwsAuthentication.ApiKeyAuthentication
              apiKeyAuthentication ->
          apiKey(apiKeyAuthentication);
    };
  }

  private static AwsBedrockRuntimeAuthenticationCustomizer defaultCredentials() {
    return bedrockBuilder ->
        bedrockBuilder.credentialsProvider(DefaultCredentialsProvider.builder().build());
  }

  private static AwsBedrockRuntimeAuthenticationCustomizer staticCredentials(
      BedrockProviderConfiguration.AwsAuthentication.StaticCredentialsAuthentication
          staticCredentialsAuthentication) {
    var awsCredentials =
        AwsBasicCredentials.create(
            staticCredentialsAuthentication.accessKey(),
            staticCredentialsAuthentication.secretKey());

    return bedrockBuilder ->
        bedrockBuilder.credentialsProvider(StaticCredentialsProvider.create(awsCredentials));
  }

  private static AwsBedrockRuntimeAuthenticationCustomizer apiKey(
      BedrockProviderConfiguration.AwsAuthentication.ApiKeyAuthentication apiKeyAuthentication) {
    return bedrockBuilder ->
        bedrockBuilder
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .putAuthScheme(NoAuthAuthScheme.create())
            .overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .headers(
                        Map.of("Authorization", List.of("Bearer " + apiKeyAuthentication.apiKey())))
                    .build());
  }

  void provideAuthenticationMechanism(BedrockRuntimeClientBuilder runtime);
}
