/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.model.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class A2aSendMessageResultSerializationTest {

  private final ObjectMapper objectMapper = TestObjectMapperSupplier.getInstance();
  private final InMemoryDocumentStore documentStore = InMemoryDocumentStore.INSTANCE;
  private final DocumentFactory documentFactory = new DocumentFactoryImpl(documentStore);

  @BeforeEach
  void setUp() {
    documentStore.clear();
  }

  @Test
  void serializesAndDeserializesMessageWithAllPartTypes() throws Exception {
    var document = createDocument("message document", "text/plain", "message.txt");
    var message =
        A2aMessage.builder()
            .role(A2aMessage.Role.AGENT)
            .messageId("msg-1")
            .contextId("ctx-1")
            .taskId("task-1")
            .referenceTaskIds(List.of("ref-1", "ref-2"))
            .metadata(Map.of("messageKey", "messageValue"))
            .contents(
                List.of(
                    A2aTextPart.textPart("plain text"),
                    new A2aTextPart("text with metadata", Map.of("source", "agent")),
                    A2aDataPart.dataPart(Map.of("field", "value", "count", 42)),
                    new A2aDataPart(List.of("a", "b"), Map.of("kind", "list")),
                    A2aDocumentPart.documentPart(document),
                    new A2aDocumentPart(document, Map.of("role", "attachment"))))
            .build();

    var json = objectMapper.writeValueAsString(message);

    JSONAssert.assertEquals(
        """
        {
          "kind": "message",
          "role": "agent",
          "messageId": "msg-1",
          "contextId": "ctx-1",
          "taskId": "task-1",
          "referenceTaskIds": ["ref-1", "ref-2"],
          "metadata": {"messageKey": "messageValue"},
          "contents": [
            {"type": "text", "text": "plain text"},
            {"type": "text", "text": "text with metadata", "metadata": {"source": "agent"}},
            {"type": "object", "content": {"field": "value", "count": 42}},
            {"type": "object", "content": ["a", "b"], "metadata": {"kind": "list"}},
            {"type": "document", "document": %1$s},
            {"type": "document", "document": %1$s, "metadata": {"role": "attachment"}}
          ]
        }
        """
            .formatted(documentJson(document, "text/plain", "message.txt")),
        json,
        true);

    assertRoundTrip(json, message);
  }

  @Test
  void serializesAndDeserializesMessageWithEmptyMetadata() throws Exception {
    var message =
        A2aMessage.builder()
            .role(A2aMessage.Role.USER)
            .messageId("msg-2")
            .contextId("ctx-2")
            .referenceTaskIds(List.of())
            .metadata(Map.of())
            .contents(
                List.of(
                    new A2aTextPart("empty metadata text", Map.of()),
                    new A2aDataPart(Map.of("k", "v"), Map.of())))
            .build();

    var json = objectMapper.writeValueAsString(message);

    JSONAssert.assertEquals(
        """
        {
          "kind": "message",
          "role": "user",
          "messageId": "msg-2",
          "contextId": "ctx-2",
          "contents": [
            {"type": "text", "text": "empty metadata text"},
            {"type": "object", "content": {"k": "v"}}
          ]
        }
        """,
        json,
        true);

    var deserialized = objectMapper.readValue(json, A2aSendMessageResult.class);
    assertThat(deserialized).isInstanceOf(A2aMessage.class);
    var deserializedMessage = (A2aMessage) deserialized;
    assertThat(deserializedMessage.contents()).hasSize(2);
    assertThat(deserializedMessage.contents().get(0))
        .isEqualTo(A2aTextPart.textPart("empty metadata text"));
    assertThat(deserializedMessage.contents().get(1))
        .isEqualTo(A2aDataPart.dataPart(Map.of("k", "v")));
  }

  @Test
  void serializesAndDeserializesTaskWithArtifactsAndHistory() throws Exception {
    var artifactDocument = createDocument("artifact document", "application/pdf", "report.pdf");
    var historyDocument = createDocument("history document", "text/plain", "history.txt");
    var timestamp = OffsetDateTime.parse("2026-01-02T03:04:05Z");

    var statusMessage =
        A2aMessage.builder()
            .role(A2aMessage.Role.AGENT)
            .messageId("status-msg")
            .contextId("ctx-1")
            .referenceTaskIds(List.of())
            .metadata(Map.of())
            .contents(List.of(A2aTextPart.textPart("Done")))
            .build();

    var artifact =
        A2aArtifact.builder()
            .artifactId("art-1")
            .name("Report")
            .description("The final report")
            .metadata(Map.of("artifactKey", "artifactValue"))
            .contents(
                List.of(
                    A2aTextPart.textPart("artifact text"),
                    new A2aDataPart(Map.of("result", true), Map.of("format", "json")),
                    A2aDocumentPart.documentPart(artifactDocument)))
            .build();

    var emptyArtifact =
        A2aArtifact.builder()
            .artifactId("art-2")
            .metadata(Map.of())
            .contents(List.of(A2aTextPart.textPart("second artifact")))
            .build();

    var historyMessage =
        A2aMessage.builder()
            .role(A2aMessage.Role.USER)
            .messageId("hist-1")
            .contextId("ctx-1")
            .taskId("task-1")
            .referenceTaskIds(List.of())
            .metadata(Map.of("historyKey", "historyValue"))
            .contents(
                List.of(
                    new A2aTextPart("question", Map.of("lang", "en")),
                    A2aDocumentPart.documentPart(historyDocument)))
            .build();

    var task =
        A2aTask.builder()
            .id("task-1")
            .contextId("ctx-1")
            .status(
                A2aTaskStatus.builder()
                    .state(A2aTaskStatus.TaskState.COMPLETED)
                    .message(statusMessage)
                    .timestamp(timestamp)
                    .build())
            .metadata(Map.of("taskKey", "taskValue"))
            .artifacts(List.of(artifact, emptyArtifact))
            .history(List.of(historyMessage))
            .build();

    var json = objectMapper.writeValueAsString(task);

    JSONAssert.assertEquals(
        """
        {
          "kind": "task",
          "id": "task-1",
          "contextId": "ctx-1",
          "status": {
            "state": "completed",
            "message": {
              "kind": "message",
              "role": "agent",
              "messageId": "status-msg",
              "contextId": "ctx-1",
              "contents": [{"type": "text", "text": "Done"}]
            },
            "timestamp": "2026-01-02T03:04:05Z"
          },
          "metadata": {"taskKey": "taskValue"},
          "artifacts": [
            {
              "artifactId": "art-1",
              "name": "Report",
              "description": "The final report",
              "contents": [
                {"type": "text", "text": "artifact text"},
                {"type": "object", "content": {"result": true}, "metadata": {"format": "json"}},
                {"type": "document", "document": %1$s}
              ],
              "metadata": {"artifactKey": "artifactValue"}
            },
            {
              "artifactId": "art-2",
              "contents": [{"type": "text", "text": "second artifact"}]
            }
          ],
          "history": [
            {
              "kind": "message",
              "role": "user",
              "messageId": "hist-1",
              "contextId": "ctx-1",
              "taskId": "task-1",
              "metadata": {"historyKey": "historyValue"},
              "contents": [
                {"type": "text", "text": "question", "metadata": {"lang": "en"}},
                {"type": "document", "document": %2$s}
              ]
            }
          ]
        }
        """
            .formatted(
                documentJson(artifactDocument, "application/pdf", "report.pdf"),
                documentJson(historyDocument, "text/plain", "history.txt")),
        json,
        true);

    assertRoundTrip(json, task);
  }

  private void assertRoundTrip(String json, A2aSendMessageResult original)
      throws JsonProcessingException, JSONException {
    var deserialized = objectMapper.readValue(json, A2aSendMessageResult.class);

    assertThat(deserialized).usingRecursiveComparison().isEqualTo(original);
    JSONAssert.assertEquals(json, objectMapper.writeValueAsString(deserialized), true);
  }

  private Document createDocument(String content, String contentType, String fileName) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(fileName)
            .build());
  }

  private static String documentJson(Document document, String contentType, String fileName) {
    var reference = (CamundaDocumentReference) document.reference();
    return """
        {
          "camunda.document.type": "camunda",
          "storeId": "in-memory",
          "documentId": "%s",
          "contentHash": "%s",
          "metadata": {"contentType": "%s", "fileName": "%s"}
        }
        """
        .formatted(reference.getDocumentId(), reference.getContentHash(), contentType, fileName);
  }
}
