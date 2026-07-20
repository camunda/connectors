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
import io.camunda.connector.agenticai.aiagent.framework.api.V1ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.api.V2ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProvider;
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

  @Mock private ChatModelProvider<AnthropicProviderConfiguration> chatModelProvider;
  @Mock private ChatMessageConverter chatMessageConverter;
  @Mock private ToolSpecificationConverter toolSpecificationConverter;
  @Mock private JsonSchemaConverter jsonSchemaConverter;
  @Mock private CloseableChatModel chatModel;

  private Langchain4JChatModelApiFactory factory;

  @BeforeEach
  void setUp() {
    factory =
        new Langchain4JChatModelApiFactory(
            chatModelProvider,
            chatMessageConverter,
            toolSpecificationConverter,
            jsonSchemaConverter,
            Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
  }

  @Test
  void supportsConfigurationMatchingProviderType() {
    when(chatModelProvider.type()).thenReturn(AnthropicProviderConfiguration.ANTHROPIC_ID);
    final ChatModelApiConfiguration configuration =
        new V1ChatModelApiConfiguration(anthropicProviderConfiguration());

    assertThat(factory.supports(configuration)).isTrue();
  }

  @Test
  void doesNotSupportConfigurationWithDifferentProviderType() {
    when(chatModelProvider.type()).thenReturn("some-other-provider");
    final ChatModelApiConfiguration configuration =
        new V1ChatModelApiConfiguration(anthropicProviderConfiguration());

    assertThat(factory.supports(configuration)).isFalse();
  }

  @Test
  void doesNotSupportNonV1ChatModelApiConfiguration() {
    final ChatModelApiConfiguration configuration = mock(V2ChatModelApiConfiguration.class);

    assertThat(factory.supports(configuration)).isFalse();
  }

  @Test
  void createBuildsChatModelOnceAndReturnsLangchain4JChatModelApi() {
    final var providerConfiguration = anthropicProviderConfiguration();
    final ChatModelApiConfiguration configuration =
        new V1ChatModelApiConfiguration(providerConfiguration);
    when(chatModelProvider.createChatModel(providerConfiguration)).thenReturn(chatModel);

    final var api = factory.create(configuration);

    assertThat(api).isInstanceOf(Langchain4JChatModelApi.class);
    assertThat(api.capabilities()).isSameAs(Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
    verify(chatModelProvider).createChatModel(providerConfiguration);
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
