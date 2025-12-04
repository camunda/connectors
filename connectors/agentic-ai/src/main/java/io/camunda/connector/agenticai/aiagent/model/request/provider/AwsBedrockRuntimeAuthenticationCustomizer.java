package io.camunda.connector.agenticai.aiagent.model.request.provider;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.token.credentials.SdkTokenProvider;
import software.amazon.awssdk.auth.token.credentials.StaticTokenProvider;
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
    return bedrockBuilder -> bedrockBuilder.credentialsProvider(DefaultCredentialsProvider.builder().build());
  }

  private static AwsBedrockRuntimeAuthenticationCustomizer staticCredentials(
      BedrockProviderConfiguration.AwsAuthentication.StaticCredentialsAuthentication
          staticCredentialsAuthentication) {
    var awsCredentials =
        AwsBasicCredentials.create(
            staticCredentialsAuthentication.accessKey(),
            staticCredentialsAuthentication.secretKey());

    return bedrockBuilder -> bedrockBuilder.credentialsProvider(StaticCredentialsProvider.create(awsCredentials));
  }

  private static AwsBedrockRuntimeAuthenticationCustomizer apiKey(
      BedrockProviderConfiguration.AwsAuthentication.ApiKeyAuthentication apiKeyAuthentication) {
    return bedrockBuilder ->
        bedrockBuilder.tokenProvider(StaticTokenProvider.create(apiKeyAuthentication::apiKey));
  }

  void provideAuthenticationMechanism(BedrockRuntimeClientBuilder runtime);
}
