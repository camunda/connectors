/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.InlineDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DocumentReferenceXmlTagTest {

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  @Test
  void camundaDocumentRendersIdFileNameContentTypeAndToolOrigin() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                .contentType("application/pdf")
                .fileName("report.pdf")
                .build());

    assertThat(
            DocumentReferenceXmlTag.from(doc, DocumentSource.toolCall("search", "call_abc", null))
                .toXml())
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"report.pdf\" contentType=\"application/pdf\" toolName=\"search\" toolCallId=\"call_abc\" />"
                .formatted(DocumentHandle.idFor(doc)));
  }

  @Test
  void camundaDocumentWithoutToolOriginOmitsToolAttributes() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName("a.txt")
                .build());

    assertThat(DocumentReferenceXmlTag.from(doc).toXml())
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"a.txt\" contentType=\"text/plain\" />"
                .formatted(DocumentHandle.idFor(doc)));
  }

  @Test
  void camundaDocumentNeverExposesStoreIdContentHashOrDocumentIdAttribute() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName("a.txt")
                .build());

    assertThat(
            DocumentReferenceXmlTag.from(doc, DocumentSource.toolCall("tool", "call_1", null))
                .toXml())
        .doesNotContain("storeId", "contentHash", "documentId", "metadata", "url");
  }

  @Test
  void camundaDocumentWithoutMetadataOmitsFileNameAndContentType() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8)).build());

    assertThat(DocumentReferenceXmlTag.from(doc).toXml())
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
        DocumentReferenceXmlTag.from(doc, DocumentSource.toolCall("search", "call_abc", null))
            .toXml();

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

    assertThat(DocumentReferenceXmlTag.from(doc).toXml())
        .isEqualTo("<doc id=\"%s\" />".formatted(DocumentHandle.idFor(doc)));
    assertThat(
            DocumentReferenceXmlTag.from(
                    new ExternalDocument("https://example.com/x", "", u -> null))
                .fileName())
        .isNull();
  }

  @Test
  void inlineDocumentWithoutContentTypeOmitsItInsteadOfResolvingItFromTheName() {
    final var doc = new InlineDocument("{\"a\":1}", "data.json", null);

    assertThat(DocumentReferenceXmlTag.from(doc).toXml())
        .isEqualTo("<doc id=\"%s\" fileName=\"data.json\" />".formatted(DocumentHandle.idFor(doc)));
  }

  @Test
  void inlineDocumentRendersNameAndContentType() {
    final var doc = new InlineDocument("{\"a\":1}", "data.json", "application/json");

    assertThat(
            DocumentReferenceXmlTag.from(doc, DocumentSource.toolCall("gen", "call_1", null))
                .toXml())
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
    final var entry =
        DocumentRegistryEntry.from(
            new IngestedDocument(doc, DocumentSource.toolCall("tool", "call_1", null)));
    final var expected =
        "<doc id=\"0f6b0a1e-aaaa-bbbb-cccc-0123456789ab\" contentType=\"text/plain\""
            + " toolName=\"tool\" toolCallId=\"call_1\" />";

    assertThat(
            DocumentReferenceXmlTag.from(doc, DocumentSource.toolCall("tool", "call_1", null))
                .toXml())
        .isEqualTo(expected);
    assertThat(DocumentReferenceXmlTag.from(entry).toXml()).isEqualTo(expected);
    assertThat(DocumentRegistry.of(java.util.List.of(entry)).knownDocumentsText())
        .doesNotContain("fileName");
  }

  @Test
  void keepsAFileNameThatDiffersFromTheId() {
    assertThat(new DocumentReferenceXmlTag("doc-1", "doc-1.txt", null, null, null, null).toXml())
        .isEqualTo("<doc id=\"doc-1\" fileName=\"doc-1.txt\" />");
  }

  @Test
  void escapesSpecialCharactersInAttributes() {
    final var doc = new ExternalDocument("https://example.com/x", "a<b>\"c\"&.pdf", u -> null);

    assertThat(
            DocumentReferenceXmlTag.from(
                    doc, DocumentSource.toolCall("tool<with\"quotes>", "call_1", null))
                .toXml())
        .contains("fileName=\"a&lt;b&gt;&quot;c&quot;&amp;.pdf\"")
        .contains("toolName=\"tool&lt;with&quot;quotes&gt;\"");
  }

  @Test
  void registryEntryRendersTheSameTagAsTheDocument() {
    final var documents =
        List.<Document>of(
            documentFactory.create(
                DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                    .contentType("text/plain")
                    .fileName("a.txt")
                    .build()),
            documentFactory.create(
                DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8)).build()),
            new ExternalDocument(
                "https://example.com/files/report.pdf?t=secret", "Report", u -> null),
            new ExternalDocument("https://example.com/files/report.pdf?t=secret", null, u -> null),
            new InlineDocument("{\"a\":1}", "data.json", "application/json"),
            new InlineDocument("{\"a\":1}", "data.json", null));

    for (final var doc : documents) {
      for (final var origin : List.of(List.<String>of(), List.of("call_1", "tool"))) {
        final var toolCallId = origin.isEmpty() ? null : origin.get(0);
        final var toolName = origin.isEmpty() ? null : origin.get(1);
        final var entry =
            DocumentRegistryEntry.from(
                new IngestedDocument(doc, DocumentSource.toolCall(toolName, toolCallId, null)));

        assertThat(DocumentReferenceXmlTag.from(entry))
            .as("%s from %s", doc.reference(), origin)
            .isEqualTo(
                DocumentReferenceXmlTag.from(
                    doc, DocumentSource.toolCall(toolName, toolCallId, null)));
        assertThat(DocumentReferenceXmlTag.from(entry).toXml())
            .isEqualTo(
                DocumentReferenceXmlTag.from(
                        doc, DocumentSource.toolCall(toolName, toolCallId, null))
                    .toXml());
      }
    }
  }

  @Test
  void userMessageAndEventDocumentsCarryTheSourceInsteadOfToolAttributes() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName("a.txt")
                .build());

    assertThat(DocumentReferenceXmlTag.from(doc, DocumentSource.userMessage("message-1")).toXml())
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"a.txt\" contentType=\"text/plain\" source=\"user_message\" />"
                .formatted(DocumentHandle.idFor(doc)));
    assertThat(DocumentReferenceXmlTag.from(doc, DocumentSource.event("message-1")).toXml())
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"a.txt\" contentType=\"text/plain\" source=\"event\" />"
                .formatted(DocumentHandle.idFor(doc)));
  }

  @Test
  void neverRendersTheMessageId() {
    final var doc = new InlineDocument("{\"a\":1}", "data.json", "application/json");
    final var entry =
        DocumentRegistryEntry.from(
            new IngestedDocument(doc, DocumentSource.toolCall("tool", "call_1", "message-1")));

    assertThat(DocumentReferenceXmlTag.from(entry).toXml()).doesNotContain("message-1");
    assertThat(DocumentReferenceXmlTag.from(doc, DocumentSource.userMessage("message-2")).toXml())
        .doesNotContain("message-2");
  }

  @Test
  void registryEntryOfAUserMessageDocumentRendersTheSource() {
    final var doc = new InlineDocument("{\"a\":1}", "data.json", "application/json");
    final var entry =
        DocumentRegistryEntry.from(new IngestedDocument(doc, DocumentSource.event(null)));

    assertThat(DocumentReferenceXmlTag.from(entry))
        .isEqualTo(DocumentReferenceXmlTag.from(doc, DocumentSource.event(null)));
    assertThat(DocumentReferenceXmlTag.from(entry).source()).isEqualTo("event");
  }

  @Test
  void toContentMarksTheTagTextAsADocumentReferenceOfTheDocument() {
    final var tag = new DocumentReferenceXmlTag("doc-1", "a.txt", null, "event", null, null);

    final var content = tag.toContent();

    assertThat(content.text()).isEqualTo(tag.toXml());
    assertThat(content.metadata())
        .containsExactly(entry("kind", "document-reference"), entry("documentId", "doc-1"));
    assertThat(DocumentReferenceXmlTag.documentIdOf(content)).isEqualTo("doc-1");
    assertThat(DocumentReferenceXmlTag.documentIdOf(TextContent.textContent(tag.toXml()))).isNull();
    assertThat(
            DocumentReferenceXmlTag.documentIdOf(
                new TextContent(tag.toXml(), Map.of("documentId", "doc-1"))))
        .isNull();
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

    assertThat(DocumentReferenceXmlTag.from(document).toXml())
        .isEqualTo(
            "<doc id=\"doc-1\" fileName=\"report.pdf\" contentType=\"application/pdf\" size=\"48213\" />");
  }

  @Test
  void theSizeIsReadFromTheMetadataWithoutReadingTheDocument() {
    final var document = camundaDocumentWithSize(10L);

    DocumentReferenceXmlTag.from(document, DocumentSource.toolCall("search", "call_1", null));

    verify(document, atLeastOnce()).reference();
    verify(document, atLeastOnce()).metadata();
    verifyNoMoreInteractions(document);
  }

  @Test
  void camundaDocumentWithoutASizeOmitsIt() {
    assertThat(DocumentReferenceXmlTag.from(camundaDocumentWithSize(null)).toXml())
        .doesNotContain("size=");
  }

  @Test
  void externalAndInlineDocumentsNeverRenderASize() {
    assertThat(
            DocumentReferenceXmlTag.from(
                    new ExternalDocument("https://example.com/a.pdf", "a.pdf", u -> null))
                .toXml())
        .doesNotContain("size=");
    assertThat(
            DocumentReferenceXmlTag.from(
                    new InlineDocument("{\"a\":1}", "d.json", "application/json"))
                .toXml())
        .doesNotContain("size=");
  }

  @Test
  void registryEntryRendersTheSizeOfItsCamundaReference() {
    final var entry =
        new DocumentRegistryEntry(
            "doc-1",
            DocumentSource.userMessage(null),
            Map.of(
                "camunda.document.type",
                "camunda",
                "documentId",
                "doc-1",
                "metadata",
                Map.of("fileName", "report.pdf", "contentType", "application/pdf", "size", 48213)));

    assertThat(DocumentReferenceXmlTag.from(entry).toXml())
        .isEqualTo(
            "<doc id=\"doc-1\" fileName=\"report.pdf\" contentType=\"application/pdf\" size=\"48213\" source=\"user_message\" />");
    assertThat(
            DocumentReferenceXmlTag.from(
                    new DocumentRegistryEntry(
                        "ext",
                        DocumentSource.userMessage(null),
                        Map.of("camunda.document.type", "external", "size", 5)))
                .toXml())
        .doesNotContain("size=");
  }

  @Test
  void toLoadedContentAddsTheLoadedMarkerToTheDocumentReferenceMarker() {
    final var tag = new DocumentReferenceXmlTag("doc-1", "a.txt", null, "event", null, null);

    final var content = tag.toLoadedContent();

    assertThat(content.text()).isEqualTo(tag.toXml());
    assertThat(content.metadata())
        .containsExactly(
            entry("kind", "document-reference"),
            entry("documentId", "doc-1"),
            entry("loaded", true));
    assertThat(DocumentReferenceXmlTag.isLoaded(content)).isTrue();
    assertThat(DocumentReferenceXmlTag.isLoaded(tag.toContent())).isFalse();
    assertThat(DocumentReferenceXmlTag.isLoaded(TextContent.textContent(tag.toXml()))).isFalse();
    assertThat(
            DocumentReferenceXmlTag.isLoaded(new TextContent(tag.toXml(), Map.of("loaded", true))))
        .isFalse();
  }
}
