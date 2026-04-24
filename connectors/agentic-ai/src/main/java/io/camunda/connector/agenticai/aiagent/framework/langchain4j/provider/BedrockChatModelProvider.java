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
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import java.net.URI;
import java.util.Optional;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public class BedrockChatModelProvider
    extends AbstractChatModelProvider<BedrockProviderConfiguration> {

  private final ChatModelHttpProxySupport proxySupport;

  public BedrockChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties,
      ChatModelHttpProxySupport proxySupport) {
    super(agenticAiConnectorsConfigurationProperties);
    this.proxySupport = proxySupport;
  }

  @Override
  public String type() {
    return BedrockProviderConfiguration.BEDROCK_ID;
  }

  @Override
  public ChatModel createChatModel(BedrockProviderConfiguration bedrock) {
    final var connection = bedrock.bedrock();

    final var builder =
        BedrockChatModel.builder()
            .client(createBedrockClient(connection))
            .modelId(connection.model().model())
            .timeout(deriveTimeoutSetting(connection.timeouts()));

    applyBedrockModelParametersIfPresent(connection, builder);

    return builder.build();
  }

  private BedrockRuntimeClient createBedrockClient(
      BedrockProviderConfiguration.BedrockConnection connection) {
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

    overrideClientConfigurationBuilder.apiCallTimeout(deriveTimeoutSetting(connection.timeouts()));

    SdkHttpClient httpClient = proxySupport.createAwsHttpClient(endpointOverride);
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
