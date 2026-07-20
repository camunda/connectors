/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory;

import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.ChatModelProviderSupport.CONNECT_TIMEOUT;
import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.ChatModelProviderSupport.deriveTimeoutSetting;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.CloseableChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.CloseableChatModelDelegate;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Langchain4JOpenAiChatModelApiFactory
    extends Langchain4JChatModelApiFactory<OpenAiProviderConfiguration> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(Langchain4JOpenAiChatModelApiFactory.class);

  private final ChatModelProperties config;
  private final ChatModelHttpProxySupport proxySupport;

  public Langchain4JOpenAiChatModelApiFactory(
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
    return OpenAiProviderConfiguration.OPENAI_ID;
  }

  @Override
  protected CloseableChatModel createChatModel(OpenAiProviderConfiguration openai) {
    final var connection = openai.openai();
    final var apiTimeout =
        deriveTimeoutSetting("OpenAI model call", config, connection.timeouts(), LOGGER);

    final var http = proxySupport.createJdkHttpClientBuilder();
    final var builder =
        OpenAiChatModel.builder()
            .apiKey(connection.authentication().apiKey())
            .modelName(connection.model().model())
            .timeout(apiTimeout)
            .httpClientBuilder(http.connectTimeout(CONNECT_TIMEOUT).readTimeout(apiTimeout));

    Optional.ofNullable(connection.authentication().organizationId())
        .ifPresent(builder::organizationId);
    Optional.ofNullable(connection.authentication().projectId()).ifPresent(builder::projectId);

    final var modelParameters = connection.model().parameters();
    if (modelParameters != null) {
      final var requestParametersBuilder = OpenAiChatRequestParameters.builder();
      Optional.ofNullable(modelParameters.maxCompletionTokens())
          .ifPresent(requestParametersBuilder::maxCompletionTokens);
      Optional.ofNullable(modelParameters.temperature())
          .ifPresent(requestParametersBuilder::temperature);
      Optional.ofNullable(modelParameters.topP()).ifPresent(requestParametersBuilder::topP);

      builder.defaultRequestParameters(requestParametersBuilder.build());
    }

    return new CloseableChatModelDelegate(builder.build(), http);
  }

  @Override
  protected AgentMetrics.TokenUsage mapTokenUsage(@Nullable TokenUsage usage) {
    if (usage instanceof OpenAiTokenUsage openAiTokenUsage) {
      return ChatModelProviderSupport.applyOpenAiTokenUsageDetail(
              baseTokenUsageBuilder(openAiTokenUsage), openAiTokenUsage)
          .build();
    }

    return super.mapTokenUsage(usage);
  }
}
