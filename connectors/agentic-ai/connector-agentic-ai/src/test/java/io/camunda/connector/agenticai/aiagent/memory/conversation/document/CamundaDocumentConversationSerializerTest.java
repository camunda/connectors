/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationContext.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link ReasoningContent}, including its opaque {@code providerPayload}, survives
 * the document conversation store's JSON serialization boundary byte-identically. This is the
 * fidelity the Anthropic reasoning round-trip (and therefore prompt-cache prefix stability) relies
 * on when the document memory backend is used; it also exercises the same polymorphic {@code
 * Content} Jackson path the in-process backend persists through the {@code agentContext} variable.
 */
class CamundaDocumentConversationSerializerTest {

  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();
  private final CamundaDocumentConversationSerializer serializer =
      new CamundaDocumentConversationSerializer(objectMapper);

  @Test
  void reasoningContentWithProviderPayloadSurvivesSerializationRoundTrip() throws Exception {
    // Mirrors the Anthropic content converter's output: neutral text is null, the raw Anthropic
    // thinking block (type/thinking/signature) is the single source carried in providerPayload.
    final Map<String, Object> rawThinkingBlock = new LinkedHashMap<>();
    rawThinkingBlock.put("type", "thinking");
    rawThinkingBlock.put("thinking", "let me reason about ervin's email");
    rawThinkingBlock.put("signature", "EsoCCokBCA8YAipA-signature-blob");

    final var reasoning = new ReasoningContent(null, rawThinkingBlock, null);
    final var original =
        new DocumentContent(
            List.of(
                AssistantMessage.builder()
                    .content(List.of(reasoning, TextContent.textContent("the answer")))
                    .build()));

    final String json = serializer.writeDocumentContent(original);

    final Document document = mock(Document.class);
    when(document.asInputStream())
        .thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    final DocumentContent restored = serializer.readDocumentContent(document);

    // The whole message (and its content list) round-trips equal, and specifically the reasoning
    // block keeps null text plus a byte-identical providerPayload (signature included) — exactly
    // what must be re-sent to Anthropic verbatim.
    assertThat(restored.messages()).isEqualTo(original.messages());

    final var restoredAssistant = (AssistantMessage) restored.messages().get(0);
    final var restoredReasoning = (ReasoningContent) restoredAssistant.content().get(0);
    assertThat(restoredReasoning.text()).isNull();
    assertThat(restoredReasoning.providerPayload()).isEqualTo(rawThinkingBlock);
  }
}
