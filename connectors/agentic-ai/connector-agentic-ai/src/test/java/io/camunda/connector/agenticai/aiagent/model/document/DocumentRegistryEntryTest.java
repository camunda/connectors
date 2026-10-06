/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.document.jackson.DocumentReferenceModel.ExternalDocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.InlineDocumentReferenceModel;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentRegistryEntryTest {

  private final ObjectMapper objectMapper = TestObjectMapperSupplier.INSTANCE;
  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  private Document camundaDocument(String content) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType("application/pdf")
            .fileName("report.pdf")
            .build());
  }

  private static Document documentWith(
      io.camunda.connector.api.document.DocumentReference reference, DocumentMetadata metadata) {
    final var document = mock(Document.class);
    when(document.reference()).thenReturn(reference);
    when(document.metadata()).thenReturn(metadata);
    return document;
  }

  @Test
  void camundaDocumentKeepsTheCompleteReferenceAsSerializedByTheDocumentSerializer()
      throws JsonProcessingException {
    final var document = camundaDocument("hello");

    final var entry =
        DocumentRegistryEntry.from(
            new IngestedDocument(document, DocumentSource.toolCall("search", "call-1", null)));

    assertThat(entry.id()).isEqualTo(DocumentHandle.idFor(document));
    assertThat(entry.source()).isEqualTo(DocumentSource.toolCall("search", "call-1", null));
    assertThat(DocumentHandle.fileNameFor(entry.reference())).isEqualTo("report.pdf");
    assertThat(DocumentHandle.contentTypeFor(entry.reference())).isEqualTo("application/pdf");
    assertThat(entry.reference())
        .containsEntry("camunda.document.type", "camunda")
        .containsEntry("storeId", "in-memory")
        .containsEntry("documentId", entry.id())
        .containsKeys("contentHash", "metadata");

    // the map is the JSON the standard serializer emits: it binds back to an equal document
    final var restored =
        objectMapper.readValue(objectMapper.writeValueAsString(entry.reference()), Document.class);
    assertThat(restored.reference())
        .isInstanceOfSatisfying(
            CamundaDocumentReference.class,
            reference -> {
              final var original = (CamundaDocumentReference) document.reference();
              assertThat(reference.getStoreId()).isEqualTo(original.getStoreId());
              assertThat(reference.getDocumentId()).isEqualTo(original.getDocumentId());
              assertThat(reference.getContentHash()).isEqualTo(original.getContentHash());
            });
    assertThat(restored.metadata().getFileName()).isEqualTo("report.pdf");
  }

  @Test
  void externalDocumentKeepsTheUrl() {
    final var document =
        documentWith(
            new ExternalDocumentReferenceModel("https://example.com/a.pdf?token=secret", "a.pdf"),
            null);

    final var entry =
        DocumentRegistryEntry.from(
            new IngestedDocument(document, DocumentSource.userMessage(null)));

    assertThat(entry.id()).startsWith("ext-");
    assertThat(entry.reference())
        .containsEntry("camunda.document.type", "external")
        .containsEntry("url", "https://example.com/a.pdf?token=secret")
        .containsEntry("name", "a.pdf");
    assertThat(entry.source()).isEqualTo(DocumentSource.userMessage(null));
  }

  @Test
  void sourceOfAnEntryWithoutAToolCallOrMessageIdHasOnlyTheType() throws JsonProcessingException {
    final var entry =
        DocumentRegistryEntry.from(
            new IngestedDocument(camundaDocument("hello"), DocumentSource.event(null)));

    assertThat(
            objectMapper.readTree(objectMapper.writeValueAsString(entry)).get("source").toString())
        .isEqualTo("{\"type\":\"EVENT\"}");
  }

  @Test
  void serializesTheIdThenTheSourceThenTheUntouchedReference() throws JsonProcessingException {
    final var document = camundaDocument("hello");
    final var entry =
        DocumentRegistryEntry.from(
            new IngestedDocument(
                document, DocumentSource.toolCall("search", "call-1", "message-1")));

    final var json = objectMapper.readTree(objectMapper.writeValueAsString(entry));

    assertThat(json.fieldNames()).toIterable().containsExactly("id", "source", "reference");
    assertThat(json.get("id").asText()).isEqualTo(DocumentHandle.idFor(document));
    assertThat(json.get("source").toString())
        .isEqualTo(
            "{\"type\":\"TOOL_CALL\",\"toolName\":\"search\",\"toolCallId\":\"call-1\",\"messageId\":\"message-1\"}");
    assertThat(objectMapper.convertValue(json.get("reference"), Map.class))
        .isEqualTo(entry.reference());
    // no file name or content type next to the reference
    assertThat(json.toString()).doesNotContain("\"fileName\":\"report.pdf\",\"contentType\"");
    assertThat(json.has("fileName")).isFalse();
    assertThat(json.has("contentType")).isFalse();
  }

  @Test
  void unknownPropertiesAreIgnoredWhenReadingAnEntry() throws JsonProcessingException {
    final var json =
        """
        {"id":"d1","future":true,"source":{"type":"TOOL_CALL","toolName":"t","toolCallId":"c","future":1},
         "reference":{"camunda.document.type":"external","url":"https://x/y","name":"y.txt"}}""";

    final var entry = objectMapper.readValue(json, DocumentRegistryEntry.class);

    assertThat(entry.id()).isEqualTo("d1");
    assertThat(entry.source()).isEqualTo(DocumentSource.toolCall("t", "c", null));
    assertThat(entry.reference()).containsEntry("name", "y.txt");
  }

  @Test
  void inlineDocumentKeepsTheContent() {
    final var document =
        documentWith(new InlineDocumentReferenceModel("aGVsbG8=", "a.txt", "text/plain"), null);

    final var entry =
        DocumentRegistryEntry.from(
            new IngestedDocument(document, DocumentSource.userMessage(null)));

    assertThat(entry.id()).startsWith("inline-");
    assertThat(entry.reference())
        .containsEntry("camunda.document.type", "inline")
        .containsEntry("content", "aGVsbG8=");
  }

  @Test
  void entryRoundTripsThroughJson() throws JsonProcessingException {
    final var entry =
        DocumentRegistryEntry.from(
            new IngestedDocument(
                camundaDocument("hello"), DocumentSource.toolCall("search", "call-1", null)));

    final var json = objectMapper.writeValueAsString(entry);

    assertThat(objectMapper.readValue(json, DocumentRegistryEntry.class)).isEqualTo(entry);
  }

  @Test
  void serializedEntrySizes() throws JsonProcessingException {
    final var camundaEntry =
        DocumentRegistryEntry.from(
            new IngestedDocument(
                camundaDocument("hello"),
                DocumentSource.toolCall(
                    "Search_Tool",
                    "call_0123456789abcdef",
                    "0197a000-0000-7000-8000-000000000004")));
    final var externalEntry =
        DocumentRegistryEntry.from(
            new IngestedDocument(
                documentWith(
                    new ExternalDocumentReferenceModel(
                        "https://example.com/files/2026/quarterly-report.pdf",
                        "quarterly-report.pdf"),
                    null),
                DocumentSource.toolCall(
                    "Search_Tool",
                    "call_0123456789abcdef",
                    "0197a000-0000-7000-8000-000000000004")));
    final var inlineEntry =
        DocumentRegistryEntry.from(
            new IngestedDocument(
                documentWith(
                    new InlineDocumentReferenceModel("x".repeat(1024), "inline.txt", "text/plain"),
                    null),
                DocumentSource.toolCall(
                    "Search_Tool",
                    "call_0123456789abcdef",
                    "0197a000-0000-7000-8000-000000000004")));

    final var sizes =
        Map.of(
            "camunda", objectMapper.writeValueAsBytes(camundaEntry).length,
            "external", objectMapper.writeValueAsBytes(externalEntry).length,
            "inline1KB", objectMapper.writeValueAsBytes(inlineEntry).length);
    System.out.println("DOCUMENT REGISTRY ENTRY SIZES " + sizes);

    assertThat(sizes.get("camunda")).isLessThan(700);
    assertThat(sizes.get("external")).isLessThan(400);
    assertThat(sizes.get("inline1KB")).isBetween(1024, 1700);
  }
}
