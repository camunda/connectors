/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
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
                    new IngestedDocument(first, DocumentSource.userMessage(null)),
                    new IngestedDocument(
                        second, DocumentSource.toolCall("search", "call-1", null))));

    assertThat(registry.entries())
        .extracting(DocumentRegistryEntry::id)
        .containsExactly(DocumentHandle.idFor(first), DocumentHandle.idFor(second));
    assertThat(registry.findById(DocumentHandle.idFor(second)))
        .hasValueSatisfying(
            entry -> {
              assertThat(DocumentHandle.fileNameFor(entry.reference())).isEqualTo("two.txt");
              assertThat(DocumentHandle.contentTypeFor(entry.reference())).isEqualTo("text/plain");
              assertThat(entry.source())
                  .isEqualTo(DocumentSource.toolCall("search", "call-1", null));
            });
  }

  @Test
  void firstRegistrationWins() {
    final var doc = document("one", "one.txt");

    final var registry =
        DocumentRegistry.empty()
            .withDocuments(
                List.of(
                    new IngestedDocument(doc, DocumentSource.toolCall("search", "call-1", null))))
            .withDocuments(
                List.of(
                    new IngestedDocument(doc, DocumentSource.toolCall("other", "call-2", null)),
                    new IngestedDocument(doc, DocumentSource.userMessage(null))));

    assertThat(registry.size()).isEqualTo(1);
    assertThat(registry.findById(DocumentHandle.idFor(doc)))
        .hasValueSatisfying(entry -> assertThat(entry.source().toolCallId()).isEqualTo("call-1"));
  }

  @Test
  void addingKnownDocumentsReturnsSameInstance() {
    final var doc = document("one", "one.txt");
    final var registry =
        DocumentRegistry.empty()
            .withDocuments(List.of(new IngestedDocument(doc, DocumentSource.userMessage(null))));

    assertThat(
            registry.withDocuments(
                List.of(new IngestedDocument(doc, DocumentSource.toolCall("t", "c", null)))))
        .isSameAs(registry);
    assertThat(registry.withDocuments(List.of())).isSameAs(registry);
  }

  @Test
  void ofDeduplicatesEntriesByIdKeepingTheFirst() {
    final var entry1 =
        new DocumentRegistryEntry("a", DocumentSource.userMessage(null), Map.of("k", "1"));
    final var entry2 =
        new DocumentRegistryEntry("a", DocumentSource.userMessage(null), Map.of("k", "2"));
    final var entry3 =
        new DocumentRegistryEntry("b", DocumentSource.userMessage(null), Map.of("k", "3"));

    assertThat(DocumentRegistry.of(List.of(entry1, entry2, entry3)).entries())
        .containsExactly(entry1, entry3);
  }
}
