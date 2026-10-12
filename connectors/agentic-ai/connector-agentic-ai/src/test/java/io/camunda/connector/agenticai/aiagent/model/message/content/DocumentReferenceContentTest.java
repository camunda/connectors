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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.aiagent.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
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

class DocumentReferenceContentTest {

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
            DocumentReferenceContent.from(doc, DocumentSource.toolCall("search", "call_abc", null))
                .toTag())
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

    assertThat(DocumentReferenceContent.from(doc).toTag())
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
            DocumentReferenceContent.from(doc, DocumentSource.toolCall("tool", "call_1", null))
                .toTag())
        .doesNotContain("storeId", "contentHash", "documentId", "metadata", "url");
  }

  @Test
  void camundaDocumentWithoutMetadataOmitsFileNameAndContentType() {
    final var doc =
        documentFactory.create(
            DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8)).build());

    assertThat(DocumentReferenceContent.from(doc).toTag())
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
        DocumentReferenceContent.from(doc, DocumentSource.toolCall("search", "call_abc", null))
            .toTag();

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

    assertThat(DocumentReferenceContent.from(doc).toTag())
        .isEqualTo("<doc id=\"%s\" />".formatted(DocumentHandle.idFor(doc)));
    assertThat(
            DocumentReferenceContent.from(
                    new ExternalDocument("https://example.com/x", "", u -> null))
                .fileName())
        .isNull();
  }

  @Test
  void inlineDocumentWithoutContentTypeOmitsItInsteadOfResolvingItFromTheName() {
    final var doc = new InlineDocument("{\"a\":1}", "data.json", null);

    assertThat(DocumentReferenceContent.from(doc).toTag())
        .isEqualTo("<doc id=\"%s\" fileName=\"data.json\" />".formatted(DocumentHandle.idFor(doc)));
  }

  @Test
  void inlineDocumentRendersNameAndContentType() {
    final var doc = new InlineDocument("{\"a\":1}", "data.json", "application/json");

    assertThat(
            DocumentReferenceContent.from(doc, DocumentSource.toolCall("gen", "call_1", null))
                .toTag())
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
            DocumentReferenceContent.from(doc, DocumentSource.toolCall("tool", "call_1", null))
                .toTag())
        .isEqualTo(expected);
    assertThat(DocumentReferenceContent.from(entry).toTag()).isEqualTo(expected);
    assertThat(DocumentRegistry.of(java.util.List.of(entry)).knownDocumentsText())
        .doesNotContain("fileName");
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
            DocumentReferenceContent.from(
                    doc, DocumentSource.toolCall("tool<with\"quotes>", "call_1", null))
                .toTag())
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

        assertThat(DocumentReferenceContent.from(entry))
            .as("%s from %s", doc.reference(), origin)
            .isEqualTo(
                DocumentReferenceContent.from(
                    doc, DocumentSource.toolCall(toolName, toolCallId, null)));
        assertThat(DocumentReferenceContent.from(entry).toTag())
            .isEqualTo(
                DocumentReferenceContent.from(
                        doc, DocumentSource.toolCall(toolName, toolCallId, null))
                    .toTag());
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

    assertThat(DocumentReferenceContent.from(doc, DocumentSource.userMessage("message-1")).toTag())
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"a.txt\" contentType=\"text/plain\" source=\"user_message\" />"
                .formatted(DocumentHandle.idFor(doc)));
    assertThat(DocumentReferenceContent.from(doc, DocumentSource.event("message-1")).toTag())
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

    assertThat(DocumentReferenceContent.from(entry).toTag()).doesNotContain("message-1");
    assertThat(DocumentReferenceContent.from(doc, DocumentSource.userMessage("message-2")).toTag())
        .doesNotContain("message-2");
  }

  @Test
  void registryEntryOfAUserMessageDocumentRendersTheSource() {
    final var doc = new InlineDocument("{\"a\":1}", "data.json", "application/json");
    final var entry =
        DocumentRegistryEntry.from(new IngestedDocument(doc, DocumentSource.event(null)));

    assertThat(DocumentReferenceContent.from(entry))
        .isEqualTo(DocumentReferenceContent.from(doc, DocumentSource.event(null)));
    assertThat(DocumentReferenceContent.from(entry).source()).isEqualTo("event");
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

    assertThat(DocumentReferenceContent.from(document).toTag())
        .isEqualTo(
            "<doc id=\"doc-1\" fileName=\"report.pdf\" contentType=\"application/pdf\" size=\"48213\" />");
  }

  @Test
  void theSizeIsReadFromTheMetadataWithoutReadingTheDocument() {
    final var document = camundaDocumentWithSize(10L);

    DocumentReferenceContent.from(document, DocumentSource.toolCall("search", "call_1", null));

    verify(document, atLeastOnce()).reference();
    verify(document, atLeastOnce()).metadata();
    verifyNoMoreInteractions(document);
  }

  @Test
  void camundaDocumentWithoutASizeOmitsIt() {
    assertThat(DocumentReferenceContent.from(camundaDocumentWithSize(null)).toTag())
        .doesNotContain("size=");
  }

  @Test
  void externalAndInlineDocumentsNeverRenderASize() {
    assertThat(
            DocumentReferenceContent.from(
                    new ExternalDocument("https://example.com/a.pdf", "a.pdf", u -> null))
                .toTag())
        .doesNotContain("size=");
    assertThat(
            DocumentReferenceContent.from(
                    new InlineDocument("{\"a\":1}", "d.json", "application/json"))
                .toTag())
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

    assertThat(DocumentReferenceContent.from(entry).toTag())
        .isEqualTo(
            "<doc id=\"doc-1\" fileName=\"report.pdf\" contentType=\"application/pdf\" size=\"48213\" source=\"user_message\" />");
    assertThat(
            DocumentReferenceContent.from(
                    new DocumentRegistryEntry(
                        "ext",
                        DocumentSource.userMessage(null),
                        Map.of("camunda.document.type", "external", "size", 5)))
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
    return new DocumentReferenceContent(
        id, fileName, contentType, size, source, toolName, toolCallId, DocumentDelivery.REFERENCE);
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
  void theDeliveryDefaultsToReference() {
    assertThat(
            new DocumentReferenceContent("doc-1", null, null, null, null, null, null, null)
                .delivery())
        .isEqualTo(DocumentDelivery.REFERENCE);
    assertThat(DocumentReferenceContent.from(new InlineDocument("{}", "d.json", null)).delivery())
        .isEqualTo(DocumentDelivery.REFERENCE);
  }

  @Test
  void fromTakesTheGivenDelivery() {
    final var doc = new InlineDocument("{\"a\":1}", "data.json", "application/json");
    final var entry =
        DocumentRegistryEntry.from(new IngestedDocument(doc, DocumentSource.event(null)));

    assertThat(
            DocumentReferenceContent.from(doc, DocumentSource.event(null), DocumentDelivery.CONTENT)
                .delivery())
        .isEqualTo(DocumentDelivery.CONTENT);
    assertThat(DocumentReferenceContent.from(entry, DocumentDelivery.CONTENT).delivery())
        .isEqualTo(DocumentDelivery.CONTENT);
    assertThat(DocumentReferenceContent.from(entry).delivery())
        .isEqualTo(DocumentDelivery.REFERENCE);
  }

  @Test
  void withDeliveryReturnsACopyWithTheDeliveryAndEverythingElseUnchanged() {
    final var reference =
        new DocumentReferenceContent(
            "doc-1",
            "a.txt",
            "text/plain",
            3L,
            null,
            "tool",
            "call_1",
            DocumentDelivery.REFERENCE,
            Map.of("k", "v"));

    final var withContent = reference.withDelivery(DocumentDelivery.CONTENT);

    assertThat(withContent)
        .isEqualTo(
            new DocumentReferenceContent(
                "doc-1",
                "a.txt",
                "text/plain",
                3L,
                null,
                "tool",
                "call_1",
                DocumentDelivery.CONTENT,
                Map.of("k", "v")));
    assertThat(reference.delivery()).isEqualTo(DocumentDelivery.REFERENCE);
    assertThat(withContent.withDelivery(DocumentDelivery.REFERENCE)).isEqualTo(reference);
  }

  @Test
  void rejectsABlankId() {
    assertThatThrownBy(() -> reference(" ", null, null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> reference(null, null, null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void roundTripsThroughContentInterfaceTypeWithTypeDiscriminator() throws Exception {
    final var objectMapper = new ObjectMapper();
    final Content reference =
        new DocumentReferenceContent(
            "doc-1",
            "a.txt",
            "text/plain",
            3L,
            null,
            "tool",
            "call_1",
            DocumentDelivery.CONTENT,
            Map.of("k", "v"));

    final var serialized = objectMapper.writeValueAsString(reference);

    assertThat(serialized).contains("\"type\":\"documentReference\"");
    assertThat(serialized).contains("\"delivery\":\"content\"");
    assertThat(objectMapper.readValue(serialized, Content.class)).isEqualTo(reference);
  }

  @Test
  void serializesWithoutAbsentAttributesAndReadsAMissingDeliveryAsReference() throws Exception {
    final var objectMapper = new ObjectMapper();
    final var reference = reference("doc-1", null, null, null, "event", null, null);

    final var serialized = objectMapper.writeValueAsString(reference);

    assertThat(serialized)
        .isEqualTo(
            "{\"type\":\"documentReference\",\"id\":\"doc-1\",\"source\":\"event\",\"delivery\":\"reference\"}");
    assertThat(
            objectMapper.readValue(
                "{\"type\":\"documentReference\",\"id\":\"doc-1\",\"source\":\"event\"}",
                Content.class))
        .isEqualTo(reference);
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
