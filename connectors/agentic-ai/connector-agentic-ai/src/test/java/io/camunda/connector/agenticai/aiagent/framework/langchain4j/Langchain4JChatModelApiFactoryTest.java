/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.api.LlmProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.api.ProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Langchain4JChatModelApiFactoryTest {

  @Mock private ChatModelFactory chatModelFactory;
  @Mock private ChatMessageConverter chatMessageConverter;
  @Mock private ToolSpecificationConverter toolSpecificationConverter;
  @Mock private JsonSchemaConverter jsonSchemaConverter;
  @Mock private CloseableChatModel chatModel;

  private Langchain4JChatModelApiFactory factory;

  @BeforeEach
  void setUp() {
    factory =
        new Langchain4JChatModelApiFactory(
            provider -> provider instanceof AnthropicProviderConfiguration,
            chatModelFactory,
            chatMessageConverter,
            toolSpecificationConverter,
            jsonSchemaConverter,
            Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
  }

  @Test
  void supportsConfigurationMatchingPredicate() {
    final ChatModelApiConfiguration configuration =
        new ProviderChatModelApiConfiguration(anthropicProviderConfiguration());

    assertThat(factory.supports(configuration)).isTrue();
  }

  @Test
  void doesNotSupportConfigurationNotMatchingPredicate() {
    final var rejectingFactory =
        new Langchain4JChatModelApiFactory(
            provider -> false,
            chatModelFactory,
            chatMessageConverter,
            toolSpecificationConverter,
            jsonSchemaConverter,
            Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
    final ChatModelApiConfiguration configuration =
        new ProviderChatModelApiConfiguration(anthropicProviderConfiguration());

    assertThat(rejectingFactory.supports(configuration)).isFalse();
  }

  @Test
  void doesNotSupportNonProviderChatModelApiConfiguration() {
    final ChatModelApiConfiguration configuration =
        mock(LlmProviderChatModelApiConfiguration.class);

    assertThat(factory.supports(configuration)).isFalse();
  }

  @Test
  void createBuildsChatModelOnceAndReturnsLangchain4JChatModelApi() {
    final var providerConfiguration = anthropicProviderConfiguration();
    final ChatModelApiConfiguration configuration =
        new ProviderChatModelApiConfiguration(providerConfiguration);
    when(chatModelFactory.createChatModel(providerConfiguration)).thenReturn(chatModel);

    final var api = factory.create(configuration);

    assertThat(api).isInstanceOf(Langchain4JChatModelApi.class);
    assertThat(api.capabilities()).isSameAs(Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
    verify(chatModelFactory).createChatModel(providerConfiguration);
  }

  private static AnthropicProviderConfiguration anthropicProviderConfiguration() {
    return new AnthropicProviderConfiguration(
        new AnthropicConnection(
            null,
            new AnthropicAuthentication("api-key"),
            null,
            new AnthropicModel("claude", null)));
  }
}
