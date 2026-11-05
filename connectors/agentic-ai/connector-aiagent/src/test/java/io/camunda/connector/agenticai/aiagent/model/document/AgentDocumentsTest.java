/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments.NewDocument;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.document.jackson.DocumentReferenceModel.InlineDocumentReferenceModel;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentDocumentsTest {

  private final ObjectMapper objectMapper = TestObjectMapperSupplier.INSTANCE;
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

  private static Document inline(String content, String name) {
    return documentWith(new InlineDocumentReferenceModel(content, name, "text/plain"));
  }

  @Test
  void emptyHasNoDocuments() {
    final var documents = AgentDocuments.empty();

    assertThat(documents.all()).isEmpty();
    assertThat(documents.find(AgentDocumentId.of("unknown"))).isEmpty();
  }

  @Test
  void keepsRegistrationOrderAndLooksUpById() {
    final var first = document("one", "one.txt");
    final var second = document("two", "two.txt");

    final var documents =
        AgentDocuments.empty()
            .register(
                List.of(
                    new NewDocument(first, DocumentOrigin.userMessage()),
                    new NewDocument(second, DocumentOrigin.toolCall("search", "call-1"))));

    assertThat(documents.all())
        .extracting(AgentDocument::id)
        .containsExactly(AgentDocumentId.of(first), AgentDocumentId.of(second));
    assertThat(documents.find(AgentDocumentId.of(second)))
        .hasValueSatisfying(found -> {
          assertThat(found.fileName()).isEqualTo("two.txt");
          assertThat(found.contentType()).isEqualTo("text/plain");
          assertThat(found.origin()).isEqualTo(DocumentOrigin.toolCall("search", "call-1"));
        });
  }

  @Test
  void registeringASingleDocumentAddsItLast() {
    final var first = document("one", "one.txt");
    final var second = document("two", "two.txt");

    final var documents =
        AgentDocuments.empty()
            .register(first, DocumentOrigin.userMessage())
            .register(second, DocumentOrigin.event());

    assertThat(documents.all())
        .extracting(AgentDocument::id)
        .containsExactly(AgentDocumentId.of(first), AgentDocumentId.of(second));
  }

  @Test
  void rejectsABatchWithAnInlineDocumentAboveTheLimit() {
    final var batch =
        List.of(
            new NewDocument(document("one", "one.txt"), DocumentOrigin.userMessage()),
            new NewDocument(
                inline("x".repeat(AgentDocuments.MAX_INLINE_CONTENT_LENGTH + 1), "big.png"),
                DocumentOrigin.userMessage()));

    assertThatThrownBy(() -> AgentDocuments.empty().register(batch))
        .isInstanceOf(InlineDocumentTooLargeException.class)
        .hasMessageContaining("'big.png'")
        .hasMessageContaining("1048577 characters")
        .hasMessageContaining("limit is 1048576")
        .hasMessageContaining("Camunda document");
  }

  @Test
  void anInlineDocumentWithTheLimitAsContentLengthIsAccepted() {
    final var content = "x".repeat(AgentDocuments.MAX_INLINE_CONTENT_LENGTH);

    final var documents =
        AgentDocuments.empty().register(inline(content, "limit.txt"), DocumentOrigin.userMessage());

    assertThat(documents.all()).hasSize(1);
  }

  @Test
  void anInlineDocumentAboveTheLimitWithoutANameIsRejectedWithItsId() {
    final var tooLarge = inline("x".repeat(AgentDocuments.MAX_INLINE_CONTENT_LENGTH + 1), null);

    assertThatThrownBy(() -> AgentDocuments.empty().register(tooLarge, DocumentOrigin.event()))
        .isInstanceOf(InlineDocumentTooLargeException.class)
        .hasMessageContaining("'" + AgentDocumentId.of(tooLarge) + "'");
  }

  @Test
  void camundaAndExternalDocumentsAreAcceptedWhateverTheirSize() {
    final var camunda =
        documentFactory.create(
            DocumentCreationRequest.from(new byte[2 * AgentDocuments.MAX_INLINE_CONTENT_LENGTH])
                .contentType("application/octet-stream")
                .fileName("large.bin")
                .build());
    final var external =
        documentWith(
            new io.camunda.connector.document.jackson.DocumentReferenceModel
                .ExternalDocumentReferenceModel(
                "https://example.com/" + "a".repeat(2 * AgentDocuments.MAX_INLINE_CONTENT_LENGTH),
                "large.bin"));

    final var documents =
        AgentDocuments.empty()
            .register(camunda, DocumentOrigin.event())
            .register(external, DocumentOrigin.event());

    assertThat(documents.all())
        .extracting(AgentDocument::id)
        .containsExactly(AgentDocumentId.of(camunda), AgentDocumentId.of(external));
  }

  @Test
  void firstRegistrationWins() {
    final var doc = document("one", "one.txt");

    final var documents =
        AgentDocuments.empty()
            .register(doc, DocumentOrigin.toolCall("search", "call-1"))
            .register(
                List.of(
                    new NewDocument(doc, DocumentOrigin.toolCall("other", "call-2")),
                    new NewDocument(doc, DocumentOrigin.userMessage())));

    assertThat(documents.all()).hasSize(1);
    assertThat(documents.find(AgentDocumentId.of(doc)))
        .hasValueSatisfying(found ->
            assertThat(found.origin()).isEqualTo(DocumentOrigin.toolCall("search", "call-1")));
  }

  @Test
  void registeringKnownDocumentsReturnsTheSameInstance() {
    final var doc = document("one", "one.txt");
    final var documents = AgentDocuments.empty().register(doc, DocumentOrigin.userMessage());

    assertThat(documents.register(doc, DocumentOrigin.toolCall("t", "c"))).isSameAs(documents);
    assertThat(documents.register(List.of())).isSameAs(documents);
  }

  @Test
  void aDocumentWithTheIdOfADifferentDocumentTheCollectionHoldsIsRejected() {
    final var registered = document("one", "one.txt");
    final var other = document("two", "two.txt");
    // the collection holds `registered` under the id `other` gets
    final var documents =
        AgentDocuments.of(
            List.of(
                new AgentDocument(
                    AgentDocumentId.of(other),
                    DocumentOrigin.userMessage(),
                    AgentDocument.of(registered, DocumentOrigin.userMessage()).reference())));

    assertThatThrownBy(() -> documents.register(other, DocumentOrigin.toolCall("t", "c")))
        .isInstanceOf(DocumentIdCollisionException.class)
        .hasMessageContaining("'" + AgentDocumentId.of(other) + "'")
        .hasMessageContaining("two different documents");
  }

  @Test
  void twoDifferentDocumentsOfOneBatchWithTheSameIdAreRejected() {
    final var first = document("one", "one.txt");
    final var second = document("two", "two.txt");

    // a real id collision needs a hash collision, so the id of the second document is forced
    try (var ids = mockStatic(AgentDocumentId.class, CALLS_REAL_METHODS)) {
      final var firstId = AgentDocumentId.of(first);
      ids.when(() -> AgentDocumentId.of(second)).thenReturn(firstId);

      assertThatThrownBy(() ->
              AgentDocuments.empty()
                  .register(
                      List.of(
                          new NewDocument(first, DocumentOrigin.userMessage()),
                          new NewDocument(second, DocumentOrigin.userMessage()))))
          .isInstanceOf(DocumentIdCollisionException.class)
          .hasMessageContaining("'" + firstId + "'");
    }
  }

  @Test
  void ofKeepsTheFirstDocumentOfAnId() {
    final var one = AgentDocument.of(document("one", "one.txt"), DocumentOrigin.userMessage());
    final var duplicate =
        new AgentDocument(
            one.id(),
            DocumentOrigin.event(),
            AgentDocument.of(document("two", "two.txt"), DocumentOrigin.event()).reference());
    final var other =
        AgentDocument.of(document("three", "three.txt"), DocumentOrigin.userMessage());

    assertThat(AgentDocuments.of(List.of(one, duplicate, other)).all()).containsExactly(one, other);
  }

  @Test
  void hasValueEquality() {
    final var doc = document("one", "one.txt");

    assertThat(AgentDocuments.empty().register(doc, DocumentOrigin.userMessage()))
        .isEqualTo(AgentDocuments.empty().register(doc, DocumentOrigin.userMessage()))
        .hasSameHashCodeAs(AgentDocuments.empty().register(doc, DocumentOrigin.userMessage()));
    assertThat(AgentDocuments.empty().register(doc, DocumentOrigin.userMessage()))
        .isNotEqualTo(AgentDocuments.empty());
  }

  @Test
  void roundTripsThroughJsonAsTheListOfItsDocuments() throws JsonProcessingException {
    final var documents =
        AgentDocuments.empty()
            .register(document("one", "one.txt"), DocumentOrigin.userMessage())
            .register(inline("aGVsbG8=", "a.txt"), DocumentOrigin.toolCall("search", "call-1"));

    final var json = objectMapper.writeValueAsString(documents);

    assertThat(objectMapper.readTree(json).isArray()).isTrue();
    assertThat(objectMapper.readValue(json, AgentDocuments.class)).isEqualTo(documents);
  }
}
