/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.InlineDocumentReferenceModel;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.InlineDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentRegistryTest {

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  private final ObjectMapper objectMapper = new ObjectMapper();

  // ── empty ──────────────────────────────────────────────────────────────────

  @Test
  void emptyRegistryHasNoEntries() {
    assertThat(DocumentRegistry.empty().entries()).isEmpty();
  }

  // ── withAddedDocuments ────────────────────────────────────────────────────

  @Test
  void addsCamundaDocumentEntry() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName("data.txt")
                .build());

    final var registry = DocumentRegistry.empty().withAddedDocuments(List.of(doc));

    assertThat(registry.entries()).hasSize(1);
    final var entry = registry.entries().getFirst();
    assertThat(entry.id()).isEqualTo(DocumentHandle.idFor(doc));
    assertThat(entry.fileName()).isEqualTo("data.txt");
    assertThat(entry.contentType()).isEqualTo("text/plain");
    // reference must be serializable model type, never raw bytes
    assertThat(entry.reference()).isInstanceOf(CamundaDocumentReferenceModel.class);
  }

  @Test
  void deduplicatesByIdFirstEntryWins() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName("original.txt")
                .build());

    // withAddedDocuments twice with the same document
    final var registry =
        DocumentRegistry.empty().withAddedDocuments(List.of(doc)).withAddedDocuments(List.of(doc));

    assertThat(registry.entries()).hasSize(1);
    assertThat(registry.entries().getFirst().fileName()).isEqualTo("original.txt");
  }

  @Test
  void accumulatesDocumentsAcrossMultipleCalls() {
    final var doc1 =
        documentFactory.create(
            DocumentCreationRequest.from("a".getBytes(StandardCharsets.UTF_8))
                .fileName("a.txt")
                .build());
    final var doc2 =
        documentFactory.create(
            DocumentCreationRequest.from("b".getBytes(StandardCharsets.UTF_8))
                .fileName("b.txt")
                .build());

    final var registry =
        DocumentRegistry.empty()
            .withAddedDocuments(List.of(doc1))
            .withAddedDocuments(List.of(doc2));

    assertThat(registry.entries()).hasSize(2);
    assertThat(registry.entries().stream().map(DocumentRegistryEntry::id))
        .containsExactlyInAnyOrder(DocumentHandle.idFor(doc1), DocumentHandle.idFor(doc2));
  }

  @Test
  void noBytesStoredForInlineDocument() {
    final var doc = new InlineDocument("inline content", "inline.txt", "text/plain");

    final var registry = DocumentRegistry.empty().withAddedDocuments(List.of(doc));

    assertThat(registry.entries()).hasSize(1);
    final var ref = registry.entries().getFirst().reference();
    // InlineDocumentReferenceModel stores null content (bytes never persisted)
    assertThat(ref).isInstanceOf(InlineDocumentReferenceModel.class);
    assertThat(((InlineDocumentReferenceModel) ref).content()).isNull();
  }

  @Test
  void externalDocumentIdDoesNotContainRawUrl() {
    // Use a mock to avoid triggering the download lambda in ExternalDocument
    final var externalRef = mock(ExternalDocumentReference.class);
    when(externalRef.url()).thenReturn("https://secret.example.com/file.pdf");
    final var doc = mock(Document.class);
    when(doc.reference()).thenReturn(externalRef);
    when(doc.metadata()).thenReturn(null);

    final var registry = DocumentRegistry.empty().withAddedDocuments(List.of(doc));

    final var entry = registry.entries().getFirst();
    assertThat(entry.id()).startsWith("ext-").doesNotContain("secret.example.com");
  }

  // ── findById ──────────────────────────────────────────────────────────────

  @Test
  void findsExistingEntryById() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("hello".getBytes(StandardCharsets.UTF_8)).build());
    final var id = DocumentHandle.idFor(doc);

    final var registry = DocumentRegistry.empty().withAddedDocuments(List.of(doc));

    assertThat(registry.findById(id))
        .isPresent()
        .get()
        .extracting(DocumentRegistryEntry::id)
        .isEqualTo(id);
  }

  @Test
  void returnsEmptyForUnknownId() {
    final var registry =
        DocumentRegistry.empty()
            .withAddedDocuments(
                List.of(
                    documentFactory.create(
                        DocumentCreationRequest.from("x".getBytes(StandardCharsets.UTF_8))
                            .build())));

    assertThat(registry.findById("non-existent-id")).isEmpty();
  }

  // ── Jackson round-trip ────────────────────────────────────────────────────

  @Test
  void jacksonRoundTripPreservesEntries() throws Exception {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                .contentType("application/pdf")
                .fileName("report.pdf")
                .build());

    final var registry = DocumentRegistry.empty().withAddedDocuments(List.of(doc));

    final var serialized = objectMapper.writeValueAsString(registry);
    final var deserialized = objectMapper.readValue(serialized, DocumentRegistry.class);

    assertThat(deserialized.entries()).hasSize(1);
    final var entry = deserialized.entries().getFirst();
    assertThat(entry.id()).isEqualTo(registry.entries().getFirst().id());
    assertThat(entry.fileName()).isEqualTo("report.pdf");
    assertThat(entry.contentType()).isEqualTo("application/pdf");
  }

  @Test
  void jacksonRoundTripForEmptyRegistry() throws Exception {
    final var registry = DocumentRegistry.empty();
    final var serialized = objectMapper.writeValueAsString(registry);
    final var deserialized = objectMapper.readValue(serialized, DocumentRegistry.class);
    assertThat(deserialized.entries()).isEmpty();
  }
}
