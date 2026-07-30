/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AssistantMessageMetadataDecoratorTest {

  static Stream<ProviderConfiguration> nonVertexAiProviders() {
    return Stream.of(
        new AnthropicProviderConfiguration(null),
        new BedrockProviderConfiguration(null),
        new AzureOpenAiProviderConfiguration(null),
        new OpenAiProviderConfiguration(null),
        new OpenAiCompatibleProviderConfiguration(null));
  }

  @ParameterizedTest
  @MethodSource("nonVertexAiProviders")
  void forProvider_resolvesToDropAttributesForNonVertexAiProviders(
      ProviderConfiguration providerConfiguration) {
    assertThat(AssistantMessageMetadataDecorator.forProvider(providerConfiguration))
        .isSameAs(AssistantMessageMetadataDecorator.DROP_ATTRIBUTES);
  }

  @Test
  void forProvider_resolvesToGoogleVertexAiDecoratorForGoogleVertexAiProvider() {
    assertThat(
            AssistantMessageMetadataDecorator.forProvider(
                new GoogleVertexAiProviderConfiguration(null)))
        .isSameAs(GoogleVertexAiAssistantMessageMetadataDecorator.INSTANCE);
  }

  @Test
  void dropAttributes_alwaysReturnsEmptyMap() {
    assertThat(AssistantMessageMetadataDecorator.DROP_ATTRIBUTES.decorateOnWrite(Map.of("a", "b")))
        .isEmpty();
    assertThat(AssistantMessageMetadataDecorator.DROP_ATTRIBUTES.decorateOnRead(Map.of("a", "b")))
        .isEmpty();
  }
}
