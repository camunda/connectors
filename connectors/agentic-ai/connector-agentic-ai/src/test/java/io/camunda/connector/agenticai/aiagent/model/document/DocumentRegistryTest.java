/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.toolCallResultMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentRegistryTest {

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  private Document document(String content, String fileName) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType("text/plain")
            .fileName(fileName)
            .build());
  }

  @Test
  void emptyRegistryHasNoEntries() {
    final var registry = DocumentRegistry.empty();

    assertThat(registry.isEmpty()).isTrue();
    assertThat(registry.size()).isZero();
    assertThat(registry.entries()).isEmpty();
    assertThat(registry.findById("unknown")).isEmpty();
  }

  @Test
  void keepsInsertionOrderAndLooksUpById() {
    final var first = document("one", "one.txt");
    final var second = document("two", "two.txt");

    final var registry =
        DocumentRegistry.empty()
            .withDocuments(
                List.of(
                    new IngestedDocument(first, null, null),
                    new IngestedDocument(second, "call-1", "search")));

    assertThat(registry.entries())
        .extracting(DocumentRegistryEntry::id)
        .containsExactly(DocumentHandle.idFor(first), DocumentHandle.idFor(second));
    assertThat(registry.findById(DocumentHandle.idFor(second)))
        .hasValueSatisfying(
            entry -> {
              assertThat(entry.fileName()).isEqualTo("two.txt");
              assertThat(entry.contentType()).isEqualTo("text/plain");
              assertThat(entry.toolName()).isEqualTo("search");
              assertThat(entry.toolCallId()).isEqualTo("call-1");
            });
  }

  @Test
  void firstRegistrationWins() {
    final var doc = document("one", "one.txt");

    final var registry =
        DocumentRegistry.empty()
            .withDocuments(List.of(new IngestedDocument(doc, "call-1", "search")))
            .withDocuments(
                List.of(
                    new IngestedDocument(doc, "call-2", "other"),
                    new IngestedDocument(doc, null, null)));

    assertThat(registry.size()).isEqualTo(1);
    assertThat(registry.findById(DocumentHandle.idFor(doc)))
        .hasValueSatisfying(entry -> assertThat(entry.toolCallId()).isEqualTo("call-1"));
  }

  @Test
  void addingKnownDocumentsReturnsSameInstance() {
    final var doc = document("one", "one.txt");
    final var registry =
        DocumentRegistry.empty().withDocuments(List.of(new IngestedDocument(doc, null, null)));

    assertThat(registry.withDocuments(List.of(new IngestedDocument(doc, "c", "t"))))
        .isSameAs(registry);
    assertThat(registry.withDocuments(List.of())).isSameAs(registry);
  }

  @Test
  void ofDeduplicatesEntriesByIdKeepingTheFirst() {
    final var entry1 = new DocumentRegistryEntry("a", Map.of("k", "1"), null, null, null, null);
    final var entry2 = new DocumentRegistryEntry("a", Map.of("k", "2"), null, null, null, null);
    final var entry3 = new DocumentRegistryEntry("b", Map.of("k", "3"), null, null, null, null);

    assertThat(DocumentRegistry.of(List.of(entry1, entry2, entry3)).entries())
        .containsExactly(entry1, entry3);
  }

  @Test
  void fromMessagesCollectsUserAndNestedToolResultDocuments() {
    final var userDoc = document("user", "user.txt");
    final var toolDoc = document("tool", "tool.txt");
    final var nestedDoc = document("nested", "nested.txt");

    final var messages =
        List.<Message>of(
            userMessage(
                List.of(TextContent.textContent("hi"), DocumentContent.documentContent(userDoc))),
            assistantMessage("calling"),
            toolCallResultMessage(
                List.of(
                    ToolCallResult.builder()
                        .id("call-1")
                        .name("search")
                        .content(
                            new LinkedHashMap<>(
                                Map.of("result", List.of(toolDoc, Map.of("d", nestedDoc)))))
                        .build())),
            // the synthetic document message repeats the tool result documents without origin
            userMessage(List.of(DocumentContent.documentContent(toolDoc))));

    final var registry = DocumentRegistry.fromMessages(messages);

    assertThat(registry.entries())
        .extracting(DocumentRegistryEntry::id, DocumentRegistryEntry::toolCallId)
        .containsExactly(
            tuple(DocumentHandle.idFor(userDoc), null),
            tuple(DocumentHandle.idFor(toolDoc), "call-1"),
            tuple(DocumentHandle.idFor(nestedDoc), "call-1"));
  }
}
