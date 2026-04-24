/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import java.util.Optional;

public class OpenAiChatModelProvider
    extends AbstractChatModelProvider<OpenAiProviderConfiguration> {

  private final ChatModelHttpProxySupport proxySupport;

  public OpenAiChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties,
      ChatModelHttpProxySupport proxySupport) {
    super(agenticAiConnectorsConfigurationProperties);
    this.proxySupport = proxySupport;
  }

  @Override
  public String type() {
    return OpenAiProviderConfiguration.OPENAI_ID;
  }

  @Override
  public ChatModel createChatModel(OpenAiProviderConfiguration openai) {
    final var connection = openai.openai();

    final var builder =
        OpenAiChatModel.builder()
            .apiKey(connection.authentication().apiKey())
            .modelName(connection.model().model())
            .timeout(deriveTimeoutSetting(connection.timeouts()))
            .httpClientBuilder(proxySupport.createJdkHttpClientBuilder());

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

    return builder.build();
  }
}
