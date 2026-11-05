/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentOrigin;
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

class DocumentTagsTest {

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  private static AgentDocuments workspaceOf(Document document, DocumentOrigin origin) {
    return AgentDocuments.empty().register(document, origin);
  }

  private static String longTag(Document document, DocumentOrigin origin) {
    return DocumentTags.longTag(workspaceOf(document, origin), AgentDocumentId.of(document));
  }

  private static String shortTag(Document document) {
    return DocumentTags.shortTag(
        workspaceOf(document, DocumentOrigin.userMessage()), AgentDocumentId.of(document));
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
  void camundaDocumentRendersIdFileNameContentTypeAndToolOriginAndNeverItsStoreInternals() {
    final var doc = camundaDocument("report.pdf", "application/pdf");

    final var tag = longTag(doc, DocumentOrigin.toolCall("search", "call_abc"));

    assertThat(tag)
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"report.pdf\" contentType=\"application/pdf\" toolName=\"search\" toolCallId=\"call_abc\" />"
                .formatted(AgentDocumentId.of(doc)));
    assertThat(tag).doesNotContain("storeId", "contentHash", "documentId", "metadata", "url");
  }

  @Test
  void camundaDocumentWithoutMetadataOmitsFileNameAndContentType() {
    final var doc = camundaDocument(null, null);

    assertThat(longTag(doc, DocumentOrigin.toolCall("search", "call_abc")))
        .startsWith("<doc id=\"%s\"".formatted(AgentDocumentId.of(doc)))
        .doesNotContain("fileName", "contentType");
  }

  @Test
  void externalDocumentUsesReferenceNameAndNeverRendersTheUrlOrFetchesMetadata() {
    // the downloader fails on use: rendering must not trigger a download for the content type
    final var doc =
        new ExternalDocument(
            "https://example.com/files/report.pdf?X-Token=secret", "Quarterly Report", u -> null);

    final var tag = longTag(doc, DocumentOrigin.toolCall("search", "call_abc"));

    assertThat(tag)
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"Quarterly Report\" toolName=\"search\" toolCallId=\"call_abc\" />"
                .formatted(AgentDocumentId.of(doc)));
    assertThat(tag).doesNotContain("example.com", "secret", "url");
  }

  @Test
  void externalDocumentWithoutNameOmitsFileNameInsteadOfTakingItFromTheUrl() {
    final var doc =
        new ExternalDocument(
            "https://example.com/a/b/my%20file.pdf?X-Token=secret#frag", null, u -> null);

    assertThat(shortTag(doc)).isEqualTo("<doc id=\"%s\" />".formatted(AgentDocumentId.of(doc)));
    assertThat(shortTag(new ExternalDocument("https://example.com/x", "", u -> null)))
        .doesNotContain("fileName");
  }

  @Test
  void inlineDocumentWithoutContentTypeOmitsItInsteadOfResolvingItFromTheName() {
    final var doc = new InlineDocument("{\"a\":1}", "data.json", null);

    assertThat(shortTag(doc))
        .isEqualTo("<doc id=\"%s\" fileName=\"data.json\" />".formatted(AgentDocumentId.of(doc)));
  }

  @Test
  void inlineDocumentRendersNameAndContentType() {
    final var doc = new InlineDocument("{\"a\":1}", "data.json", "application/json");

    assertThat(longTag(doc, DocumentOrigin.toolCall("gen", "call_1")))
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"data.json\" contentType=\"application/json\" toolName=\"gen\" toolCallId=\"call_1\" />"
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
    final var id = AgentDocumentId.of(doc);
    final var origin = DocumentOrigin.toolCall("tool", "call_1");

    assertThat(longTag(doc, origin))
        .isEqualTo(
            "<doc id=\"%s\" contentType=\"text/plain\" toolName=\"tool\" toolCallId=\"call_1\" />"
                .formatted(id));
    assertThat(DocumentTags.shortTag(doc))
        .isEqualTo("<doc id=\"%s\" contentType=\"text/plain\" />".formatted(id));
  }

  @Test
  void keepsAFileNameThatDiffersFromTheCamundaDocumentId() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                .documentId("doc-1")
                .fileName("doc-1.txt")
                .build());

    assertThat(shortTag(doc))
        .isEqualTo("<doc id=\"%s\" fileName=\"doc-1.txt\" />".formatted(AgentDocumentId.of(doc)));
  }

  @Test
  void escapesSpecialCharactersInAttributes() {
    final var doc = new ExternalDocument("https://example.com/x", "a<b>\"c\"&.pdf", u -> null);

    assertThat(longTag(doc, DocumentOrigin.toolCall("tool<with\"quotes>", "call_1")))
        .contains("fileName=\"a&lt;b&gt;&quot;c&quot;&amp;.pdf\"")
        .contains("toolName=\"tool&lt;with&quot;quotes&gt;\"");
  }

  @Test
  void userMessageAndEventDocumentsCarryTheSourceInsteadOfToolAttributes() {
    final var doc = camundaDocument("a.txt", "text/plain");
    final var id = AgentDocumentId.of(doc);

    assertThat(longTag(doc, DocumentOrigin.userMessage()))
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"a.txt\" contentType=\"text/plain\" source=\"user_message\" />"
                .formatted(id));
    assertThat(longTag(doc, DocumentOrigin.event()))
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"a.txt\" contentType=\"text/plain\" source=\"event\" />"
                .formatted(id));
  }

  @Test
  void theShortTagHasNoOriginAttributesWhateverTheOriginOfTheDocument() {
    final var doc = camundaDocument("a.txt", "text/plain");
    final var expected =
        "<doc id=\"%s\" fileName=\"a.txt\" contentType=\"text/plain\" />"
            .formatted(AgentDocumentId.of(doc));

    for (final var origin :
        List.of(
            DocumentOrigin.userMessage(),
            DocumentOrigin.event(),
            DocumentOrigin.toolCall("search", "call_1"))) {
      assertThat(DocumentTags.shortTag(workspaceOf(doc, origin), AgentDocumentId.of(doc)))
          .isEqualTo(expected);
    }
    assertThat(DocumentTags.shortTag(doc)).isEqualTo(expected);
  }

  @Test
  void rendersAllAttributesInOrder() {
    final var doc = camundaDocumentWithSize(48213L);
    final var id = AgentDocumentId.of(doc);

    assertThat(longTag(doc, DocumentOrigin.userMessage()))
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"report.pdf\" contentType=\"application/pdf\" size=\"48213\" source=\"user_message\" />"
                .formatted(id));
    assertThat(longTag(doc, DocumentOrigin.toolCall("search", "call_abc")))
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"report.pdf\" contentType=\"application/pdf\" size=\"48213\" toolName=\"search\" toolCallId=\"call_abc\" />"
                .formatted(id));
  }

  @Test
  void theSizeIsReadFromTheMetadataWithoutReadingTheDocument() {
    final var document = camundaDocumentWithSize(10L);

    assertThat(DocumentTags.shortTag(document)).contains("size=\"10\"");

    verify(document, atLeastOnce()).reference();
    verifyNoMoreInteractions(document);
  }

  @Test
  void aDocumentWithoutAKnownSizeRendersNone() {
    assertThat(shortTag(camundaDocumentWithSize(null))).doesNotContain("size=");
    assertThat(shortTag(new ExternalDocument("https://example.com/a.pdf", "a.pdf", u -> null)))
        .doesNotContain("size=");
    assertThat(shortTag(new InlineDocument("{\"a\":1}", "d.json", "application/json")))
        .doesNotContain("size=");
  }

  @Test
  void shortTagOfADocumentEqualsTheShortTagOfItsAgentDocument() {
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
      assertThat(shortTag(doc)).as("%s", doc.reference()).isEqualTo(DocumentTags.shortTag(doc));
    }
  }

  @Test
  void aDocumentTheAgentDoesNotKnowIsRenderedWithItsIdOnly() {
    final var id = AgentDocumentId.of("doc_000000000000");

    assertThat(DocumentTags.longTag(AgentDocuments.empty(), id))
        .isEqualTo("<doc id=\"doc_000000000000\" />");
    assertThat(DocumentTags.shortTag(AgentDocuments.empty(), id))
        .isEqualTo("<doc id=\"doc_000000000000\" />");
    assertThat(DocumentTags.idOnlyTag(id)).isEqualTo("<doc id=\"doc_000000000000\" />");
  }
}
