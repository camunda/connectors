/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.CloseableChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.Langchain4JChatModelApi;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Exercises the {@code supports()}/{@code create()} behavior shared by every {@link
 * Langchain4JChatModelApiFactory} subclass, via a minimal concrete factory. Provider-specific
 * {@code createChatModel} and {@code mapTokenUsage} behavior is covered by the per-provider factory
 * tests.
 */
@ExtendWith(MockitoExtension.class)
class Langchain4JChatModelApiFactoryTest {

  @Mock private ChatMessageConverter chatMessageConverter;
  @Mock private ToolSpecificationConverter toolSpecificationConverter;
  @Mock private JsonSchemaConverter jsonSchemaConverter;
  @Mock private CloseableChatModel chatModel;

  @Test
  void supportsConfigurationMatchingProviderType() {
    final var factory = testFactory(AnthropicProviderConfiguration.ANTHROPIC_ID);
    final ChatModelApiConfiguration configuration = anthropicProviderConfiguration();

    assertThat(factory.supports(configuration)).isTrue();
  }

  @Test
  void doesNotSupportConfigurationWithDifferentProviderType() {
    final var factory = testFactory("some-other-provider");
    final ChatModelApiConfiguration configuration = anthropicProviderConfiguration();

    assertThat(factory.supports(configuration)).isFalse();
  }

  @Test
  void doesNotSupportNonV1ProviderConfiguration() {
    final var factory = testFactory(AnthropicProviderConfiguration.ANTHROPIC_ID);
    final ChatModelApiConfiguration configuration =
        new AnthropicChatModel(
            new AnthropicChatModel.AnthropicConnection(
                new AnthropicDirectBackend(null, "sk-ant"),
                new AnthropicChatModel.AnthropicModel("claude-sonnet-4-6", null),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

    assertThat(factory.supports(configuration)).isFalse();
  }

  @Test
  void createBuildsChatModelOnceAndReturnsLangchain4JChatModelApi() {
    final var providerConfiguration = anthropicProviderConfiguration();
    final ChatModelApiConfiguration configuration = providerConfiguration;
    final var factory = testFactory(AnthropicProviderConfiguration.ANTHROPIC_ID);

    final var api = factory.create(configuration);

    assertThat(api).isInstanceOf(Langchain4JChatModelApi.class);
    assertThat(api.capabilities()).isSameAs(Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
    assertThat(factory.createChatModelInvocations).containsExactly(providerConfiguration);
  }

  private TestChatModelApiFactory testFactory(String providerType) {
    return new TestChatModelApiFactory(
        providerType,
        chatModel,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter,
        Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
  }

  private static AnthropicProviderConfiguration anthropicProviderConfiguration() {
    return new AnthropicProviderConfiguration(
        new AnthropicConnection(
            null,
            new AnthropicAuthentication("api-key"),
            null,
            new AnthropicModel("claude", null)));
  }

  private static final class TestChatModelApiFactory
      extends Langchain4JChatModelApiFactory<AnthropicProviderConfiguration> {

    private final String providerType;
    private final CloseableChatModel chatModel;
    private final List<AnthropicProviderConfiguration> createChatModelInvocations =
        new ArrayList<>();

    TestChatModelApiFactory(
        String providerType,
        CloseableChatModel chatModel,
        ChatMessageConverter chatMessageConverter,
        ToolSpecificationConverter toolSpecificationConverter,
        JsonSchemaConverter jsonSchemaConverter,
        ModelCapabilities capabilities) {
      super(chatMessageConverter, toolSpecificationConverter, jsonSchemaConverter, capabilities);
      this.providerType = providerType;
      this.chatModel = chatModel;
    }

    @Override
    protected String providerType() {
      return providerType;
    }

    @Override
    protected CloseableChatModel createChatModel(
        AnthropicProviderConfiguration providerConfiguration) {
      createChatModelInvocations.add(providerConfiguration);
      return chatModel;
    }
  }
}
