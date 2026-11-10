/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.document.jackson.DocumentReferenceModel.ExternalDocumentReferenceModel;
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
                    AgentDocument.of(
                        first, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID)),
                    AgentDocument.of(second, AgentDocumentOrigin.toolCall("search", "call-1"))));

    assertThat(documents.all())
        .extracting(AgentDocument::id)
        .containsExactly(AgentDocumentId.of(first), AgentDocumentId.of(second));
    assertThat(documents.find(AgentDocumentId.of(second)))
        .hasValueSatisfying(found -> {
          assertThat(found.fileName()).isEqualTo("two.txt");
          assertThat(found.contentType()).isEqualTo("text/plain");
          assertThat(found.origin()).isEqualTo(AgentDocumentOrigin.toolCall("search", "call-1"));
        });
  }

  @Test
  void registeringASingleDocumentAddsItLast() {
    final var first = document("one", "one.txt");
    final var second = document("two", "two.txt");

    final var documents =
        AgentDocuments.empty()
            .register(first, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))
            .register(second, AgentDocumentOrigin.event(TestDocuments.MESSAGE_ID));

    assertThat(documents.all())
        .extracting(AgentDocument::id)
        .containsExactly(AgentDocumentId.of(first), AgentDocumentId.of(second));
  }

  @Test
  void rejectsADocumentThatIsNotACamundaDocument() {
    final var origin = AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID);

    assertThatThrownBy(() -> AgentDocuments.empty().register(inline("hello", "a.txt"), origin))
        .isInstanceOf(UnsupportedDocumentReferenceException.class);
    assertThatThrownBy(() ->
            AgentDocuments.empty()
                .register(
                    documentWith(new ExternalDocumentReferenceModel("https://example.com/a", "a")),
                    origin))
        .isInstanceOf(UnsupportedDocumentReferenceException.class);
  }

  @Test
  void firstRegistrationWins() {
    final var doc = document("one", "one.txt");

    final var documents =
        AgentDocuments.empty()
            .register(doc, AgentDocumentOrigin.toolCall("search", "call-1"))
            .register(
                List.of(
                    AgentDocument.of(doc, AgentDocumentOrigin.toolCall("other", "call-2")),
                    AgentDocument.of(
                        doc, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))));

    assertThat(documents.all()).hasSize(1);
    assertThat(documents.find(AgentDocumentId.of(doc)))
        .hasValueSatisfying(found ->
            assertThat(found.origin()).isEqualTo(AgentDocumentOrigin.toolCall("search", "call-1")));
  }

  @Test
  void registeringKnownDocumentsReturnsTheSameInstance() {
    final var doc = document("one", "one.txt");
    final var documents =
        AgentDocuments.empty()
            .register(doc, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID));

    assertThat(documents.register(doc, AgentDocumentOrigin.toolCall("t", "c"))).isSameAs(documents);
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
                    AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID),
                    AgentDocument.of(
                            registered, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))
                        .reference())));

    assertThatThrownBy(() -> documents.register(other, AgentDocumentOrigin.toolCall("t", "c")))
        .isInstanceOf(AgentDocumentIdCollisionException.class)
        .hasMessageContaining("'" + AgentDocumentId.of(other) + "'")
        .hasMessageContaining("two different documents");
  }

  @Test
  void twoDifferentDocumentsOfOneBatchWithTheSameIdAreRejected() {
    final var first = document("one", "one.txt");
    final var second = document("two", "two.txt");

    // a real id collision needs a hash collision, so the second document gets the id of the first
    final var origin = AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID);
    final var firstDocument = AgentDocument.of(first, origin);
    final var secondWithTheFirstId =
        new AgentDocument(firstDocument.id(), origin, AgentDocument.of(second, origin).reference());

    assertThatThrownBy(
            () -> AgentDocuments.empty().register(List.of(firstDocument, secondWithTheFirstId)))
        .isInstanceOf(AgentDocumentIdCollisionException.class)
        .hasMessageContaining("'" + firstDocument.id() + "'");
  }

  @Test
  void ofKeepsTheFirstDocumentOfAnId() {
    final var one =
        AgentDocument.of(
            document("one", "one.txt"), AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID));
    final var duplicate =
        new AgentDocument(
            one.id(),
            AgentDocumentOrigin.event(TestDocuments.MESSAGE_ID),
            AgentDocument.of(
                    document("two", "two.txt"), AgentDocumentOrigin.event(TestDocuments.MESSAGE_ID))
                .reference());
    final var other =
        AgentDocument.of(
            document("three", "three.txt"),
            AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID));

    assertThat(AgentDocuments.of(List.of(one, duplicate, other)).all()).containsExactly(one, other);
  }

  @Test
  void hasValueEquality() {
    final var doc = document("one", "one.txt");

    assertThat(
            AgentDocuments.empty()
                .register(doc, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID)))
        .isEqualTo(
            AgentDocuments.empty()
                .register(doc, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID)))
        .hasSameHashCodeAs(
            AgentDocuments.empty()
                .register(doc, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID)));
    assertThat(
            AgentDocuments.empty()
                .register(doc, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID)))
        .isNotEqualTo(AgentDocuments.empty());
  }

  @Test
  void roundTripsThroughJsonAsTheListOfItsDocuments() throws JsonProcessingException {
    final var documents =
        AgentDocuments.empty()
            .register(
                document("one", "one.txt"),
                AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))
            .register(document("two", "two.txt"), AgentDocumentOrigin.toolCall("search", "call-1"));

    final var json = objectMapper.writeValueAsString(documents);

    assertThat(objectMapper.readTree(json).isArray()).isTrue();
    assertThat(objectMapper.readValue(json, AgentDocuments.class)).isEqualTo(documents);
  }
}
