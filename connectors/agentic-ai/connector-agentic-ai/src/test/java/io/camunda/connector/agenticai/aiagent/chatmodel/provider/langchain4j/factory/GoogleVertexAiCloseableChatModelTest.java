/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.langchain4j.model.chat.ChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GoogleVertexAiCloseableChatModelTest {

  private static final String TOOL_CALL_ID = "toolCallId";

  private final ChatModel delegate = mock(ChatModel.class);
  private final AutoCloseable resource = mock(AutoCloseable.class);
  private final GoogleVertexAiCloseableChatModel chatModel =
      new GoogleVertexAiCloseableChatModel(delegate, resource);

  @Test
  void closeClosesTheResource() throws Exception {
    chatModel.close();
    verify(resource).close();
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

    assertThat(chatModel.decorateOnWrite(TOOL_CALL_ID, aiMessageAttributes))
        .containsExactly(
            entry(
                GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID,
                Map.of("thoughtSignature", "c2lnbmF0dXJl")));
  }

  @Test
  void decorateOnWrite_dropsResultWhenNoMatchingThoughtSignature() {
    assertThat(chatModel.decorateOnWrite(TOOL_CALL_ID, Map.of("raw_http_response", "leak-risk")))
        .isEmpty();
  }

  @Test
  void decorateOnWrite_dropsResultWhenSignatureValueIsNotAString() {
    assertThat(
            chatModel.decorateOnWrite(
                TOOL_CALL_ID, Map.of("thought_signature_" + TOOL_CALL_ID, 42)))
        .isEmpty();
  }

  @Test
  void decorateOnRead_returnsPrefixedAttributeEntry() {
    final var persisted =
        Map.of(
            GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID,
            Map.of("thoughtSignature", "c2ln"));

    assertThat(chatModel.decorateOnRead(TOOL_CALL_ID, persisted))
        .containsExactly(entry("thought_signature_" + TOOL_CALL_ID, "c2ln"));
  }

  @Test
  void decorateOnRead_dropsResultWhenSignatureValueIsNotAString() {
    final var persisted =
        Map.of(
            GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID,
            Map.of("thoughtSignature", 42));

    assertThat(chatModel.decorateOnRead(TOOL_CALL_ID, persisted)).isEmpty();
  }

  /**
   * Persisted entries are namespaced by provider ID so that metadata left behind by a previous
   * provider (e.g. after a config update or process instance migration) is never picked up as if it
   * belonged to this one.
   */
  @Test
  void decorateOnRead_ignoresMetadataPersistedUnderADifferentProviderId() {
    final var persisted = Map.of("some-other-provider", Map.of("thoughtSignature", "c2ln"));

    assertThat(chatModel.decorateOnRead(TOOL_CALL_ID, persisted)).isEmpty();
  }
}
