/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import com.anthropic.bedrock.backends.BedrockMantleBackend;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.Timeout;
import com.anthropic.core.http.ProxyAuthenticator;
import com.anthropic.foundry.backends.FoundryBackend;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.ConnectionResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.ConnectionSettings;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.EndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.envelope.ModelCallEnvelope;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicConfiguration.Backend;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.aws.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.FoundryAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.FoundryCredentialResolver;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

public class AnthropicChatModelFactory implements ChatModelFactory {

  private static final String DEFAULT_ENDPOINT = "https://api.anthropic.com";

  private final ConnectionResolver connectionResolver;
  private final FoundryCredentialResolver foundryCredentialResolver;
  private final AnthropicMessageRequestConverter requestConverter;
  private final AnthropicMessageResponseConverter responseConverter;

  public AnthropicChatModelFactory(
      ConnectionResolver connectionResolver,
      FoundryCredentialResolver foundryCredentialResolver,
      AnthropicMessageRequestConverter requestConverter,
      AnthropicMessageResponseConverter responseConverter) {
    this.connectionResolver = connectionResolver;
    this.foundryCredentialResolver = foundryCredentialResolver;
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
  }

  @Override
  public boolean supports(ChatModelConfiguration configuration) {
    return configuration instanceof AnthropicConfiguration;
  }

  @Override
  public ChatModel create(ChatModelConfiguration configuration) {
    final var model = (AnthropicConfiguration) configuration;
    final var client = buildClient(model.backend(), model.timeout());
    return new ModelCallEnvelope(
        new AnthropicChatModel(client, model, requestConverter, responseConverter),
        new AnthropicSdkFailureMapper());
  }

  private AnthropicClient buildClient(Backend backend, Duration timeout) {
    final var settings =
        connectionResolver.resolve(timeout, backend.endpoint(), defaultEndpoint(backend));
    final var builder = AnthropicOkHttpClient.builder();

    switch (backend) {
      case Backend.Api api -> applyApiBackend(builder, api);
      case Backend.AwsBedrockMantle awsBedrockMantle ->
          applyAwsBedrockMantleBackend(builder, awsBedrockMantle);
      case Backend.Foundry foundry -> applyFoundryBackend(builder, foundry, timeout);
      case Backend.Custom custom -> applyCustomBackend(builder, custom);
    }

    applyConnectionSettings(builder, settings);
    return builder.build();
  }

  /** The endpoint which the SDK connects to when the configuration does not set one. */
  private static String defaultEndpoint(Backend backend) {
    return switch (backend) {
      case Backend.AwsBedrockMantle mantle ->
          "https://bedrock-mantle.%s.api.aws/anthropic".formatted(mantle.region());
      default -> DEFAULT_ENDPOINT;
    };
  }

  private static void applyConnectionSettings(
      AnthropicOkHttpClient.Builder builder, ConnectionSettings settings) {
    builder.timeout(
        Timeout.builder()
            .connect(settings.connectTimeout())
            .read(settings.timeout())
            .write(settings.timeout())
            .request(settings.timeout())
            .build());
    settings
        .proxy()
        .ifPresent(
            p -> {
              builder.proxy(p.proxy());
              if (p.hasCredentials()) {
                builder.proxyAuthenticator(ProxyAuthenticator.basic(p.username(), p.password()));
              }
            });
  }

  private static void applyApiBackend(AnthropicOkHttpClient.Builder builder, Backend.Api api) {
    builder.apiKey(api.apiKey());

    if (api.endpoint() != null) {
      builder.baseUrl(api.endpoint());
    }
  }

  private void applyCustomBackend(AnthropicOkHttpClient.Builder builder, Backend.Custom custom) {
    builder.baseUrl(custom.endpoint());

    switch (custom.authentication()) {
      case EndpointAuthentication.ApiKey apiKey -> builder.apiKey(apiKey.apiKey());
      case EndpointAuthentication.None ignored -> {}
      case EndpointAuthentication.OAuthClientCredentials oauth ->
          builder.addInterceptor(
              OAuthBearerTokenInterceptor.create(connectionResolver.bearerToken(oauth)));
    }
  }

  private static void applyAwsBedrockMantleBackend(
      AnthropicOkHttpClient.Builder builder, Backend.AwsBedrockMantle awsBedrockMantle) {
    final var backendBuilder =
        BedrockMantleBackend.builder().region(Region.of(awsBedrockMantle.region()));

    if (awsBedrockMantle.endpoint() != null) {
      // passed through verbatim: BedrockMantleBackend.baseUrl() otherwise defaults to
      // https://bedrock-mantle.<region>.api.aws/anthropic, so an override must include the
      // /anthropic path segment itself (documented on the endpoint field).
      backendBuilder.baseUrl(awsBedrockMantle.endpoint());
    }

    switch (awsBedrockMantle.authentication()) {
      case AwsAuthentication.StaticCredentials credentials ->
          backendBuilder
              .awsAccessKey(credentials.accessKey())
              .awsSecretAccessKey(credentials.secretKey());
      case AwsAuthentication.DefaultCredentialsChain ignored ->
          backendBuilder.awsCredentialsProvider(DefaultCredentialsProvider.builder().build());
      case AwsAuthentication.ApiKey apiKey -> backendBuilder.apiKey(apiKey.apiKey());
    }

    builder.backend(backendBuilder.build());
  }

  /**
   * Delegates to the Anthropic SDK's own {@link FoundryBackend}. The SDK owns base-URL
   * normalization (appending {@code /anthropic} if missing) and per-request authorization.
   */
  private void applyFoundryBackend(
      AnthropicOkHttpClient.Builder builder, Backend.Foundry foundry, Duration timeout) {
    final var backendBuilder = FoundryBackend.builder().baseUrl(foundry.endpoint());

    switch (foundry.authentication()) {
      case FoundryAuthentication.ApiKey apiKey -> backendBuilder.apiKey(apiKey.apiKey());
      case FoundryAuthentication.ClientCredentials clientCredentials ->
          backendBuilder.bearerTokenSupplier(
              foundryCredentialResolver.bearerTokenSupplier(clientCredentials, timeout));
      case FoundryAuthentication.ManagedIdentity managedIdentity ->
          backendBuilder.bearerTokenSupplier(
              foundryCredentialResolver.bearerTokenSupplier(managedIdentity, timeout));
    }

    builder.backend(backendBuilder.build());
  }
}
