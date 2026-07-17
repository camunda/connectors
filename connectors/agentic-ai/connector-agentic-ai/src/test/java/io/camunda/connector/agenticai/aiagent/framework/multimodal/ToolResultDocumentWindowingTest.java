/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.multimodal;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALLS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.framework.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.memory.runtime.MessageWindowFilter;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies the "Memory window / eviction" contract from the C5 plan against the real {@link
 * MessageWindowFilter}: a tool-result document evicts atomically with its originating turn, and
 * carrying a document never changes a {@link ToolCallResultMessage}'s window-accounting weight (1
 * slot, same as without a document) — the strategy only ever sees the already-windowed snapshot.
 */
class ToolResultDocumentWindowingTest {

  // capabilities with no tool-result modalities: every tool-result document takes the fallback
  private static final ModelCapabilities BRIDGE_CAPS =
      new CoreModelCapabilities(
          List.of(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT),
          List.of(Modality.TEXT),
          List.of(Modality.TEXT),
          null,
          null);

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
  private final ToolCallResultStrategy strategy = new CapabilityAwareToolCallResultStrategy();

  private Document doc(String fileName) {
    return documentFactory.create(
        DocumentCreationRequest.from("bytes".getBytes(StandardCharsets.UTF_8))
            .contentType("application/pdf")
            .fileName(fileName)
            .build());
  }

  /**
   * Self-describing tool-result message (composer shape): from(raw) content + a DocumentContent.
   */
  private ToolCallResultMessage toolResultWithDoc(String id, Document document) {
    final var base =
        ToolCallResultContent.from(
            ToolCallResult.builder().id(id).name("getReport").content(Map.of("k", "v")).build());
    final var content = new ArrayList<>(base.content());
    content.add(DocumentContent.documentContent(document));
    return ToolCallResultMessage.builder().results(List.of(base.withContent(content))).build();
  }

  /** Same shape but without any document — used to assert window-accounting parity. */
  private ToolCallResultMessage toolResultWithoutDoc(String id) {
    final var base =
        ToolCallResultContent.from(
            ToolCallResult.builder().id(id).name("getReport").content(Map.of("k", "v")).build());
    return ToolCallResultMessage.builder().results(List.of(base)).build();
  }

  @Test
  void documentSurvivesWhileItsTurnIsInWindow() {
    var doc1 = doc("turn1.pdf");
    var doc2 = doc("turn2.pdf");

    List<Message> messages =
        List.of(
            userMessage("Hi"),
            assistantMessage("thinking turn 1", TOOL_CALLS), // hasToolCalls
            toolResultWithDoc("call_1", doc1),
            assistantMessage("thinking turn 2", TOOL_CALLS), // hasToolCalls
            toolResultWithDoc("call_2", doc2),
            assistantMessage("Done"));

    // window large enough to keep everything
    var windowed = MessageWindowFilter.apply(messages, messages.size());
    assertThat(windowed).hasSize(messages.size());

    var sent =
        strategy
            .routeToolResults(new ConversationSnapshot(windowed, List.of()), BRIDGE_CAPS)
            .messages();

    // both turns' documents survive, each rendered as its own trailing synthetic message
    assertThat(sent).hasSize(messages.size() + 2);
    var synthetics =
        sent.stream()
            .filter(UserMessage.class::isInstance)
            .map(UserMessage.class::cast)
            .filter(
                um ->
                    um.metadata() != null
                        && Boolean.TRUE.equals(
                            um.metadata().get(UserMessage.METADATA_TOOL_CALL_DOCUMENTS)))
            .toList();
    assertThat(synthetics).hasSize(2);
    assertThat(synthetics.get(0).content()).contains(DocumentContent.documentContent(doc1));
    assertThat(synthetics.get(1).content()).contains(DocumentContent.documentContent(doc2));
  }

  @Test
  void documentIsGoneOnceItsTurnIsEvicted_noOrphanToolResult() {
    var doc1 = doc("turn1.pdf");
    var doc2 = doc("turn2.pdf");

    List<Message> messages =
        List.of(
            userMessage("Hi"),
            assistantMessage("thinking turn 1", TOOL_CALLS), // hasToolCalls
            toolResultWithDoc("call_1", doc1),
            assistantMessage("thinking turn 2", TOOL_CALLS), // hasToolCalls
            toolResultWithDoc("call_2", doc2),
            assistantMessage("Done"));

    // small enough window to force evicting turn 1's assistant message + tool call result
    var windowed = MessageWindowFilter.apply(messages, 4);

    // turn 1's assistant message and tool-result message are gone together (no orphan tool
    // result left behind); turn 2 and the final assistant message survive
    assertThat(windowed)
        .doesNotContain(messages.get(1)) // turn 1 assistant message (hasToolCalls)
        .doesNotContain(messages.get(2)) // turn 1 tool call result (would-be orphan)
        .contains(messages.get(3), messages.get(4), messages.get(5));

    var sent =
        strategy
            .routeToolResults(new ConversationSnapshot(windowed, List.of()), BRIDGE_CAPS)
            .messages();

    // only turn 2's document renders
    var syntheticDocs =
        sent.stream()
            .filter(UserMessage.class::isInstance)
            .map(UserMessage.class::cast)
            .filter(
                um ->
                    um.metadata() != null
                        && Boolean.TRUE.equals(
                            um.metadata().get(UserMessage.METADATA_TOOL_CALL_DOCUMENTS)))
            .flatMap(um -> um.content().stream())
            .toList();
    assertThat(syntheticDocs)
        .contains(DocumentContent.documentContent(doc2))
        .doesNotContain(DocumentContent.documentContent(doc1));
  }

  @Test
  void carryingADocumentDoesNotChangeWindowAccountingWeight() {
    // identical structure, only difference is whether the tool call results carry a document —
    // a ToolCallResultMessage is not an isToolCallDocumentMessage, so it always counts as exactly
    // 1 window slot regardless of carried content
    List<Message> withDocs =
        List.of(
            userMessage("Hi"),
            assistantMessage("thinking turn 1", TOOL_CALLS),
            toolResultWithDoc("call_1", doc("turn1.pdf")),
            assistantMessage("thinking turn 2", TOOL_CALLS),
            toolResultWithDoc("call_2", doc("turn2.pdf")),
            assistantMessage("Done"));
    List<Message> withoutDocs =
        List.of(
            userMessage("Hi"),
            assistantMessage("thinking turn 1", TOOL_CALLS),
            toolResultWithoutDoc("call_1"),
            assistantMessage("thinking turn 2", TOOL_CALLS),
            toolResultWithoutDoc("call_2"),
            assistantMessage("Done"));

    for (int maxMessages = 1; maxMessages <= withDocs.size(); maxMessages++) {
      var windowedWithDocs = MessageWindowFilter.apply(withDocs, maxMessages);
      var windowedWithoutDocs = MessageWindowFilter.apply(withoutDocs, maxMessages);
      assertThat(windowedWithDocs).hasSameSizeAs(windowedWithoutDocs);
    }
  }
}
