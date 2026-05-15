/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import static io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderSupport.deriveTimeoutSetting;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.ChatModel;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public class BedrockChatModelProvider implements ChatModelProvider<BedrockProviderConfiguration> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BedrockChatModelProvider.class);

  private final ChatModelProperties config;
  private final ChatModelHttpProxySupport proxySupport;

  public BedrockChatModelProvider(
      ChatModelProperties config, ChatModelHttpProxySupport proxySupport) {
    this.config = config;
    this.proxySupport = proxySupport;
  }

  @Override
  public String type() {
    return BedrockProviderConfiguration.BEDROCK_ID;
  }

  @Override
  public ChatModel createChatModel(BedrockProviderConfiguration bedrock) {
    final var connection = bedrock.bedrock();
    final var apiTimeout =
        deriveTimeoutSetting("Bedrock model call", config, connection.timeouts(), LOGGER);

    final var builder =
        BedrockChatModel.builder()
            .client(createBedrockClient(connection, apiTimeout))
            .modelId(connection.model().model())
            .timeout(apiTimeout);

    applyBedrockModelParametersIfPresent(connection, builder);

    return builder.build();
  }

  private BedrockRuntimeClient createBedrockClient(
      BedrockProviderConfiguration.BedrockConnection connection, Duration apiTimeout) {
    var bedrockClientBuilder =
        BedrockRuntimeClient.builder().region(Region.of(connection.region()));
    var overrideClientConfigurationBuilder = ClientOverrideConfiguration.builder();

    var authenticationCustomizer = AwsBedrockRuntimeAuthenticationCustomizer.createFor(connection);
    authenticationCustomizer.provideAuthenticationMechanism(
        bedrockClientBuilder, overrideClientConfigurationBuilder);

    URI endpointOverride = null;
    if (connection.endpoint() != null) {
      endpointOverride = URI.create(connection.endpoint());
      bedrockClientBuilder.endpointOverride(endpointOverride);
    }

    overrideClientConfigurationBuilder.apiCallTimeout(apiTimeout);

    // Align the underlying Apache HTTP socket timeout with the high-level API call timeout so
    // long-running LLM responses (e.g. Claude Sonnet > 30s) are not killed by the Apache default
    // socket timeout of 30 seconds. See issue #7193.
    SdkHttpClient httpClient =
        proxySupport
            .createAwsHttpClientBuilder(endpointOverride)
            .connectionTimeout(apiTimeout)
            .socketTimeout(apiTimeout)
            .build();
    bedrockClientBuilder.httpClient(httpClient);

    bedrockClientBuilder.overrideConfiguration(overrideClientConfigurationBuilder.build());

    return bedrockClientBuilder.build();
  }

  private void applyBedrockModelParametersIfPresent(
      BedrockProviderConfiguration.BedrockConnection connection, BedrockChatModel.Builder builder) {
    final var modelParameters = connection.model().parameters();
    if (modelParameters == null) {
      return;
    }

    final var requestParametersBuilder = BedrockChatRequestParameters.builder();
    Optional.ofNullable(modelParameters.maxTokens())
        .ifPresent(requestParametersBuilder::maxOutputTokens);
    Optional.ofNullable(modelParameters.temperature())
        .ifPresent(requestParametersBuilder::temperature);
    Optional.ofNullable(modelParameters.topP()).ifPresent(requestParametersBuilder::topP);

    builder.defaultRequestParameters(requestParametersBuilder.build());
  }
}
