/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_INLINE_DOCUMENTS_TOO_LARGE;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_INLINE_DOCUMENT_TOO_LARGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
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
  void acceptsInlineDocumentsUpToTheTotalLimit() {
    final var half = DocumentRegistries.MAX_TOTAL_INLINE_CONTENT_LENGTH / 2;

    final var registry =
        DocumentRegistries.withDocuments(
            DocumentRegistries.withDocuments(
                DocumentRegistry.empty(), List.of(inline("a".repeat(half), "a.txt"))),
            List.of(inline("b".repeat(half), "b.txt")));

    assertThat(registry.size()).isEqualTo(2);
  }

  @Test
  void rejectsInlineDocumentsAboveTheTotalLimitOfOneBatch() {
    final var batch =
        List.of(inline("a".repeat(600 * 1024), "a.txt"), inline("b".repeat(600 * 1024), "b.txt"));

    assertThatThrownBy(() -> DocumentRegistries.withDocuments(DocumentRegistry.empty(), batch))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_INLINE_DOCUMENTS_TOO_LARGE);
              assertThat(e.getMessage())
                  .contains("1228800 characters in total", "limit is 1048576")
                  .contains("Camunda document");
            });
  }

  @Test
  void countsTheInlineDocumentsTheRegistryHoldsAlready() {
    final var registry =
        DocumentRegistries.withDocuments(
            DocumentRegistry.empty(), List.of(inline("a".repeat(600 * 1024), "a.txt")));
    final var next = List.of(inline("b".repeat(600 * 1024), "b.txt"));

    assertThatThrownBy(() -> DocumentRegistries.withDocuments(registry, next))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_INLINE_DOCUMENTS_TOO_LARGE));
  }

  @Test
  void theTotalLimitHoldsForTheRegistryOfAnAgentContextAndLeavesItUntouchedWhenItFails() {
    final var context =
        DocumentRegistries.register(
            AgentContext.empty(), List.of(inline("a".repeat(600 * 1024), "a.txt")));
    final var next = List.of(inline("b".repeat(600 * 1024), "b.txt"));

    assertThatThrownBy(() -> DocumentRegistries.register(context, next))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_INLINE_DOCUMENTS_TOO_LARGE));
    assertThat(context.documentRegistry().size()).isEqualTo(1);
  }

  @Test
  void theOrderOfTheDocumentsOfABatchDoesNotChangeWhetherTheTotalLimitIsExceeded() {
    final var small = inline("a".repeat(100 * 1024), "a.txt");
    final var large = inline("b".repeat(1000 * 1024), "b.txt");

    assertThatThrownBy(
            () -> DocumentRegistries.withDocuments(DocumentRegistry.empty(), List.of(small, large)))
        .isInstanceOf(ConnectorException.class);
    assertThatThrownBy(
            () -> DocumentRegistries.withDocuments(DocumentRegistry.empty(), List.of(large, small)))
        .isInstanceOf(ConnectorException.class);
  }

  @Test
  void aDocumentTheRegistryKnowsAlreadyDoesNotCountAgain() {
    final var big = inline("a".repeat(DocumentRegistries.MAX_TOTAL_INLINE_CONTENT_LENGTH), "a.txt");
    final var registry = DocumentRegistries.withDocuments(DocumentRegistry.empty(), List.of(big));

    assertThat(DocumentRegistries.withDocuments(registry, List.of(big))).isSameAs(registry);
  }

  @Test
  void camundaDocumentsDoNotCountForTheTotalOfInlineContent() {
    final var registry =
        DocumentRegistries.withDocuments(
            DocumentRegistry.empty(),
            List.of(
                inline("a".repeat(DocumentRegistries.MAX_TOTAL_INLINE_CONTENT_LENGTH), "a.txt")));

    final var withCamunda =
        DocumentRegistries.withDocuments(
            registry,
            List.of(
                new IngestedDocument(
                    document("x".repeat(2 * 1024 * 1024), "large.txt"),
                    DocumentSource.userMessage())));

    assertThat(withCamunda.size()).isEqualTo(2);
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
