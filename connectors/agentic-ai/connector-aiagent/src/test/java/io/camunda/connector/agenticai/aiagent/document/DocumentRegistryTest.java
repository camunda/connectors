/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_INLINE_DOCUMENT_TOO_LARGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.document.jackson.DocumentReferenceModel.InlineDocumentReferenceModel;
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

  private static Document documentWith(DocumentReference reference) {
    final var document = mock(Document.class);
    when(document.reference()).thenReturn(reference);
    return document;
  }

  @Test
  void emptyRegistryHasNoEntries() {
    final var registry = DocumentRegistry.empty();

    assertThat(registry.isEmpty()).isTrue();
    assertThat(registry.size()).isZero();
    assertThat(registry.entries()).isEmpty();
    assertThat(registry.findById(DocumentId.of("unknown"))).isEmpty();
  }

  @Test
  void keepsInsertionOrderAndLooksUpById() {
    final var first = document("one", "one.txt");
    final var second = document("two", "two.txt");

    final var registry =
        DocumentRegistries.withDocuments(
            DocumentRegistry.empty(),
            List.of(
                new IngestedDocument(first, DocumentSource.userMessage()),
                new IngestedDocument(second, DocumentSource.toolCall("search", "call-1"))));

    assertThat(registry.entries())
        .extracting(DocumentRegistryEntry::id)
        .containsExactly(DocumentHandle.idFor(first), DocumentHandle.idFor(second));
    assertThat(registry.findById(DocumentHandle.idFor(second)))
        .hasValueSatisfying(
            entry -> {
              assertThat(DocumentHandle.fileNameFor(entry.reference())).isEqualTo("two.txt");
              assertThat(DocumentHandle.contentTypeFor(entry.reference())).isEqualTo("text/plain");
              assertThat(entry.source()).isEqualTo(DocumentSource.toolCall("search", "call-1"));
            });
  }

  @Test
  void rejectsABatchWithAnInlineDocumentAboveTheLimit() {
    final var tooLarge =
        documentWith(
            new InlineDocumentReferenceModel(
                "x".repeat(DocumentRegistries.MAX_INLINE_CONTENT_LENGTH + 1),
                "big.txt",
                "text/plain"));
    final var batch =
        List.of(
            new IngestedDocument(document("one", "one.txt"), DocumentSource.userMessage()),
            new IngestedDocument(tooLarge, DocumentSource.userMessage()));

    assertThatThrownBy(() -> DocumentRegistries.withDocuments(DocumentRegistry.empty(), batch))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_INLINE_DOCUMENT_TOO_LARGE));
  }

  private static IngestedDocument inline(String content, String name) {
    return new IngestedDocument(
        documentWith(new InlineDocumentReferenceModel(content, name, "text/plain")),
        DocumentSource.userMessage());
  }

  @Test
  void firstRegistrationWins() {
    final var doc = document("one", "one.txt");

    final var registry =
        DocumentRegistries.withDocuments(
            DocumentRegistries.withDocuments(
                DocumentRegistry.empty(),
                List.of(new IngestedDocument(doc, DocumentSource.toolCall("search", "call-1")))),
            List.of(
                new IngestedDocument(doc, DocumentSource.toolCall("other", "call-2")),
                new IngestedDocument(doc, DocumentSource.userMessage())));

    assertThat(registry.size()).isEqualTo(1);
    assertThat(registry.findById(DocumentHandle.idFor(doc)))
        .hasValueSatisfying(entry -> assertThat(entry.source().toolCallId()).isEqualTo("call-1"));
  }

  @Test
  void addingKnownDocumentsReturnsSameInstance() {
    final var doc = document("one", "one.txt");
    final var registry =
        DocumentRegistries.withDocuments(
            DocumentRegistry.empty(),
            List.of(new IngestedDocument(doc, DocumentSource.userMessage())));

    assertThat(
            DocumentRegistries.withDocuments(
                registry, List.of(new IngestedDocument(doc, DocumentSource.toolCall("t", "c")))))
        .isSameAs(registry);
    assertThat(DocumentRegistries.withDocuments(registry, List.of())).isSameAs(registry);
  }

  @Test
  void ofDeduplicatesEntriesByIdKeepingTheFirst() {
    final var entry1 =
        new DocumentRegistryEntry(
            DocumentId.of("a"), DocumentSource.userMessage(), Map.of("k", "1"));
    final var entry2 =
        new DocumentRegistryEntry(
            DocumentId.of("a"), DocumentSource.userMessage(), Map.of("k", "2"));
    final var entry3 =
        new DocumentRegistryEntry(
            DocumentId.of("b"), DocumentSource.userMessage(), Map.of("k", "3"));

    assertThat(DocumentRegistry.of(List.of(entry1, entry2, entry3)).entries())
        .containsExactly(entry1, entry3);
  }
}
