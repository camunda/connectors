/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentReferences;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.InlineDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DocumentReferenceContentTest {

  private static final Document DOCUMENT = new InlineDocument("data", "a.txt", "text/plain");

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  @Test
  void camundaDocumentRendersIdFileNameContentTypeAndToolOriginAndNeverItsStoreInternals() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                .contentType("application/pdf")
                .fileName("report.pdf")
                .build());

    final var tag =
        DocumentReferences.from(doc, DocumentSource.toolCall("search", "call_abc")).toTag();

    assertThat(tag)
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"report.pdf\" contentType=\"application/pdf\" toolName=\"search\" toolCallId=\"call_abc\" />"
                .formatted(DocumentHandle.idFor(doc)));
    assertThat(tag).doesNotContain("storeId", "contentHash", "documentId", "metadata", "url");
  }

  @Test
  void camundaDocumentWithoutToolOriginOmitsToolAttributes() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName("a.txt")
                .build());

    assertThat(DocumentReferences.from(doc).toTag())
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"a.txt\" contentType=\"text/plain\" />"
                .formatted(DocumentHandle.idFor(doc)));
  }

  @Test
  void camundaDocumentWithoutMetadataOmitsFileNameAndContentType() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8)).build());

    assertThat(DocumentReferences.from(doc).toTag())
        .startsWith("<doc id=\"%s\"".formatted(DocumentHandle.idFor(doc)))
        .doesNotContain("fileName", "contentType", "toolName", "toolCallId");
  }

  @Test
  void externalDocumentUsesReferenceNameAndNeverRendersTheUrlOrFetchesMetadata() {
    // the downloader fails on use: rendering must not trigger a download for the content type
    final var doc =
        new ExternalDocument(
            "https://example.com/files/report.pdf?X-Token=secret", "Quarterly Report", u -> null);

    final var xml =
        DocumentReferences.from(doc, DocumentSource.toolCall("search", "call_abc")).toTag();

    assertThat(xml)
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"Quarterly Report\" toolName=\"search\" toolCallId=\"call_abc\" />"
                .formatted(DocumentHandle.idFor(doc)));
    assertThat(xml).doesNotContain("example.com", "secret", "url");
  }

  @Test
  void externalDocumentWithoutNameOmitsFileNameInsteadOfTakingItFromTheUrl() {
    final var doc =
        new ExternalDocument(
            "https://example.com/a/b/my%20file.pdf?X-Token=secret#frag", null, u -> null);

    assertThat(DocumentReferences.from(doc).toTag())
        .isEqualTo("<doc id=\"%s\" />".formatted(DocumentHandle.idFor(doc)));
    assertThat(
            DocumentReferences.from(new ExternalDocument("https://example.com/x", "", u -> null))
                .fileName())
        .isNull();
  }

  @Test
  void inlineDocumentWithoutContentTypeOmitsItInsteadOfResolvingItFromTheName() {
    final var doc = new InlineDocument("{\"a\":1}", "data.json", null);

    assertThat(DocumentReferences.from(doc).toTag())
        .isEqualTo("<doc id=\"%s\" fileName=\"data.json\" />".formatted(DocumentHandle.idFor(doc)));
  }

  @Test
  void inlineDocumentRendersNameAndContentType() {
    final var doc = new InlineDocument("{\"a\":1}", "data.json", "application/json");

    assertThat(DocumentReferences.from(doc, DocumentSource.toolCall("gen", "call_1")).toTag())
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"data.json\" contentType=\"application/json\" toolName=\"gen\" toolCallId=\"call_1\" />"
                .formatted(DocumentHandle.idFor(doc)));
  }

  @Test
  void omitsAFileNameThatEqualsTheId() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                .documentId("0f6b0a1e-aaaa-bbbb-cccc-0123456789ab")
                .fileName("0f6b0a1e-aaaa-bbbb-cccc-0123456789ab")
                .contentType("text/plain")
                .build());
    final var expected =
        "<doc id=\"0f6b0a1e-aaaa-bbbb-cccc-0123456789ab\" contentType=\"text/plain\""
            + " toolName=\"tool\" toolCallId=\"call_1\" />";

    assertThat(DocumentReferences.from(doc, DocumentSource.toolCall("tool", "call_1")).toTag())
        .isEqualTo(expected);
  }

  @Test
  void keepsAFileNameThatDiffersFromTheId() {
    assertThat(reference("doc-1", "doc-1.txt", null, null, null, null, null).toTag())
        .isEqualTo("<doc id=\"doc-1\" fileName=\"doc-1.txt\" />");
  }

  @Test
  void escapesSpecialCharactersInAttributes() {
    final var doc = new ExternalDocument("https://example.com/x", "a<b>\"c\"&.pdf", u -> null);

    assertThat(
            DocumentReferences.from(doc, DocumentSource.toolCall("tool<with\"quotes>", "call_1"))
                .toTag())
        .contains("fileName=\"a&lt;b&gt;&quot;c&quot;&amp;.pdf\"")
        .contains("toolName=\"tool&lt;with&quot;quotes&gt;\"");
  }

  @Test
  void userMessageAndEventDocumentsCarryTheSourceInsteadOfToolAttributes() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName("a.txt")
                .build());

    assertThat(DocumentReferences.from(doc, DocumentSource.userMessage()).toTag())
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"a.txt\" contentType=\"text/plain\" source=\"user_message\" />"
                .formatted(DocumentHandle.idFor(doc)));
    assertThat(DocumentReferences.from(doc, DocumentSource.event()).toTag())
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"a.txt\" contentType=\"text/plain\" source=\"event\" />"
                .formatted(DocumentHandle.idFor(doc)));
  }

  private static Document camundaDocumentWithSize(@Nullable Long size) {
    final var reference = mock(CamundaDocumentReference.class);
    when(reference.getDocumentId()).thenReturn("doc-1");
    final var metadata = mock(DocumentMetadata.class);
    when(metadata.getFileName()).thenReturn("report.pdf");
    when(metadata.getContentType()).thenReturn("application/pdf");
    when(metadata.getSize()).thenReturn(size);
    final var document = mock(Document.class);
    when(document.reference()).thenReturn(reference);
    when(document.metadata()).thenReturn(metadata);
    return document;
  }

  @Test
  void camundaDocumentWithASizeInItsMetadataRendersTheSizeAfterTheContentType() {
    final var document = camundaDocumentWithSize(48213L);

    assertThat(DocumentReferences.from(document).toTag())
        .isEqualTo(
            "<doc id=\"doc-1\" fileName=\"report.pdf\" contentType=\"application/pdf\" size=\"48213\" />");
  }

  @Test
  void theSizeIsReadFromTheMetadataWithoutReadingTheDocument() {
    final var document = camundaDocumentWithSize(10L);

    DocumentReferences.from(document, DocumentSource.toolCall("search", "call_1"));

    verify(document, atLeastOnce()).reference();
    verify(document, atLeastOnce()).metadata();
    verifyNoMoreInteractions(document);
  }

  @Test
  void aDocumentWithoutAKnownSizeRendersNone() {
    assertThat(DocumentReferences.from(camundaDocumentWithSize(null)).toTag())
        .doesNotContain("size=");
    assertThat(
            DocumentReferences.from(
                    new ExternalDocument("https://example.com/a.pdf", "a.pdf", u -> null))
                .toTag())
        .doesNotContain("size=");
    assertThat(
            DocumentReferences.from(new InlineDocument("{\"a\":1}", "d.json", "application/json"))
                .toTag())
        .doesNotContain("size=");
  }

  private static DocumentReferenceContent reference(
      String id,
      @Nullable String fileName,
      @Nullable String contentType,
      @Nullable Long size,
      @Nullable String source,
      @Nullable String toolName,
      @Nullable String toolCallId) {
    final var type =
        "user_message".equals(source)
            ? DocumentSource.Type.USER_MESSAGE
            : "event".equals(source) ? DocumentSource.Type.EVENT : DocumentSource.Type.TOOL_CALL;
    final var documentSource =
        type == DocumentSource.Type.TOOL_CALL && toolName == null && toolCallId == null
            ? null
            : new DocumentSource(type, toolName, toolCallId);
    return new DocumentReferenceContent(
        id == null ? null : DocumentId.of(id),
        DOCUMENT,
        fileName,
        contentType,
        size,
        documentSource,
        DocumentDelivery.REFERENCE,
        null);
  }

  @Test
  void rendersAllAttributesInOrder() {
    assertThat(
            reference(
                    "doc-1",
                    "report.pdf",
                    "application/pdf",
                    48213L,
                    "user_message",
                    "search",
                    "call_abc")
                .toTag())
        .isEqualTo(
            "<doc id=\"doc-1\" fileName=\"report.pdf\" contentType=\"application/pdf\" size=\"48213\" source=\"user_message\" toolName=\"search\" toolCallId=\"call_abc\" />");
  }

  @Test
  void omitsNullAndBlankAttributes() {
    assertThat(reference("doc-1", " ", "", null, "  ", null, "").toTag())
        .isEqualTo("<doc id=\"doc-1\" />");
  }

  @Test
  void theDeliveryDoesNotChangeTheTag() {
    final var reference = reference("doc-1", "a.txt", "text/plain", 3L, "event", null, null);

    assertThat(reference.withDelivery(DocumentDelivery.CONTENT).toTag())
        .isEqualTo(reference.toTag());
  }

  @Test
  void withDeliveryReturnsACopyWithTheDeliveryAndEverythingElseUnchanged() {
    final var reference =
        new DocumentReferenceContent(
            DocumentId.of("doc-1"),
            DOCUMENT,
            "a.txt",
            "text/plain",
            3L,
            DocumentSource.toolCall("tool", "call_1"),
            DocumentDelivery.REFERENCE,
            Map.of("k", "v"));

    final var withContent = reference.withDelivery(DocumentDelivery.CONTENT);

    assertThat(withContent)
        .isEqualTo(
            new DocumentReferenceContent(
                DocumentId.of("doc-1"),
                DOCUMENT,
                "a.txt",
                "text/plain",
                3L,
                DocumentSource.toolCall("tool", "call_1"),
                DocumentDelivery.CONTENT,
                Map.of("k", "v")));
    assertThat(reference.delivery()).isEqualTo(DocumentDelivery.REFERENCE);
    assertThat(withContent.withDelivery(DocumentDelivery.REFERENCE)).isEqualTo(reference);
  }

  @Test
  void rejectsAMissingId() {
    assertThatThrownBy(() -> reference(null, null, null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void roundTripsThroughContentInterfaceTypeWithTypeDiscriminator() throws Exception {
    final var objectMapper = TestObjectMapperSupplier.INSTANCE;
    final Content reference =
        new DocumentReferenceContent(
            DocumentId.of("doc-1"),
            DOCUMENT,
            "a.txt",
            "text/plain",
            3L,
            DocumentSource.toolCall("tool", "call_1"),
            DocumentDelivery.CONTENT,
            Map.of("k", "v"));

    final var serialized = objectMapper.writeValueAsString(reference);

    assertThat(serialized).contains("\"type\":\"documentReference\"");
    assertThat(serialized).contains("\"delivery\":\"content\"");
    final var read = objectMapper.readValue(serialized, Content.class);
    assertThat(read).isEqualTo(reference);
    assertThat(((DocumentReferenceContent) read).document().asByteArray())
        .isEqualTo("data".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void serializesWithoutAbsentAttributesAndReadsAMissingDeliveryAsReference() throws Exception {
    final var objectMapper = TestObjectMapperSupplier.INSTANCE;
    final var reference = reference("doc-1", null, null, null, "event", null, null);
    final var document = objectMapper.writeValueAsString(DOCUMENT);

    final var serialized = objectMapper.writeValueAsString(reference);

    assertThat(serialized)
        .isEqualTo(
            "{\"type\":\"documentReference\",\"id\":\"doc-1\",\"document\":%s,\"source\":{\"type\":\"EVENT\"},\"delivery\":\"reference\"}"
                .formatted(document));
    assertThat(
            objectMapper.readValue(
                "{\"type\":\"documentReference\",\"id\":\"doc-1\",\"document\":%s,\"source\":{\"type\":\"EVENT\"}}"
                    .formatted(document),
                Content.class))
        .isEqualTo(reference);
  }

  @Test
  void theDocumentIsPersistedWithTheBlockButIsNotPartOfTheTag() throws Exception {
    final var external =
        new ExternalDocument(
            "https://example.com/report.pdf?token=secret", "report.pdf", u -> null);
    final var reference = DocumentReferences.from(external, DocumentSource.userMessage());

    assertThat(TestObjectMapperSupplier.INSTANCE.writeValueAsString(reference))
        .contains("token=secret");
    assertThat(reference.toTag()).doesNotContain("example.com").doesNotContain("secret");
  }

  @Test
  void rejectsAMissingDocument() {
    assertThatThrownBy(
            () ->
                new DocumentReferenceContent(
                    DocumentId.of("doc-1"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    DocumentDelivery.REFERENCE,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void notExpandedFailureNamesTheTypeAndTheExpansion() {
    final var failure = reference("doc-1", null, null, null, null, null, null).notExpandedFailure();

    assertThat(failure)
        .hasMessageContaining("DocumentReferenceContent")
        .hasMessageContaining("doc-1")
        .hasMessageContaining("expanded");
  }
}
