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
import com.anthropic.core.http.ProxyAuthenticator;
import com.anthropic.foundry.backends.FoundryBackend;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.EndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicConfiguration.Backend;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.aws.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.FoundryAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.FoundryCredentialResolver;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.common.http.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.authentication.OAuthClientCredentialsTokenResolver;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

public class AnthropicChatModelFactory implements ChatModelFactory {

  private final AgenticAiHttpProxySupport httpProxySupport;
  private final AnthropicMessageRequestConverter requestConverter;
  private final AnthropicMessageResponseConverter responseConverter;
  private final OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver;
  private final FoundryCredentialResolver foundryCredentialResolver;

  public AnthropicChatModelFactory(
      AgenticAiHttpProxySupport httpProxySupport,
      AnthropicMessageRequestConverter requestConverter,
      AnthropicMessageResponseConverter responseConverter,
      OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver,
      FoundryCredentialResolver foundryCredentialResolver) {
    this.httpProxySupport = httpProxySupport;
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
    this.oAuthClientCredentialsTokenResolver = oAuthClientCredentialsTokenResolver;
    this.foundryCredentialResolver = foundryCredentialResolver;
  }

  @Override
  public boolean supports(ChatModelConfiguration configuration) {
    return configuration instanceof AnthropicConfiguration;
  }

  @Override
  public ChatModel create(ChatModelConfiguration configuration) {
    final var model = (AnthropicConfiguration) configuration;
    final var client =
        buildClient(
            model.backend(),
            model.timeout(),
            httpProxySupport,
            oAuthClientCredentialsTokenResolver,
            foundryCredentialResolver);
    return new AnthropicChatModel(client, model, requestConverter, responseConverter);
  }

  private static AnthropicClient buildClient(
      Backend backend,
      Duration timeout,
      AgenticAiHttpProxySupport httpProxySupport,
      OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver,
      FoundryCredentialResolver foundryCredentialResolver) {
    final var builder = AnthropicOkHttpClient.builder();

    switch (backend) {
      case Backend.Api api -> applyApiBackend(builder, api);
      case Backend.AwsBedrockMantle awsBedrockMantle ->
          applyAwsBedrockMantleBackend(builder, awsBedrockMantle);
      case Backend.Foundry foundry ->
          applyFoundryBackend(builder, foundry, foundryCredentialResolver, timeout);
      case Backend.Custom custom ->
          applyCustomBackend(builder, custom, oAuthClientCredentialsTokenResolver);
    }

    builder.timeout(timeout);

    final String scheme =
        backend.endpoint() != null ? URI.create(backend.endpoint()).getScheme() : null;
    httpProxySupport
        .okHttpProxy(scheme != null ? scheme : ProxyConfiguration.SCHEME_HTTPS)
        .ifPresent(
            p -> {
              builder.proxy(p.proxy());
              if (p.hasCredentials()) {
                builder.proxyAuthenticator(ProxyAuthenticator.basic(p.username(), p.password()));
              }
            });
    return builder.build();
  }

  private static void applyApiBackend(AnthropicOkHttpClient.Builder builder, Backend.Api api) {
    builder.apiKey(api.apiKey());

    if (api.endpoint() != null) {
      builder.baseUrl(api.endpoint());
    }
  }

  private static void applyCustomBackend(
      AnthropicOkHttpClient.Builder builder,
      Backend.Custom custom,
      OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver) {
    builder.baseUrl(custom.endpoint());

    switch (custom.authentication()) {
      case EndpointAuthentication.ApiKey apiKey -> builder.apiKey(apiKey.apiKey());
      case EndpointAuthentication.None ignored -> {}
      case EndpointAuthentication.OAuthClientCredentials oauth ->
          builder.addInterceptor(
              OAuthBearerTokenInterceptor.create(
                  oAuthClientCredentialsTokenResolver,
                  oauth.tokenEndpoint(),
                  oauth.clientId(),
                  oauth.clientSecret(),
                  oauth.audience(),
                  oauth.clientAuthentication().oauthConstant(),
                  oauth.scopes()));
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
  private static void applyFoundryBackend(
      AnthropicOkHttpClient.Builder builder,
      Backend.Foundry foundry,
      FoundryCredentialResolver foundryCredentialResolver,
      Duration timeout) {
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
