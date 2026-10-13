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

import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
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
  void rejectsABatchWithAnInlineDocumentAboveTheLimit() {
    final var tooLarge =
        documentWith(
            new InlineDocumentReferenceModel(
                "x".repeat(DocumentRegistryEntry.MAX_INLINE_CONTENT_LENGTH + 1),
                "big.txt",
                "text/plain"));
    final var batch =
        List.of(
            new IngestedDocument(document("one", "one.txt"), DocumentSource.userMessage(null)),
            new IngestedDocument(tooLarge, DocumentSource.userMessage(null)));

    assertThatThrownBy(() -> DocumentRegistry.empty().withDocuments(batch))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_INLINE_DOCUMENT_TOO_LARGE));
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

  private static DocumentRegistry registryOfEntries(int count) {
    return DocumentRegistry.of(
        java.util.stream.IntStream.rangeClosed(1, count)
            .mapToObj(
                i ->
                    new DocumentRegistryEntry(
                        "doc-" + i, DocumentSource.userMessage(null), Map.of("k", "" + i)))
            .toList());
  }

  @Test
  void theKnownDocumentsErrorIsTheProblemsTheReferencesInRegistryOrderAndNoCount() {
    final var registry = registryOfEntries(3);

    final var result = registry.knownDocumentsError("Parameter 'p' is wrong.");

    assertThat(result.error()).isTrue();
    assertThat(result.contentBlocks())
        .containsExactly(
            TextContent.textContent("Parameter 'p' is wrong. Known documents:"),
            DocumentReferenceContent.from(registry.entries().get(0), DocumentDelivery.REFERENCE),
            DocumentReferenceContent.from(registry.entries().get(1), DocumentDelivery.REFERENCE),
            DocumentReferenceContent.from(registry.entries().get(2), DocumentDelivery.REFERENCE));
  }

  @Test
  void theKnownDocumentsErrorListsAtMostFiftyReferencesAndCountsTheRest() {
    final var registry = registryOfEntries(52);

    final var blocks = registry.knownDocumentsError("Parameter 'p' is wrong.").contentBlocks();

    assertThat(blocks).hasSize(52);
    assertThat(blocks.getFirst())
        .isEqualTo(TextContent.textContent("Parameter 'p' is wrong. Known documents:"));
    assertThat(blocks.subList(1, 51))
        .extracting(block -> ((DocumentReferenceContent) block).id())
        .containsExactlyElementsOf(
            registry.entries().stream().limit(50).map(DocumentRegistryEntry::id).toList());
    assertThat(blocks.getLast()).isEqualTo(TextContent.textContent("(and 2 more)"));
  }

  @Test
  void theKnownDocumentsErrorOfAnEmptyRegistryIsOneText() {
    final var result = DocumentRegistry.empty().knownDocumentsError("Parameter 'p' is wrong.");

    assertThat(result.error()).isTrue();
    assertThat(result.contentBlocks()).isNull();
    assertThat(result.content()).isEqualTo("Parameter 'p' is wrong. No documents are known.");
  }
}
