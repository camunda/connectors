/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.ChatModelProviderSupport.CONNECT_TIMEOUT;
import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.ChatModelProviderSupport.deriveTimeoutSetting;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseConnection;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.token.credentials.StaticTokenProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClientBuilder;
import software.amazon.awssdk.services.bedrockruntime.auth.scheme.BedrockRuntimeAuthSchemeProvider;

/**
 * Builds the AWS SDK async client backing the native Bedrock Converse provider and wraps it, along
 * with the request/response converters, in a {@link BedrockConverseChatModel}.
 */
public class BedrockConverseChatModelFactory implements ChatModelFactory {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BedrockConverseChatModelFactory.class);

  private static final String SIGV4_AUTH_SCHEME = "sigv4";
  private static final String BEARER_AUTH_SCHEME = "httpBearerAuth";

  private final ChatModelProperties config;
  private final AgenticAiHttpProxySupport httpProxySupport;
  private final BedrockConverseRequestConverter requestConverter;
  private final BedrockConverseResponseConverter responseConverter;
  private final ObjectMapper objectMapper;

  public BedrockConverseChatModelFactory(
      ChatModelProperties config,
      AgenticAiHttpProxySupport httpProxySupport,
      BedrockConverseRequestConverter requestConverter,
      BedrockConverseResponseConverter responseConverter,
      ObjectMapper objectMapper) {
    this.config = config;
    this.httpProxySupport = httpProxySupport;
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(ChatModelConfiguration configuration) {
    return configuration instanceof BedrockConverseChatModelConfiguration;
  }

  @Override
  public ChatModel create(ChatModelConfiguration configuration) {
    final var model = (BedrockConverseChatModelConfiguration) configuration;
    final var client = buildAsyncClient(model.bedrock());
    return new BedrockConverseChatModel(
        client, model, requestConverter, responseConverter, objectMapper);
  }

  /**
   * Builds the {@link BedrockRuntimeAsyncClient} for the given connection.
   *
   * <p>Package-private seam: exposed so tests can verify client construction (region, endpoint
   * override, credentials per {@link AwsAuthentication} variant, proxy, timeouts) independently of
   * {@link #create(ChatModelConfiguration)}, which wires the client this returns into a {@link
   * BedrockConverseChatModel}.
   */
  BedrockRuntimeAsyncClient buildAsyncClient(BedrockConverseConnection connection) {
    final var apiTimeout =
        deriveTimeoutSetting("Bedrock model call", config, connection.timeouts(), LOGGER);

    final BedrockRuntimeAsyncClientBuilder builder =
        BedrockRuntimeAsyncClient.builder().region(Region.of(connection.region()));
    final var overrideConfigurationBuilder = ClientOverrideConfiguration.builder();

    applyAuthentication(connection.authentication(), builder);

    URI endpointOverride = null;
    if (connection.endpoint() != null) {
      endpointOverride = URI.create(connection.endpoint());
      builder.endpointOverride(endpointOverride);
    }

    overrideConfigurationBuilder.apiCallTimeout(apiTimeout);
    builder.overrideConfiguration(overrideConfigurationBuilder.build());

    // Netty's readTimeout is the streaming client's analogue to Apache's socketTimeout; mapping
    // the API-call timeout onto it keeps a long-running model call from being killed by socket
    // inactivity partway through generation.
    builder.httpClientBuilder(
        httpProxySupport
            .createAwsAsyncHttpClientBuilder(endpointOverride)
            .connectionTimeout(CONNECT_TIMEOUT)
            .readTimeout(apiTimeout));

    return builder.build();
  }

  /**
   * Wires the credential source the user configured, and pins the auth scheme that source can
   * actually satisfy.
   *
   * <p>The scheme is pinned in every branch, not just the API-key one: left unset, the SDK resolves
   * the scheme preference from the environment ({@code AWS_AUTH_SCHEME_PREFERENCE}, {@code
   * aws.authSchemePreference}, or the profile's {@code auth_scheme_preference}), which could push a
   * scheme the configured credentials cannot satisfy to the front - e.g. an environment preferring
   * {@code httpBearerAuth} would make an explicitly configured access key/secret lose to the SDK's
   * default token provider. Explicit connector configuration wins over ambient environment either
   * way.
   *
   * <p>Only the scheme is pinned, never the identity: sigv4 resolves its credentials from the
   * provider set here, and the default credentials chain is built only for the branch that asks for
   * it (the SDK falls back to it solely when no credentials provider was set at all).
   */
  private static void applyAuthentication(
      AwsAuthentication authentication, BedrockRuntimeAsyncClientBuilder builder) {
    switch (authentication) {
      case AwsAuthentication.AwsStaticCredentialsAuthentication staticAuth ->
          builder
              .credentialsProvider(
                  StaticCredentialsProvider.create(
                      AwsBasicCredentials.create(staticAuth.accessKey(), staticAuth.secretKey())))
              .authSchemeProvider(preferring(SIGV4_AUTH_SCHEME));
      case AwsAuthentication.AwsApiKeyAuthentication apiKeyAuth ->
          // Native "Bedrock API keys" support: a bearer token, not sigv4 credentials. Without the
          // pin, sigv4 stays ahead of httpBearerAuth (the SDK's default order) and the token would
          // never be sent.
          builder
              .tokenProvider(StaticTokenProvider.create(apiKeyAuth::apiKey))
              .authSchemeProvider(preferring(BEARER_AUTH_SCHEME));
      case AwsAuthentication.AwsDefaultCredentialsChainAuthentication ignored ->
          builder
              .credentialsProvider(DefaultCredentialsProvider.builder().build())
              .authSchemeProvider(preferring(SIGV4_AUTH_SCHEME));
    }
  }

  /**
   * An auth scheme provider resolving the given scheme ahead of the operation's other candidates,
   * which stay in the list as fallbacks. The SDK picks the first candidate whose identity is
   * resolvable.
   */
  private static BedrockRuntimeAuthSchemeProvider preferring(String authSchemeName) {
    return BedrockRuntimeAuthSchemeProvider.defaultProvider(List.of(authSchemeName));
  }
}
