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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentMetadataModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.InlineDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentDocumentTest {

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

  @Test
  void ofADocumentGetsItsIdAndKeepsTheOrigin() {
    final var document = camundaDocument("hello");

    final var agentDocument =
        AgentDocument.of(document, AgentDocumentOrigin.toolCall("search", "call-1"));

    assertThat(agentDocument.id()).isEqualTo(AgentDocumentId.of(document));
    assertThat(agentDocument.origin()).isEqualTo(AgentDocumentOrigin.toolCall("search", "call-1"));
    assertThat(agentDocument.reference())
        .isInstanceOfSatisfying(
            CamundaDocumentReference.class,
            reference -> {
              final var original = (CamundaDocumentReference) document.reference();
              assertThat(reference.getStoreId()).isEqualTo(original.getStoreId());
              assertThat(reference.getDocumentId()).isEqualTo(original.getDocumentId());
              assertThat(reference.getContentHash()).isEqualTo(original.getContentHash());
              assertThat(reference.getMetadata().getFileName()).isEqualTo("report.pdf");
            });
  }

  @Test
  void restoresTheDocumentWithAFactory() {
    final var document = camundaDocument("hello");

    final var restored =
        AgentDocument.of(document, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))
            .toDocument(documentFactory);

    assertThat(restored.asByteArray()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
    assertThat(restored.metadata().getFileName()).isEqualTo("report.pdf");
    assertThat(AgentDocumentId.of(restored)).isEqualTo(AgentDocumentId.of(document));
  }

  @Test
  void camundaAccessorsReadTheMetadataOfTheReference() {
    final var reference =
        new CamundaDocumentReferenceModel(
            "store",
            "doc-1",
            null,
            new CamundaDocumentMetadataModel(
                "application/pdf", null, 5L, "report.pdf", null, null, null));

    final var agentDocument =
        new AgentDocument(
            AgentDocumentId.of(reference),
            AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID),
            reference);

    assertThat(agentDocument.fileName()).isEqualTo("report.pdf");
    assertThat(agentDocument.contentType()).isEqualTo("application/pdf");
    assertThat(agentDocument.size()).isEqualTo(5L);
  }

  @Test
  void aFileNameThatEqualsTheCamundaDocumentIdIsLeftOut() {
    final var reference =
        new CamundaDocumentReferenceModel(
            "store",
            "doc-1",
            null,
            new CamundaDocumentMetadataModel("text/plain", null, 4L, "doc-1", null, null, null));

    final var agentDocument =
        new AgentDocument(
            AgentDocumentId.of(reference),
            AgentDocumentOrigin.event(TestDocuments.MESSAGE_ID),
            reference);

    assertThat(agentDocument.fileName()).isNull();
    assertThat(agentDocument.contentType()).isEqualTo("text/plain");
    assertThat(agentDocument.size()).isEqualTo(4L);
  }

  @Test
  void aReferenceThatIsNotACamundaReferenceIsRejected() {
    final var documents =
        List.of(
            documentOf(new DocumentReference() {}),
            new ExternalDocument("https://example.com/a.pdf", "a.pdf", u -> null),
            new InlineDocument("hello", "a.txt", "text/plain"));

    for (final var document : documents) {
      assertThatThrownBy(
              () -> AgentDocument.of(document, AgentDocumentOrigin.event(TestDocuments.MESSAGE_ID)))
          .isInstanceOf(UnsupportedDocumentReferenceException.class);
    }
  }

  @Test
  void roundTripsThroughJsonLosslessly() throws JsonProcessingException {
    final var expiresAt = OffsetDateTime.of(2030, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC);
    final var reference =
        new CamundaDocumentReferenceModel(
            "in-memory",
            "doc-1",
            "hash-1",
            new CamundaDocumentMetadataModel(
                "application/pdf",
                expiresAt,
                48213L,
                "report.pdf",
                "process-1",
                4711L,
                Map.of("count", 3, "ratio", 0.5, "owner", "alice")));

    final var agentDocument =
        AgentDocument.of(documentOf(reference), AgentDocumentOrigin.toolCall("search", "call-1"));
    final var read =
        objectMapper.readValue(objectMapper.writeValueAsString(agentDocument), AgentDocument.class);

    assertThat(read).isEqualTo(agentDocument);
    final var metadata = ((CamundaDocumentReference) read.reference()).getMetadata();
    assertThat(metadata.getExpiresAt().toInstant()).isEqualTo(expiresAt.toInstant());
    assertThat(metadata.getSize()).isEqualTo(48213L);
    assertThat(metadata.getProcessInstanceKey()).isEqualTo(4711L);
    assertThat(metadata.getCustomProperties())
        .containsEntry("owner", "alice")
        .containsEntry("count", 3)
        .containsEntry("ratio", 0.5);
  }

  @Test
  void serializesTheIdThenTheOriginThenTheReferenceOfTheSdk() throws JsonProcessingException {
    final var agentDocument =
        AgentDocument.of(
            documentOf(
                new CamundaDocumentReferenceModel(
                    "in-memory",
                    "doc-1",
                    "hash-1",
                    new CamundaDocumentMetadataModel(
                        "application/pdf",
                        OffsetDateTime.of(2030, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC),
                        48213L,
                        "report.pdf",
                        null,
                        null,
                        null))),
            AgentDocumentOrigin.toolCall("search", "call-1"));

    final var json = objectMapper.readTree(objectMapper.writeValueAsString(agentDocument));

    assertThat(json)
        .isEqualTo(
            objectMapper.readTree(
                """
                {"id":"doc_6d97d0f7b602",
                 "origin":{"type":"tool_call","toolName":"search","toolCallId":"call-1"},
                 "reference":{"camunda.document.type":"camunda","storeId":"in-memory",
                   "documentId":"doc-1","contentHash":"hash-1",
                   "metadata":{"contentType":"application/pdf","expiresAt":"2030-01-02T03:04:05Z",
                     "size":48213,"fileName":"report.pdf"}}}\
                """));
    assertThat(json.fieldNames()).toIterable().containsExactly("id", "origin", "reference");
  }

  @Test
  void absentPartsOfTheReferenceStayAbsentAfterARoundTrip() throws JsonProcessingException {
    final var camundaWithoutDetails =
        AgentDocument.of(
            documentOf(new CamundaDocumentReferenceModel("in-memory", "doc-1", null, null)),
            AgentDocumentOrigin.event(TestDocuments.MESSAGE_ID));

    assertThat(
            objectMapper.readValue(
                objectMapper.writeValueAsString(camundaWithoutDetails), AgentDocument.class))
        .isEqualTo(camundaWithoutDetails);
  }

  @Test
  void readsTheStoredJsonOfACamundaReference() throws JsonProcessingException {
    final var camunda =
        objectMapper.readValue(
            """
            {"id":"doc_6d97d0f7b602","origin":{"type":"user_message","messageId":"0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a2b"},
             "reference":{"camunda.document.type":"camunda","storeId":"in-memory",
               "documentId":"doc-1","contentHash":"hash-1",
               "metadata":{"contentType":"application/pdf","size":5,"fileName":"report.pdf"}}}\
            """,
            AgentDocument.class);

    assertThat(AgentDocumentId.of(camunda.reference())).isEqualTo(camunda.id());
    assertThat(camunda.reference()).isInstanceOf(CamundaDocumentReferenceModel.class);
  }

  @Test
  void originOfAnEventHasOnlyTheType() throws JsonProcessingException {
    final var agentDocument =
        AgentDocument.of(
            camundaDocument("hello"), AgentDocumentOrigin.event(TestDocuments.MESSAGE_ID));

    final JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(agentDocument));

    assertThat(json.get("origin").toString())
        .isEqualTo("{\"type\":\"event\",\"messageId\":\"0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a2b\"}");
  }

  @Test
  void unknownPropertiesAreIgnoredWhenReading() throws JsonProcessingException {
    final var json =
        """
        {"id":"d1","future":true,"origin":{"type":"tool_call","toolName":"t","toolCallId":"c","future":1},
         "reference":{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"doc-1",
          "metadata":{"fileName":"y.txt"}}}\
        """;

    final var agentDocument = objectMapper.readValue(json, AgentDocument.class);

    assertThat(agentDocument.id()).isEqualTo(AgentDocumentId.of("d1"));
    assertThat(agentDocument.origin()).isEqualTo(AgentDocumentOrigin.toolCall("t", "c"));
    assertThat(agentDocument.fileName()).isEqualTo("y.txt");
  }

  @Test
  void serializedDocumentsStayWithinTheirSizeBudget() throws JsonProcessingException {
    final var origin = AgentDocumentOrigin.toolCall("Search_Tool", "call_0123456789abcdef");
    final var camunda = AgentDocument.of(camundaDocument("hello"), origin);

    assertThat(objectMapper.writeValueAsBytes(camunda).length).isLessThan(700);
  }

  private static Document documentOf(DocumentReference reference) {
    final var document = mock(Document.class);
    when(document.reference()).thenReturn(reference);
    return document;
  }
}
