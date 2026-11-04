/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_DOCUMENT_ID_COLLISION;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_INLINE_DOCUMENT_TOO_LARGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentWorkspace;
import io.camunda.connector.agenticai.aiagent.model.document.WorkspaceDocument;
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

class DocumentWorkspaceTest {

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
  void emptyWorkspaceHasNoEntries() {
    final var workspace = DocumentWorkspace.empty();

    assertThat(workspace.isEmpty()).isTrue();
    assertThat(workspace.size()).isZero();
    assertThat(workspace.entries()).isEmpty();
    assertThat(workspace.findById(DocumentId.of("unknown"))).isEmpty();
  }

  @Test
  void keepsInsertionOrderAndLooksUpById() {
    final var first = document("one", "one.txt");
    final var second = document("two", "two.txt");

    final var workspace =
        DocumentIngestion.withDocuments(
            DocumentWorkspace.empty(),
            List.of(
                new IngestedDocument(first, DocumentOrigin.userMessage()),
                new IngestedDocument(second, DocumentOrigin.toolCall("search", "call-1"))));

    assertThat(workspace.entries())
        .extracting(WorkspaceDocument::id)
        .containsExactly(DocumentHandle.idFor(first), DocumentHandle.idFor(second));
    assertThat(workspace.findById(DocumentHandle.idFor(second)))
        .hasValueSatisfying(entry -> {
          assertThat(DocumentHandle.fileNameFor(DocumentIngestion.referenceOf(entry)))
              .isEqualTo("two.txt");
          assertThat(DocumentHandle.contentTypeFor(DocumentIngestion.referenceOf(entry)))
              .isEqualTo("text/plain");
          assertThat(entry.origin()).isEqualTo(DocumentOrigin.toolCall("search", "call-1"));
        });
  }

  @Test
  void rejectsABatchWithAnInlineDocumentAboveTheLimit() {
    final var tooLarge =
        documentWith(
            new InlineDocumentReferenceModel(
                "x".repeat(DocumentIngestion.MAX_INLINE_CONTENT_LENGTH + 1),
                "big.txt",
                "text/plain"));
    final var batch =
        List.of(
            new IngestedDocument(document("one", "one.txt"), DocumentOrigin.userMessage()),
            new IngestedDocument(tooLarge, DocumentOrigin.userMessage()));

    assertThatThrownBy(() -> DocumentIngestion.withDocuments(DocumentWorkspace.empty(), batch))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_INLINE_DOCUMENT_TOO_LARGE));
  }

  private static IngestedDocument inline(String content, String name) {
    return new IngestedDocument(
        documentWith(new InlineDocumentReferenceModel(content, name, "text/plain")),
        DocumentOrigin.userMessage());
  }

  @Test
  void firstRegistrationWins() {
    final var doc = document("one", "one.txt");

    final var workspace =
        DocumentIngestion.withDocuments(
            DocumentIngestion.withDocuments(
                DocumentWorkspace.empty(),
                List.of(new IngestedDocument(doc, DocumentOrigin.toolCall("search", "call-1")))),
            List.of(
                new IngestedDocument(doc, DocumentOrigin.toolCall("other", "call-2")),
                new IngestedDocument(doc, DocumentOrigin.userMessage())));

    assertThat(workspace.size()).isEqualTo(1);
    assertThat(workspace.findById(DocumentHandle.idFor(doc)))
        .hasValueSatisfying(entry ->
            assertThat(entry.origin()).isEqualTo(DocumentOrigin.toolCall("search", "call-1")));
  }

  @Test
  void addingKnownDocumentsReturnsSameInstance() {
    final var doc = document("one", "one.txt");
    final var workspace =
        DocumentIngestion.withDocuments(
            DocumentWorkspace.empty(),
            List.of(new IngestedDocument(doc, DocumentOrigin.userMessage())));

    assertThat(
            DocumentIngestion.withDocuments(
                workspace, List.of(new IngestedDocument(doc, DocumentOrigin.toolCall("t", "c")))))
        .isSameAs(workspace);
    assertThat(DocumentIngestion.withDocuments(workspace, List.of())).isSameAs(workspace);
  }

  @Test
  void aDocumentWithTheIdOfADifferentDocumentOfTheWorkspaceIsRejected() {
    final var registered = document("one", "one.txt");
    final var other = document("two", "two.txt");
    // the workspace holds `registered` under the id `other` gets
    final var workspace =
        DocumentWorkspace.of(
            List.of(
                new WorkspaceDocument(
                    DocumentHandle.idFor(other),
                    DocumentOrigin.userMessage(),
                    DocumentIngestion.entryFor(
                            new IngestedDocument(registered, DocumentOrigin.userMessage()))
                        .reference())));

    assertThatThrownBy(() ->
            DocumentIngestion.withDocuments(
                workspace, List.of(new IngestedDocument(other, DocumentOrigin.toolCall("t", "c")))))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_DOCUMENT_ID_COLLISION);
              assertThat(e.getMessage())
                  .contains("'" + DocumentHandle.idFor(other) + "'", "two different documents");
            });
  }

  @Test
  void ofDeduplicatesEntriesByIdKeepingTheFirst() {
    final var entry1 =
        new WorkspaceDocument(DocumentId.of("a"), DocumentOrigin.userMessage(), Map.of("k", "1"));
    final var entry2 =
        new WorkspaceDocument(DocumentId.of("a"), DocumentOrigin.userMessage(), Map.of("k", "2"));
    final var entry3 =
        new WorkspaceDocument(DocumentId.of("b"), DocumentOrigin.userMessage(), Map.of("k", "3"));

    assertThat(DocumentWorkspace.of(List.of(entry1, entry2, entry3)).entries())
        .containsExactly(entry1, entry3);
  }
}
