/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory;

import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.ChatModelProviderSupport.CONNECT_TIMEOUT;
import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.ChatModelProviderSupport.deriveTimeoutSetting;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.bedrock.BedrockTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.CloseableChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.CloseableChatModelDelegate;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.request.v1.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public class BedrockChatModelFactory
    extends LangChain4JChatModelFactory<BedrockProviderConfiguration> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BedrockChatModelFactory.class);

  private final ChatModelProperties config;
  private final ChatModelHttpProxySupport proxySupport;

  public BedrockChatModelFactory(
      ChatModelProperties config,
      ChatModelHttpProxySupport proxySupport,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    super(chatMessageConverter, toolSpecificationConverter, jsonSchemaConverter);
    this.config = config;
    this.proxySupport = proxySupport;
  }

  @Override
  public String providerType() {
    return BedrockProviderConfiguration.BEDROCK_ID;
  }

  @Override
  public CloseableChatModel createChatModel(BedrockProviderConfiguration bedrock) {
    final var connection = bedrock.bedrock();
    final var apiTimeout =
        deriveTimeoutSetting("Bedrock model call", config, connection.timeouts(), LOGGER);

    final var bedrockRuntimeClient = createBedrockClient(connection, apiTimeout);
    final var builder =
        BedrockChatModel.builder()
            .client(bedrockRuntimeClient)
            .modelId(connection.model().model())
            .timeout(apiTimeout);

    applyBedrockModelParametersIfPresent(connection, builder);

    return new CloseableChatModelDelegate(builder.build(), bedrockRuntimeClient);
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
    // socket timeout of 30 seconds. The TCP connect timeout is kept at a small constant since it
    // covers infrastructure availability (DNS / firewall / proxy), not model latency. See issue
    // #7193.
    bedrockClientBuilder.httpClientBuilder(
        proxySupport
            .createAwsHttpClientBuilder(endpointOverride)
            .connectionTimeout(CONNECT_TIMEOUT)
            .socketTimeout(apiTimeout));

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

  @Override
  protected AgentMetrics.TokenUsage mapTokenUsage(@Nullable TokenUsage usage) {
    if (usage instanceof BedrockTokenUsage bedrockTokenUsage) {
      return baseTokenUsageBuilder(bedrockTokenUsage)
          .cacheReadTokenCount(nullToZero(bedrockTokenUsage.cacheReadInputTokens()))
          .cacheCreationTokenCount(nullToZero(bedrockTokenUsage.cacheWriteInputTokens()))
          .build();
    }
    return super.mapTokenUsage(usage);
  }
}
