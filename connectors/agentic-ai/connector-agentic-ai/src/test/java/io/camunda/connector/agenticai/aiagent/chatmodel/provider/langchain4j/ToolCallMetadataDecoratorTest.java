/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.ProviderConfiguration;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ToolCallMetadataDecoratorTest {

  private static final GoogleVertexAiProviderConfiguration GOOGLE_VERTEX_AI =
      new GoogleVertexAiProviderConfiguration(null);

  private static final String TOOL_CALL_ID = "toolCallId";

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
            ToolCallMetadataDecorator.decorateOnWrite(
                providerConfiguration,
                TOOL_CALL_ID,
                Map.of("thought_signature_" + TOOL_CALL_ID, "c2lnbmF0dXJl")))
        .isEmpty();
  }

  @ParameterizedTest
  @MethodSource("nonVertexAiProviders")
  void decorateOnRead_dropsAttributesForNonVertexAiProviders(
      ProviderConfiguration providerConfiguration) {
    assertThat(
            ToolCallMetadataDecorator.decorateOnRead(
                providerConfiguration,
                TOOL_CALL_ID,
                Map.of(
                    GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID,
                    Map.of("thoughtSignature", "c2lnbmF0dXJl"))))
        .isEmpty();
  }

  @Test
  void decorateOnWrite_keepsThoughtSignatureMatchingThisToolCallId_namespacedByProviderId() {
    final var aiMessageAttributes =
        Map.<String, Object>of(
            "thought_signature_" + TOOL_CALL_ID,
            "c2lnbmF0dXJl",
            "thought_signature_someOtherToolCallId",
            "unrelated-signature",
            "raw_http_response",
            "leak-risk");

    assertThat(
            ToolCallMetadataDecorator.decorateOnWrite(
                GOOGLE_VERTEX_AI, TOOL_CALL_ID, aiMessageAttributes))
        .containsExactly(
            entry(
                GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID,
                Map.of("thoughtSignature", "c2lnbmF0dXJl")));
  }

  @Test
  void decorateOnWrite_dropsResultForGoogleVertexAiWhenNoMatchingThoughtSignature() {
    assertThat(
            ToolCallMetadataDecorator.decorateOnWrite(
                GOOGLE_VERTEX_AI, TOOL_CALL_ID, Map.of("raw_http_response", "leak-risk")))
        .isEmpty();
  }

  @Test
  void decorateOnWrite_dropsResultForGoogleVertexAiWhenSignatureValueIsNotAString() {
    assertThat(
            ToolCallMetadataDecorator.decorateOnWrite(
                GOOGLE_VERTEX_AI, TOOL_CALL_ID, Map.of("thought_signature_" + TOOL_CALL_ID, 42)))
        .isEmpty();
  }

  @Test
  void decorateOnRead_returnsPrefixedAttributeEntryForGoogleVertexAi() {
    final var persisted =
        Map.of(
            GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID,
            Map.of("thoughtSignature", "c2ln"));

    assertThat(ToolCallMetadataDecorator.decorateOnRead(GOOGLE_VERTEX_AI, TOOL_CALL_ID, persisted))
        .containsExactly(entry("thought_signature_" + TOOL_CALL_ID, "c2ln"));
  }

  @Test
  void decorateOnRead_dropsResultForGoogleVertexAiWhenSignatureValueIsNotAString() {
    final var persisted =
        Map.of(
            GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID,
            Map.of("thoughtSignature", 42));

    assertThat(ToolCallMetadataDecorator.decorateOnRead(GOOGLE_VERTEX_AI, TOOL_CALL_ID, persisted))
        .isEmpty();
  }

  /**
   * Persisted entries are namespaced by provider ID so that metadata left behind by a previous
   * provider (e.g. after a config update or process instance migration) is never picked up as if it
   * belonged to the current one.
   */
  @Test
  void decorateOnRead_ignoresMetadataPersistedUnderADifferentProviderId() {
    final var persisted = Map.of("some-other-provider", Map.of("thoughtSignature", "c2ln"));

    assertThat(ToolCallMetadataDecorator.decorateOnRead(GOOGLE_VERTEX_AI, TOOL_CALL_ID, persisted))
        .isEmpty();
  }
}
