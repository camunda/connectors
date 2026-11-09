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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentMetadataModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.InlineDocumentReferenceModel;
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

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  private String json(AgentDocumentHandle handle) throws Exception {
    return objectMapper.writeValueAsString(handle);
  }

  @Test
  void writesAllFieldsInKeyOrderWithTheSizeAsANumber() throws Exception {
    final var handle =
        new AgentDocumentHandle(
            AgentDocumentHandle.TYPE,
            ID,
            "a.pdf",
            "application/pdf",
            123L,
            "The content is not loaded.");

    assertThat(json(handle))
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"doc_0123456789ab\","
                + "\"fileName\":\"a.pdf\",\"contentType\":\"application/pdf\",\"size\":123,"
                + "\"note\":\"The content is not loaded.\"}");
  }

  @Test
  void writesTheIdOnly() throws Exception {
    assertThat(json(AgentDocumentHandle.of(AgentDocumentId.of(ID))))
        .isEqualTo("{\"type\":\"camunda.agenticai.document\",\"id\":\"doc_0123456789ab\"}");
  }

  @Test
  void escapesTheFileName() throws Exception {
    final var handle =
        new AgentDocumentHandle(
            AgentDocumentHandle.TYPE, ID, "a \"b\" <c> \\ ä.txt", null, null, null);

    assertThat(json(handle))
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"doc_0123456789ab\","
                + "\"fileName\":\"a \\\"b\\\" <c> \\\\ ä.txt\"}");
  }

  @Test
  void omitsBlankValuesAndKeepsASizeOfZero() throws Exception {
    final var handle = new AgentDocumentHandle(AgentDocumentHandle.TYPE, ID, " ", "", 0L, "  ");

    assertThat(json(handle))
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"doc_0123456789ab\",\"size\":0}");
  }

  @Test
  void readsTheDescriptorsOfAKnownDocumentWithoutItsContent() throws Exception {
    final var document =
        new AgentDocument(
            AgentDocumentId.of(ID),
            DocumentOrigin.userMessage(),
            new InlineDocumentReferenceModel("hello", "a.txt", "text/plain"));

    assertThat(json(AgentDocumentHandle.of(document)))
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"doc_0123456789ab\","
                + "\"fileName\":\"a.txt\",\"contentType\":\"text/plain\"}");
  }

  @Test
  void rejectsAnotherType() {
    assertThatThrownBy(() -> new AgentDocumentHandle("other", ID, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static AgentDocuments agentDocumentsOf(Document document, DocumentOrigin origin) {
    return AgentDocuments.empty().register(document, origin);
  }

  /** The text of the handle of the known document. */
  private static String text(Document document) {
    return AgentDocumentHandle.of(
            agentDocumentsOf(document, DocumentOrigin.userMessage()), AgentDocumentId.of(document))
        .text();
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
  void externalDocumentUsesReferenceNameAndNeverRendersTheUrlOrFetchesMetadata() {
    // the downloader fails on use: rendering must not trigger a download for the content type
    final var doc =
        new ExternalDocument(
            "https://example.com/files/report.pdf?X-Token=secret", "Quarterly Report", u -> null);

    final var text = text(doc);

    assertThat(text)
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"%s\",\"fileName\":\"Quarterly Report\"}"
                .formatted(AgentDocumentId.of(doc)));
    assertThat(text).doesNotContain("example.com", "secret", "url");
  }

  @Test
  void externalDocumentWithoutNameOmitsFileNameInsteadOfTakingItFromTheUrl() {
    final var doc =
        new ExternalDocument(
            "https://example.com/a/b/my%20file.pdf?X-Token=secret#frag", null, u -> null);

    assertThat(text(doc))
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"%s\"}"
                .formatted(AgentDocumentId.of(doc)));
    assertThat(text(new ExternalDocument("https://example.com/x", "", u -> null)))
        .doesNotContain("fileName");
  }

  @Test
  void inlineDocumentWithoutContentTypeOmitsItInsteadOfResolvingItFromTheName() {
    final var doc = new InlineDocument("{\"a\":1}", "data.json", null);

    assertThat(text(doc))
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"%s\",\"fileName\":\"data.json\"}"
                .formatted(AgentDocumentId.of(doc)));
  }

  @Test
  void inlineDocumentRendersNameAndContentTypeAndNeverItsContent() {
    final var doc = new InlineDocument("{\"a\":1}", "data.json", "application/json");

    assertThat(text(doc))
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"%s\",\"fileName\":\"data.json\",\"contentType\":\"application/json\"}"
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
  void escapesSpecialCharactersInTheFileName() {
    final var doc = new ExternalDocument("https://example.com/x", "a<b>\"c\"&.pdf", u -> null);

    assertThat(text(doc)).contains("\"fileName\":\"a<b>\\\"c\\\"&.pdf\"");
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
    final var expected = AgentDocumentHandle.of(doc).text();

    for (final var origin :
        List.of(
            DocumentOrigin.userMessage(),
            DocumentOrigin.event(),
            DocumentOrigin.toolCall("search", "call_1"))) {
      assertThat(
              AgentDocumentHandle.of(agentDocumentsOf(doc, origin), AgentDocumentId.of(doc)).text())
          .isEqualTo(expected);
    }
  }

  @Test
  void theSizeIsReadFromTheMetadataWithoutReadingTheDocument() {
    final var document = camundaDocumentWithSize(10L);

    assertThat(AgentDocumentHandle.of(document).text()).contains("\"size\":10");

    verify(document, atLeastOnce()).reference();
    verifyNoMoreInteractions(document);
  }

  @Test
  void aDocumentWithoutAKnownSizeRendersNone() {
    assertThat(text(camundaDocumentWithSize(null))).doesNotContain("size");
    assertThat(text(new ExternalDocument("https://example.com/a.pdf", "a.pdf", u -> null)))
        .doesNotContain("size");
    assertThat(text(new InlineDocument("{\"a\":1}", "d.json", "application/json")))
        .doesNotContain("size");
  }

  @Test
  void theHandleOfAKnownIdEqualsTheHandleOfTheLiveDocument() {
    final var documents =
        List.<Document>of(
            camundaDocument("a.txt", "text/plain"),
            camundaDocument(null, null),
            camundaDocumentWithSize(48213L),
            new ExternalDocument(
                "https://example.com/files/report.pdf?t=secret", "Report", u -> null),
            new ExternalDocument("https://example.com/files/report.pdf?t=secret", null, u -> null),
            new InlineDocument("{\"a\":1}", "data.json", "application/json"),
            new InlineDocument("{\"a\":1}", "data.json", null));

    for (final var doc : documents) {
      assertThat(text(doc)).as("%s", doc.reference()).isEqualTo(AgentDocumentHandle.of(doc).text());
    }
  }

  @Test
  void aDocumentTheAgentDoesNotKnowIsRenderedWithItsIdOnly() {
    final var id = AgentDocumentId.of("doc_000000000000");
    final var expected = "{\"type\":\"camunda.agenticai.document\",\"id\":\"doc_000000000000\"}";

    assertThat(AgentDocumentHandle.of(AgentDocuments.empty(), id).text()).isEqualTo(expected);
    assertThat(AgentDocumentHandle.of(id).text()).isEqualTo(expected);
  }

  @Test
  void theTreeHasTheKeysOfTheTextInOrder() {
    final var doc = camundaDocumentWithSize(48213L);
    final var tree = AgentDocumentHandle.of(doc).tree();

    assertThat(tree.isObject()).isTrue();
    assertThat(tree.fieldNames())
        .toIterable()
        .containsExactly("type", "id", "fileName", "contentType", "size");
    assertThat(tree.get("size").isNumber()).isTrue();
    assertThat(tree.toString()).isEqualTo(text(doc));
  }
}
