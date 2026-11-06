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
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.InlineDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AgentDocumentIdDerivationTest {

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  // ── Camunda document ──────────────────────────────────────────────────────

  @Test
  void camundaDocumentIdIsStableAndIgnoresTheContentHashAndTheMetadata() {
    final var first =
        documentFactory.create(
            DocumentCreationRequest.from("hello".getBytes(StandardCharsets.UTF_8))
                .documentId("doc-1")
                .fileName("a.txt")
                .build());
    final var second =
        documentFactory.create(
            DocumentCreationRequest.from("other".getBytes(StandardCharsets.UTF_8))
                .documentId("doc-1")
                .fileName("b.txt")
                .build());

    assertThat(AgentDocumentId.of(first)).isEqualTo(AgentDocumentId.of(second));
    assertThat(AgentDocumentId.of(first).value()).isEqualTo("doc_6d97d0f7b602");
  }

  @Test
  void camundaDocumentIdDoesNotExposeTheDocumentId() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("hello".getBytes(StandardCharsets.UTF_8)).build());
    final var ref = (CamundaDocumentReference) doc.reference();

    assertThat(AgentDocumentId.of(doc).value()).doesNotContain(ref.getDocumentId());
  }

  @Test
  void idsHaveThePrefixAndTwelveLowercaseHexCharacters() {
    final var camunda =
        documentFactory.create(
            DocumentCreationRequest.from("hello".getBytes(StandardCharsets.UTF_8)).build());

    assertThat(AgentDocumentId.of(camunda).value()).matches("doc_[0-9a-f]{12}");
    assertThat(
            AgentDocumentId.of(new ExternalDocument("https://example.com/a", null, u -> null))
                .value())
        .matches("doc_[0-9a-f]{12}");
    assertThat(AgentDocumentId.of(new InlineDocument("content", null, null)).value())
        .matches("doc_[0-9a-f]{12}");
  }

  // ── External document ─────────────────────────────────────────────────────

  @Test
  void externalDocumentIdIsStableAndIgnoresTheName() {
    final var url = "https://example.com/report.pdf";
    final var doc1 = new ExternalDocument(url, "Report A", u -> null);
    final var doc2 = new ExternalDocument(url, "Report B", u -> null);

    assertThat(AgentDocumentId.of(doc1)).isEqualTo(AgentDocumentId.of(doc2));
    assertThat(AgentDocumentId.of(doc1).value()).isEqualTo("doc_f089beca8872");
  }

  @Test
  void externalDocumentDifferentUrlsProduceDifferentIds() {
    final var doc1 = new ExternalDocument("https://example.com/a.pdf", null, u -> null);
    final var doc2 = new ExternalDocument("https://example.com/b.pdf", null, u -> null);

    assertThat(AgentDocumentId.of(doc1).value()).isNotEqualTo(AgentDocumentId.of(doc2).value());
  }

  @Test
  void externalDocumentRawUrlNeverAppearsInId() {
    final var url = "https://example.com/secret.pdf";
    final var doc = new ExternalDocument(url, "Secret", u -> null);

    assertThat(AgentDocumentId.of(doc).value())
        .doesNotContain("example.com")
        .doesNotContain("secret");
  }

  // ── Inline document ───────────────────────────────────────────────────────

  @Test
  void inlineDocumentIdIsStableAndIgnoresTheNameAndTheContentType() {
    final var doc1 = new InlineDocument("hello", "a.txt", "text/plain");
    final var doc2 = new InlineDocument("hello", "b.txt", "text/csv");

    assertThat(AgentDocumentId.of(doc1)).isEqualTo(AgentDocumentId.of(doc2));
    assertThat(AgentDocumentId.of(doc1).value()).isEqualTo("doc_735f6a9cc2fd");
  }

  @Test
  void inlineDocumentDifferentContentProducesDifferentId() {
    final var doc1 = new InlineDocument("content A", "file.txt", null);
    final var doc2 = new InlineDocument("content B", "file.txt", null);

    assertThat(AgentDocumentId.of(doc1).value()).isNotEqualTo(AgentDocumentId.of(doc2).value());
  }

  @Test
  void identityKeyPartsAreLengthPrefixedSoTheirBoundariesMatter() {
    final var camundaA = new CamundaDocumentReferenceModel("ab", "c", null, null);
    final var camundaB = new CamundaDocumentReferenceModel("a", "bc", null, null);

    assertThat(AgentDocumentId.identityKey(camundaA)).containsExactly("camunda", "ab", "c");
    assertThat(AgentDocumentId.of(camundaA)).isNotEqualTo(AgentDocumentId.of(camundaB));
  }

  @Test
  void aMissingStoreIdIsNotHashedAsTheTextNull() {
    final var withoutStore = new CamundaDocumentReferenceModel(null, "c", null, null);
    final var withStoreNamedNull = new CamundaDocumentReferenceModel("null", "c", null, null);

    assertThat(AgentDocumentId.identityKey(withoutStore)).containsExactly("camunda", null, "c");
    assertThat(AgentDocumentId.of(withoutStore))
        .isNotEqualTo(AgentDocumentId.of(withStoreNamedNull));
  }

  // ── Blank-content inline document (unsupported) ─────────────────────────────

  @Test
  void inlineDocumentWithBlankContentIsRejected() {
    final var doc = new InlineDocument("   ", "file.txt", "text/plain");

    assertThatThrownBy(() -> AgentDocumentId.of(doc))
        .isInstanceOf(UnsupportedDocumentReferenceException.class)
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unsupportedReferenceTypeIsRejected() {
    final var reference = new DocumentReference() {};

    assertThatThrownBy(() -> AgentDocumentId.of(reference))
        .isInstanceOf(UnsupportedDocumentReferenceException.class)
        .hasMessageContaining("Unsupported document reference type");
  }

  // ── Custom (non-UUID) document id ─────────────────────────────────────────

  @Test
  void camundaDocumentWithACustomIdGetsAnIdOfTheSameFormat() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("hello".getBytes(StandardCharsets.UTF_8))
                .documentId("custom:doc_id/with spaces")
                .build());

    assertThat(AgentDocumentId.of(doc).value()).matches("doc_[0-9a-f]{12}");
  }
}
