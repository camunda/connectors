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
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.authentication.oauth.OAuthBearerTokenInterceptor;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.FoundryCredentialResolver;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicAwsBedrockMantleBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicFoundryBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicCustomEndpointAuthentication.ApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicCustomEndpointAuthentication.NoAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.FoundryAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OAuthClientCredentialsAuthentication;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.authentication.OAuthClientCredentialsTokenResolver;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
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
    return configuration instanceof AnthropicChatModelConfiguration;
  }

  @Override
  public ChatModel create(ChatModelConfiguration configuration) {
    final var model = (AnthropicChatModelConfiguration) configuration;
    final var connection = model.anthropic();
    final var timeout = connection.timeouts() != null ? connection.timeouts().timeout() : null;

    final var client =
        buildClient(
            connection.backend(),
            timeout,
            httpProxySupport,
            oAuthClientCredentialsTokenResolver,
            foundryCredentialResolver);
    return new AnthropicChatModel(client, model, requestConverter, responseConverter);
  }

  private static AnthropicClient buildClient(
      AnthropicBackend backend,
      @Nullable Duration timeout,
      AgenticAiHttpProxySupport httpProxySupport,
      OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver,
      FoundryCredentialResolver foundryCredentialResolver) {
    final var builder = AnthropicOkHttpClient.builder();

    switch (backend) {
      case AnthropicApiBackend apiBackend -> applyApiBackend(builder, apiBackend);
      case AnthropicAwsBedrockMantleBackend awsBedrockMantleBackend ->
          applyAwsBedrockMantleBackend(builder, awsBedrockMantleBackend);
      case AnthropicFoundryBackend foundryBackend ->
          applyFoundryBackend(builder, foundryBackend, foundryCredentialResolver);
      case AnthropicCustomBackend custom ->
          applyCustomBackend(builder, custom, oAuthClientCredentialsTokenResolver);
    }

    if (timeout != null) {
      builder.timeout(timeout);
    }

    final String scheme =
        configuredEndpoint(backend).map(endpoint -> URI.create(endpoint).getScheme()).orElse(null);
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

  private static void applyApiBackend(
      AnthropicOkHttpClient.Builder builder, AnthropicApiBackend apiBackend) {
    builder.apiKey(apiBackend.anthropic().apiKey());

    if (apiBackend.anthropic().endpoint() != null) {
      builder.baseUrl(apiBackend.anthropic().endpoint());
    }
  }

  private static void applyCustomBackend(
      AnthropicOkHttpClient.Builder builder,
      AnthropicCustomBackend custom,
      OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver) {
    builder.baseUrl(custom.custom().endpoint());

    switch (custom.custom().authentication()) {
      case ApiKeyAuthentication apiKeyAuth -> builder.apiKey(apiKeyAuth.apiKey());
      case NoAuthentication ignored -> {}
      case OAuthClientCredentialsAuthentication oauth ->
          builder.addInterceptor(
              OAuthBearerTokenInterceptor.create(
                  oAuthClientCredentialsTokenResolver,
                  oauth.oauthTokenEndpoint(),
                  oauth.clientId(),
                  oauth.clientSecret(),
                  oauth.audience(),
                  oauth.clientAuthentication().oauthConstant(),
                  oauth.scopes()));
    }
  }

  private static void applyAwsBedrockMantleBackend(
      AnthropicOkHttpClient.Builder builder,
      AnthropicAwsBedrockMantleBackend awsBedrockMantleBackend) {
    final var awsBedrockMantle = awsBedrockMantleBackend.awsBedrockMantle();
    final var backendBuilder =
        BedrockMantleBackend.builder().region(Region.of(awsBedrockMantle.region()));

    if (awsBedrockMantle.endpoint() != null) {
      // passed through verbatim: BedrockMantleBackend.baseUrl() otherwise defaults to
      // https://bedrock-mantle.<region>.api.aws/anthropic, so an override must include the
      // /anthropic path segment itself (documented on the endpoint field).
      backendBuilder.baseUrl(awsBedrockMantle.endpoint());
    }

    switch (awsBedrockMantle.authentication()) {
      case AwsAuthentication.AwsStaticCredentialsAuthentication staticAuth ->
          backendBuilder
              .awsAccessKey(staticAuth.accessKey())
              .awsSecretAccessKey(staticAuth.secretKey());
      case AwsAuthentication.AwsDefaultCredentialsChainAuthentication ignored ->
          backendBuilder.awsCredentialsProvider(DefaultCredentialsProvider.builder().build());
      case AwsAuthentication.AwsApiKeyAuthentication apiKeyAuth ->
          backendBuilder.apiKey(apiKeyAuth.apiKey());
    }

    builder.backend(backendBuilder.build());
  }

  /**
   * Delegates to the Anthropic SDK's own {@link FoundryBackend}, which owns base-URL normalization
   * (appending {@code /anthropic} if missing) and per-request authorization. Entra ID token
   * acquisition is delegated to {@link FoundryCredentialResolver}; this method never sees a raw
   * token or credential.
   */
  private static void applyFoundryBackend(
      AnthropicOkHttpClient.Builder builder,
      AnthropicFoundryBackend foundryBackend,
      FoundryCredentialResolver foundryCredentialResolver) {
    final var foundry = foundryBackend.foundry();
    final var backendBuilder = FoundryBackend.builder().baseUrl(foundry.endpoint());

    switch (foundry.authentication()) {
      case FoundryAuthentication.ApiKeyAuthentication apiKeyAuth ->
          backendBuilder.apiKey(apiKeyAuth.apiKey());
      case FoundryAuthentication.ClientCredentialsAuthentication clientCredentials ->
          backendBuilder.bearerTokenSupplier(
              foundryCredentialResolver.bearerTokenSupplier(clientCredentials));
      case FoundryAuthentication.ManagedIdentityAuthentication managedIdentity ->
          backendBuilder.bearerTokenSupplier(
              foundryCredentialResolver.bearerTokenSupplier(managedIdentity));
    }

    builder.backend(backendBuilder.build());
  }

  /**
   * The base URL actually configured for this backend, if any: {@code custom} and {@code foundry}
   * always set one, {@code aws-bedrock-mantle}'s is optional, and {@code anthropic-api}'s hidden
   * override is usually unset (the SDK then defaults to the production Anthropic API).
   */
  private static Optional<String> configuredEndpoint(AnthropicBackend backend) {
    return switch (backend) {
      case AnthropicApiBackend apiBackend -> Optional.ofNullable(apiBackend.anthropic().endpoint());
      case AnthropicAwsBedrockMantleBackend awsBedrockMantleBackend ->
          Optional.ofNullable(awsBedrockMantleBackend.awsBedrockMantle().endpoint());
      case AnthropicFoundryBackend foundryBackend ->
          Optional.of(foundryBackend.foundry().endpoint());
      case AnthropicCustomBackend custom -> Optional.of(custom.custom().endpoint());
    };
  }
}
