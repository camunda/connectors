/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.ChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public class BedrockChatModelProvider implements ChatModelProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(BedrockChatModelProvider.class);

  private final AgenticAiConnectorsConfigurationProperties.ChatModelProperties chatModelProperties;

  public BedrockChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties) {
    this.chatModelProperties = agenticAiConnectorsConfigurationProperties.aiagent().chatModel();
  }

  @Override
  public String getProviderType() {
    return BedrockProviderConfiguration.BEDROCK_ID;
  }

  @Override
  public boolean supports(ProviderConfiguration providerConfiguration) {
    return providerConfiguration instanceof BedrockProviderConfiguration;
  }

  @Override
  public ChatModel createChatModel(ProviderConfiguration providerConfiguration) {
    if (!(providerConfiguration instanceof BedrockProviderConfiguration bedrock)) {
      throw new IllegalArgumentException(
          "Expected BedrockProviderConfiguration but got "
              + providerConfiguration.getClass().getSimpleName());
    }

    final var connection = bedrock.bedrock();

    final var builder =
        BedrockChatModel.builder()
            .client(createBedrockClient(connection))
            .modelId(connection.model().model())
            .timeout(deriveTimeoutSetting(connection.timeouts()));

    return applyBedrockModelParametersIfPresent(connection, builder).build();
  }

  private BedrockRuntimeClient createBedrockClient(
      BedrockProviderConfiguration.BedrockConnection connection) {
    var bedrockClientBuilder =
        BedrockRuntimeClient.builder().region(Region.of(connection.region()));
    var overrideClientConfigurationBuilder = ClientOverrideConfiguration.builder();

    var authenticationCustomizer = AwsBedrockRuntimeAuthenticationCustomizer.createFor(connection);
    authenticationCustomizer.provideAuthenticationMechanism(
        bedrockClientBuilder, overrideClientConfigurationBuilder);

    if (connection.endpoint() != null) {
      bedrockClientBuilder.endpointOverride(URI.create(connection.endpoint()));
    }

    overrideClientConfigurationBuilder.apiCallTimeout(deriveTimeoutSetting(connection.timeouts()));

    bedrockClientBuilder.overrideConfiguration(overrideClientConfigurationBuilder.build());

    return bedrockClientBuilder.build();
  }

  private BedrockChatModel.Builder applyBedrockModelParametersIfPresent(
      BedrockProviderConfiguration.BedrockConnection connection, BedrockChatModel.Builder builder) {
    final var modelParameters = connection.model().parameters();
    if (modelParameters == null) {
      return builder;
    }

    final var requestParametersBuilder = BedrockChatRequestParameters.builder();
    Optional.ofNullable(modelParameters.maxTokens())
        .ifPresent(requestParametersBuilder::maxOutputTokens);
    Optional.ofNullable(modelParameters.temperature())
        .ifPresent(requestParametersBuilder::temperature);
    Optional.ofNullable(modelParameters.topP()).ifPresent(requestParametersBuilder::topP);

    builder.defaultRequestParameters(requestParametersBuilder.build());

    return builder;
  }

  private Duration deriveTimeoutSetting(TimeoutConfiguration timeoutConfiguration) {
    var derivedTimeout =
        Optional.ofNullable(timeoutConfiguration)
            .map(TimeoutConfiguration::timeout)
            .filter(Duration::isPositive)
            .or(() -> Optional.of(chatModelProperties.api().defaultTimeout()))
            .get();

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Setting model call timeout to {} for executing requests against the LLM provider",
          derivedTimeout);
    }

    return derivedTimeout;
  }
}
