/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.error.ConnectorException;
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
  void externalDocumentDerivedIdStartsWithExtPrefixAndIsStable() {
    final var url = "https://example.com/report.pdf";
    final var doc1 = new ExternalDocument(url, "Report A", u -> null);
    final var doc2 = new ExternalDocument(url, "Report B", u -> null);

    final var id1 = DocumentHandle.idFor(doc1);
    final var id2 = DocumentHandle.idFor(doc2);

    assertThat(id1).startsWith("ext-").hasSize(16);
    assertThat(id1).isEqualTo(id2);
  }

  @Test
  void externalDocumentDifferentUrlsProduceDifferentIds() {
    final var doc1 = new ExternalDocument("https://example.com/a.pdf", null, u -> null);
    final var doc2 = new ExternalDocument("https://example.com/b.pdf", null, u -> null);

    assertThat(DocumentHandle.idFor(doc1)).isNotEqualTo(DocumentHandle.idFor(doc2));
  }

  @Test
  void externalDocumentRawUrlNeverAppearsInId() {
    final var url = "https://example.com/secret.pdf";
    final var doc = new ExternalDocument(url, "Secret", u -> null);

    assertThat(DocumentHandle.idFor(doc)).doesNotContain("example.com").doesNotContain("secret");
  }

  // ── Inline document ───────────────────────────────────────────────────────

  @Test
  void inlineDocumentDerivedIdStartsWithInlinePrefixAndIsStable() {
    final var content = "same content";
    final var doc1 = new InlineDocument(content, "a.txt", "text/plain");
    final var doc2 = new InlineDocument(content, "b.txt", "text/csv");

    final var id1 = DocumentHandle.idFor(doc1);

    assertThat(id1).startsWith("inline-").hasSize(19);
    // Same content → same id regardless of name/contentType metadata
    assertThat(id1).isEqualTo(DocumentHandle.idFor(doc2));
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

  // ── Blank-content inline document (unsupported) ─────────────────────────────

  @Test
  void inlineDocumentWithBlankContentThrowsConnectorException() {
    final var doc = new InlineDocument("   ", "file.txt", "text/plain");

    assertThatThrownBy(() -> DocumentHandle.idFor(doc))
        .isInstanceOf(ConnectorException.class)
        .hasFieldOrPropertyWithValue("errorCode", "FAILED_MODEL_CALL");
  }

  // ── Bedrock charset ───────────────────────────────────────────────────────

  @Test
  void everyProducedIdMatchesBedrockAllowedCharset() {
    final var camundaDoc =
        documentFactory.create(
            DocumentCreationRequest.from("hello".getBytes(StandardCharsets.UTF_8)).build());
    final var externalDoc =
        new ExternalDocument("https://example.com/report.pdf", "Report", u -> null);
    final var inlineDoc = new InlineDocument("inline content", "file.txt", "text/plain");

    for (final var id :
        new String[] {
          DocumentHandle.idFor(camundaDoc),
          DocumentHandle.idFor(externalDoc),
          DocumentHandle.idFor(inlineDoc)
        }) {
      assertThat(id).matches("[A-Za-z0-9 ()\\[\\]-]{1,200}");
    }
  }
}
