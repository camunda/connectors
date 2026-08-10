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
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockChatModelConfiguration.BedrockConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.AwsAuthentication;
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
 * with the request/response converters, in a {@link BedrockChatModelApi}.
 */
public class BedrockChatModelApiFactory implements ChatModelFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(BedrockChatModelApiFactory.class);

  private final ChatModelProperties config;
  private final AgenticAiHttpProxySupport httpProxySupport;
  private final BedrockConverseRequestConverter requestConverter;
  private final BedrockConverseResponseConverter responseConverter;
  private final ObjectMapper objectMapper;

  public BedrockChatModelApiFactory(
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
    return configuration instanceof BedrockChatModelConfiguration;
  }

  @Override
  public ChatModel create(ChatModelConfiguration configuration) {
    final var model = (BedrockChatModelConfiguration) configuration;
    final var client = buildAsyncClient(model.bedrock());
    return new BedrockChatModelApi(
        client, model, requestConverter, responseConverter, objectMapper);
  }

  /**
   * Builds the {@link BedrockRuntimeAsyncClient} for the given connection.
   *
   * <p>Package-private seam: exposed so tests can verify client construction (region, endpoint
   * override, credentials per {@link AwsAuthentication} variant, proxy, timeouts) independently of
   * {@link #create(ChatModelConfiguration)}, which wires the client this returns into a {@link
   * BedrockChatModelApi}.
   */
  BedrockRuntimeAsyncClient buildAsyncClient(BedrockConnection connection) {
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

  private static void applyAuthentication(
      AwsAuthentication authentication, BedrockRuntimeAsyncClientBuilder builder) {
    switch (authentication) {
      case AwsAuthentication.AwsStaticCredentialsAuthentication staticAuth ->
          builder.credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(staticAuth.accessKey(), staticAuth.secretKey())));
      case AwsAuthentication.AwsDefaultCredentialsChainAuthentication ignored ->
          builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
      case AwsAuthentication.AwsApiKeyAuthentication apiKeyAuth ->
          // Native "Bedrock API keys" support (the same mechanism as AWS_BEARER_TOKEN_BEDROCK):
          // the httpBearerAuth scheme is listed after sigv4 by default, and sigv4 is always
          // resolvable (credentialsProvider falls back to the default chain when unset), so
          // without this preference override sigv4 would win and the token would never be sent.
          builder
              .tokenProvider(StaticTokenProvider.create(apiKeyAuth::apiKey))
              .authSchemeProvider(
                  BedrockRuntimeAuthSchemeProvider.defaultProvider(List.of("httpBearerAuth")));
    }
  }
}
