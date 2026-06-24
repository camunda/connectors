/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.InlineDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DocumentHandleTest {

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  // ── Camunda document ──────────────────────────────────────────────────────

  @Test
  void camundaDocumentUsesDocumentId() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("hello".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName("hello.txt")
                .build());
    final var ref = (CamundaDocumentReference) doc.reference();

    assertThat(DocumentHandle.idFor(doc)).isEqualTo(ref.getDocumentId());
  }

  @Test
  void camundaDocumentIdIsStable() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("hello".getBytes(StandardCharsets.UTF_8)).build());

    assertThat(DocumentHandle.idFor(doc))
        .isEqualTo(DocumentHandle.idFor(doc))
        .isEqualTo(((CamundaDocumentReference) doc.reference()).getDocumentId());
  }

  // ── External document ─────────────────────────────────────────────────────

  @Test
  void externalDocumentDerivedIdStartsWithExtPrefix() {
    final var doc = new ExternalDocument("https://example.com/report.pdf", "Report", url -> null);

    assertThat(DocumentHandle.idFor(doc)).startsWith("ext-");
  }

  @Test
  void externalDocumentSameUrlProducesSameId() {
    final var url = "https://example.com/report.pdf";
    final var doc1 = new ExternalDocument(url, "Report A", url2 -> null);
    final var doc2 = new ExternalDocument(url, "Report B", url2 -> null);

    assertThat(DocumentHandle.idFor(doc1)).isEqualTo(DocumentHandle.idFor(doc2));
  }

  @Test
  void externalDocumentDifferentUrlsProduceDifferentIds() {
    final var doc1 = new ExternalDocument("https://example.com/a.pdf", null, url -> null);
    final var doc2 = new ExternalDocument("https://example.com/b.pdf", null, url -> null);

    assertThat(DocumentHandle.idFor(doc1)).isNotEqualTo(DocumentHandle.idFor(doc2));
  }

  @Test
  void externalDocumentRawUrlNeverAppearsInId() {
    final var url = "https://example.com/secret.pdf";
    final var doc = new ExternalDocument(url, "Secret", url2 -> null);

    assertThat(DocumentHandle.idFor(doc)).doesNotContain("example.com").doesNotContain("secret");
  }

  // ── Inline document ───────────────────────────────────────────────────────

  @Test
  void inlineDocumentDerivedIdStartsWithInlinePrefix() {
    final var doc = new InlineDocument("hello content", "file.txt", "text/plain");

    assertThat(DocumentHandle.idFor(doc)).startsWith("inline-");
  }

  @Test
  void inlineDocumentSameContentProducesSameId() {
    final var content = "same content";
    final var doc1 = new InlineDocument(content, "a.txt", "text/plain");
    final var doc2 = new InlineDocument(content, "b.txt", "text/csv");

    // Same content → same id regardless of name/contentType metadata
    assertThat(DocumentHandle.idFor(doc1)).isEqualTo(DocumentHandle.idFor(doc2));
  }

  @Test
  void inlineDocumentDifferentContentProducesDifferentId() {
    final var doc1 = new InlineDocument("content A", "file.txt", null);
    final var doc2 = new InlineDocument("content B", "file.txt", null);

    assertThat(DocumentHandle.idFor(doc1)).isNotEqualTo(DocumentHandle.idFor(doc2));
  }

  @Test
  void inlineDocumentIdIsStableAcrossMultipleCalls() {
    final var doc = new InlineDocument("stable content", "file.txt", "text/plain");
    final var id1 = DocumentHandle.idFor(doc);
    final var id2 = DocumentHandle.idFor(doc);
    final var id3 = DocumentHandle.idFor(doc);

    assertThat(id1).isEqualTo(id2).isEqualTo(id3);
  }

  // ── Null / unknown reference ───────────────────────────────────────────────

  @Test
  void nullDocumentReturnsRandomUuid() {
    final var id1 = DocumentHandle.idFor(null);
    final var id2 = DocumentHandle.idFor(null);

    // Both are valid UUIDs but not necessarily equal (random)
    assertThat(id1).isNotBlank();
    assertThat(id2).isNotBlank();
  }

  @Test
  void documentWithNullReferenceReturnsRandomUuid() {
    final var doc = mock(Document.class);
    when(doc.reference()).thenReturn(null);

    final var id = DocumentHandle.idFor(doc);
    assertThat(id).isNotBlank();
  }
}
