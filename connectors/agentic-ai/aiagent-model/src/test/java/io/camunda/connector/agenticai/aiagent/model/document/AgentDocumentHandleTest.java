/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.message.MessageId;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentMetadataModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.InlineDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class AgentDocumentHandleTest {

  private static final String ID = "doc_0123456789ab";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  private static String json(AgentDocumentHandle handle) {
    try {
      return OBJECT_MAPPER.writeValueAsString(handle);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void writesAllFieldsInKeyOrderWithTheSizeAsANumber() throws Exception {
    final var handle =
        new AgentDocumentHandle(AgentDocumentHandle.TYPE, ID, "a.pdf", "application/pdf", 123L);

    assertThat(json(handle))
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"doc_0123456789ab\","
                + "\"fileName\":\"a.pdf\",\"contentType\":\"application/pdf\",\"size\":123}");
  }

  @Test
  void writesTheIdOnly() throws Exception {
    assertThat(json(AgentDocumentHandle.of(AgentDocumentId.of(ID))))
        .isEqualTo("{\"type\":\"camunda.agenticai.document\",\"id\":\"doc_0123456789ab\"}");
  }

  @Test
  void escapesTheFileName() throws Exception {
    final var handle =
        new AgentDocumentHandle(AgentDocumentHandle.TYPE, ID, "a \"b\" <c> \\ ä.txt", null, null);

    assertThat(json(handle))
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"doc_0123456789ab\","
                + "\"fileName\":\"a \\\"b\\\" <c> \\\\ ä.txt\"}");
  }

  @Test
  void omitsBlankValuesAndKeepsASizeOfZero() throws Exception {
    final var handle = new AgentDocumentHandle(AgentDocumentHandle.TYPE, ID, " ", "", 0L);

    assertThat(json(handle))
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"doc_0123456789ab\",\"size\":0}");
  }

  @Test
  void readsTheDescriptorsOfAKnownDocumentWithoutItsContent() throws Exception {
    final var document =
        new AgentDocument(
            AgentDocumentId.of(ID),
            AgentDocumentOrigin.userMessage(MessageId.of("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a2b")),
            new CamundaDocumentReferenceModel(
                "store",
                "doc-1",
                "hash",
                new CamundaDocumentMetadataModel(
                    "text/plain", null, null, "a.txt", null, null, null)));

    assertThat(json(AgentDocumentHandle.of(document)))
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"doc_0123456789ab\","
                + "\"fileName\":\"a.txt\",\"contentType\":\"text/plain\"}");
  }

  @Test
  void aDocumentThatIsNotACamundaDocumentHasNoHandle() {
    final var documents =
        List.<Document>of(
            new ExternalDocument("https://example.com/a.pdf", "a.pdf", u -> null),
            new InlineDocument("{\"a\":1}", "data.json", "application/json"));

    for (final var document : documents) {
      assertThatThrownBy(() -> AgentDocumentHandle.of(document))
          .isInstanceOf(UnsupportedDocumentReferenceException.class);
    }
  }

  @Test
  void rejectsAnotherType() {
    assertThatThrownBy(() -> new AgentDocumentHandle("other", ID, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static AgentDocuments agentDocumentsOf(Document document, AgentDocumentOrigin origin) {
    return AgentDocuments.empty().register(document, origin);
  }

  /** The text of the handle of the known document. */
  private static String text(Document document) {
    return json(
        AgentDocumentHandle.of(
            agentDocumentsOf(
                document,
                AgentDocumentOrigin.userMessage(
                    MessageId.of("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a2b"))),
            AgentDocumentId.of(document)));
  }

  private Document camundaDocument(@Nullable String fileName, @Nullable String contentType) {
    final var request =
        DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(fileName);
    return documentFactory.create(request.build());
  }

  private static Document camundaDocumentWithSize(@Nullable Long size) {
    final var reference =
        new CamundaDocumentReferenceModel(
            "store",
            "doc-1",
            "hash",
            new CamundaDocumentMetadataModel(
                "application/pdf", null, size, "report.pdf", null, null, null));
    return documentWith(reference, reference.metadata());
  }

  private static Document documentWith(
      DocumentReference reference, io.camunda.connector.api.document.DocumentMetadata metadata) {
    final var document = mock(Document.class);
    when(document.reference()).thenReturn(reference);
    when(document.metadata()).thenReturn(metadata);
    return document;
  }

  @Test
  void camundaDocumentRendersIdFileNameAndContentTypeAndNeverItsStoreInternals() {
    final var doc = camundaDocument("report.pdf", "application/pdf");

    final var text = text(doc);

    assertThat(text)
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"%s\",\"fileName\":\"report.pdf\",\"contentType\":\"application/pdf\"}"
                .formatted(AgentDocumentId.of(doc)));
    assertThat(text).doesNotContain("storeId", "contentHash", "documentId", "metadata", "url");
  }

  @Test
  void camundaDocumentWithoutMetadataOmitsFileNameAndContentType() {
    final var doc = camundaDocument(null, null);

    assertThat(text(doc))
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"%s\"}"
                .formatted(AgentDocumentId.of(doc)));
  }

  @Test
  void omitsAFileNameThatEqualsTheCamundaDocumentIdOfTheReference() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                .documentId("0f6b0a1e-aaaa-bbbb-cccc-0123456789ab")
                .fileName("0f6b0a1e-aaaa-bbbb-cccc-0123456789ab")
                .contentType("text/plain")
                .build());

    assertThat(text(doc))
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"%s\",\"contentType\":\"text/plain\"}"
                .formatted(AgentDocumentId.of(doc)));
  }

  @Test
  void keepsAFileNameThatDiffersFromTheCamundaDocumentId() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                .documentId("doc-1")
                .fileName("doc-1.txt")
                .build());

    assertThat(text(doc))
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"%s\",\"fileName\":\"doc-1.txt\"}"
                .formatted(AgentDocumentId.of(doc)));
  }

  @Test
  void writesAllFieldsInOrderWithTheSizeAsANumber() {
    final var doc = camundaDocumentWithSize(48213L);

    assertThat(text(doc))
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"%s\",\"fileName\":\"report.pdf\",\"contentType\":\"application/pdf\",\"size\":48213}"
                .formatted(AgentDocumentId.of(doc)));
  }

  @Test
  void theHandleHasNoOriginWhateverTheOriginOfTheDocument() {
    final var doc = camundaDocument("a.txt", "text/plain");
    final var expected = json(AgentDocumentHandle.of(doc));

    for (final var origin :
        List.of(
            AgentDocumentOrigin.userMessage(MessageId.of("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a2b")),
            AgentDocumentOrigin.event(MessageId.of("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a2b")),
            AgentDocumentOrigin.toolCall("search", "call_1"))) {
      assertThat(
              json(AgentDocumentHandle.of(agentDocumentsOf(doc, origin), AgentDocumentId.of(doc))))
          .isEqualTo(expected);
    }
  }

  @Test
  void theSizeIsReadFromTheMetadataWithoutReadingTheDocument() {
    final var document = camundaDocumentWithSize(10L);

    assertThat(json(AgentDocumentHandle.of(document))).contains("\"size\":10");

    verify(document, atLeastOnce()).reference();
    verifyNoMoreInteractions(document);
  }

  @Test
  void aDocumentWithoutAKnownSizeRendersNone() {
    assertThat(text(camundaDocumentWithSize(null))).doesNotContain("size");
  }

  @Test
  void theHandleOfAKnownIdEqualsTheHandleOfTheLiveDocument() {
    final var documents =
        List.<Document>of(
            camundaDocument("a.txt", "text/plain"),
            camundaDocument(null, null),
            camundaDocumentWithSize(48213L));

    for (final var doc : documents) {
      assertThat(text(doc)).as("%s", doc.reference()).isEqualTo(json(AgentDocumentHandle.of(doc)));
    }
  }

  @Test
  void aDocumentTheAgentDoesNotKnowIsRenderedWithItsIdOnly() {
    final var id = AgentDocumentId.of("doc_000000000000");
    final var expected = "{\"type\":\"camunda.agenticai.document\",\"id\":\"doc_000000000000\"}";

    assertThat(json(AgentDocumentHandle.of(AgentDocuments.empty(), id))).isEqualTo(expected);
    assertThat(json(AgentDocumentHandle.of(id))).isEqualTo(expected);
  }
}
