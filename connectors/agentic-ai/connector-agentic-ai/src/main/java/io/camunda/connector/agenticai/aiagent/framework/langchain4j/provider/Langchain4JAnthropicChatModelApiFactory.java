/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import static io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderSupport.CONNECT_TIMEOUT;
import static io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderSupport.deriveTimeoutSetting;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.CloseableChatModel;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.CloseableChatModelDelegate;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.Langchain4JChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Langchain4JAnthropicChatModelApiFactory
    extends Langchain4JChatModelApiFactory<AnthropicProviderConfiguration> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(Langchain4JAnthropicChatModelApiFactory.class);

  private final ChatModelProperties config;
  private final ChatModelHttpProxySupport proxySupport;

  public Langchain4JAnthropicChatModelApiFactory(
      ChatModelProperties config,
      ChatModelHttpProxySupport proxySupport,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter,
      ModelCapabilities capabilities) {
    super(chatMessageConverter, toolSpecificationConverter, jsonSchemaConverter, capabilities);
    this.config = config;
    this.proxySupport = proxySupport;
  }

  @Override
  protected String providerType() {
    return AnthropicProviderConfiguration.ANTHROPIC_ID;
  }

  @Override
  protected CloseableChatModel createChatModel(AnthropicProviderConfiguration anthropic) {
    final var connection = anthropic.anthropic();
    final var apiTimeout =
        deriveTimeoutSetting("Anthropic model call", config, connection.timeouts(), LOGGER);

    final var http = proxySupport.createJdkHttpClientBuilder();
    final var builder =
        AnthropicChatModel.builder()
            .apiKey(connection.authentication().apiKey())
            .modelName(connection.model().model())
            .timeout(apiTimeout)
            .httpClientBuilder(http.connectTimeout(CONNECT_TIMEOUT).readTimeout(apiTimeout));

    Optional.ofNullable(connection.endpoint()).ifPresent(builder::baseUrl);

    final var modelParameters = connection.model().parameters();
    if (modelParameters != null) {
      Optional.ofNullable(modelParameters.maxTokens()).ifPresent(builder::maxTokens);
      Optional.ofNullable(modelParameters.temperature()).ifPresent(builder::temperature);
      Optional.ofNullable(modelParameters.topP()).ifPresent(builder::topP);
      Optional.ofNullable(modelParameters.topK()).ifPresent(builder::topK);
    }

    return new CloseableChatModelDelegate(builder.build(), http);
  }

  @Override
  protected AgentMetrics.TokenUsage mapTokenUsage(@Nullable TokenUsage usage) {
    if (usage instanceof AnthropicTokenUsage anthropicTokenUsage) {
      return baseTokenUsageBuilder(anthropicTokenUsage)
          .cacheReadTokenCount(nullToZero(anthropicTokenUsage.cacheReadInputTokens()))
          .cacheCreationTokenCount(nullToZero(anthropicTokenUsage.cacheCreationInputTokens()))
          .build();
    }

    return super.mapTokenUsage(usage);
  }
}
