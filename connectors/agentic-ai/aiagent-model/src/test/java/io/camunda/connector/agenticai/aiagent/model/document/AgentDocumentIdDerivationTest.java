/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.InlineDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
  }

  @Test
  void theStoreIdAndTheDocumentIdAreLengthPrefixedSoTheirBoundariesMatter() {
    final var camundaA = new CamundaDocumentReferenceModel("ab", "c", null, null);
    final var camundaB = new CamundaDocumentReferenceModel("a", "bc", null, null);

    assertThat(AgentDocumentId.of(camundaA)).isNotEqualTo(AgentDocumentId.of(camundaB));
  }

  @Test
  void aMissingStoreIdIsNotHashedAsTheTextNull() {
    final var withoutStore = new CamundaDocumentReferenceModel(null, "c", null, null);
    final var withStoreNamedNull = new CamundaDocumentReferenceModel("null", "c", null, null);

    assertThat(AgentDocumentId.of(withoutStore))
        .isNotEqualTo(AgentDocumentId.of(withStoreNamedNull));
  }

  // ── Documents that are not Camunda documents ───────────────────────────────

  @Test
  void externalAndInlineDocumentsGetNoId() {
    final var documents =
        List.<Document>of(
            new ExternalDocument("https://example.com/report.pdf", "Report", u -> null),
            new InlineDocument("hello", "a.txt", "text/plain"));

    for (final var document : documents) {
      assertThatThrownBy(() -> AgentDocumentId.of(document))
          .isInstanceOf(UnsupportedDocumentReferenceException.class)
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> AgentDocumentId.of(document.reference()))
          .isInstanceOf(UnsupportedDocumentReferenceException.class);
    }
  }

  @Test
  void theMessageOfAnUnsupportedReferenceNeverHoldsItsContent() {
    assertThatThrownBy(
            () -> AgentDocumentId.of(new InlineDocument("secret content", "a.txt", null)))
        .hasMessage(
            "Only Camunda documents are supported, but the document reference is not a Camunda document reference.");
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
