/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

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

  private static final GoogleVertexAiProviderConfiguration GOOGLE_VERTEX_AI =
      new GoogleVertexAiProviderConfiguration(null);

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
  void decorateOnWrite_dropsAttributesForNonVertexAiProviders(
      ProviderConfiguration providerConfiguration) {
    assertThat(
            AssistantMessageMetadataDecorator.decorateOnWrite(
                providerConfiguration, Map.of("a", "b")))
        .isEmpty();
  }

  @ParameterizedTest
  @MethodSource("nonVertexAiProviders")
  void decorateOnRead_dropsAttributesForNonVertexAiProviders(
      ProviderConfiguration providerConfiguration) {
    assertThat(
            AssistantMessageMetadataDecorator.decorateOnRead(
                providerConfiguration, Map.of("a", "b")))
        .isEmpty();
  }

  @Test
  void decorateOnWrite_keepsOnlyThoughtSignaturesForGoogleVertexAi_namespacedByProviderId() {
    final var attributes =
        Map.<String, Object>of(
            "thought_signature_toolCallId", "c2lnbmF0dXJl",
            "raw_http_response", "leak-risk");

    assertThat(AssistantMessageMetadataDecorator.decorateOnWrite(GOOGLE_VERTEX_AI, attributes))
        .containsExactly(
            entry(
                GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID,
                Map.of("thought_signature_toolCallId", "c2lnbmF0dXJl")));
  }

  @Test
  void decorateOnWrite_dropsResultForGoogleVertexAiWhenNoThoughtSignaturesRemain() {
    assertThat(
            AssistantMessageMetadataDecorator.decorateOnWrite(
                GOOGLE_VERTEX_AI, Map.of("raw_http_response", "leak-risk")))
        .isEmpty();
  }

  @Test
  void decorateOnRead_keepsOnlyStringValuedThoughtSignaturesForGoogleVertexAi() {
    final var persisted =
        Map.of(
            GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID,
            Map.of("thought_signature_a", "c2ln", "thought_signature_b", 42));

    assertThat(AssistantMessageMetadataDecorator.decorateOnRead(GOOGLE_VERTEX_AI, persisted))
        .containsExactly(entry("thought_signature_a", "c2ln"));
  }

  /**
   * Persisted entries are namespaced by provider ID so that attributes left behind by a previous
   * provider (e.g. after a config update or process instance migration) are never picked up as if
   * they belonged to the current one.
   */
  @Test
  void decorateOnRead_ignoresAttributesPersistedUnderADifferentProviderId() {
    final var persisted = Map.of("some-other-provider", Map.of("thought_signature_a", "c2ln"));

    assertThat(AssistantMessageMetadataDecorator.decorateOnRead(GOOGLE_VERTEX_AI, persisted))
        .isEmpty();
  }
}
