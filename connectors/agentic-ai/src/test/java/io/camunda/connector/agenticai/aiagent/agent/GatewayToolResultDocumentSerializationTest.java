/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aArtifact;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTaskStatus;
import io.camunda.connector.agenticai.mcp.client.model.content.McpDocumentContent;
import io.camunda.connector.agenticai.mcp.client.model.content.McpEmbeddedResourceContent;
import io.camunda.connector.agenticai.mcp.client.model.content.McpEmbeddedResourceContent.BlobDocumentResource;
import io.camunda.connector.agenticai.mcp.client.model.content.McpTextContent;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.util.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression test pinning the JSON wire format that the connector ObjectMapper produces for
 * Documents nested inside the typed gateway result objects that {@code McpClientGatewayToolHandler}
 * and {@code A2aGatewayToolHandler} put on the transformed {@code ToolCallResult.content()}.
 *
 * <p>The contract: every {@link Document} reachable from {@code McpClientCallToolResult} or {@code
 * A2aSendMessageResult} must serialize through the standard {@code DocumentSerializer} into a
 * {@code camunda.document.type} reference — never as base64 blob data and never as a raw {@code
 * DocumentReference} POJO. This is the property that lets the LLM correlate references it sees in
 * the tool result text with the actual document content delivered in the synthetic user message
 * (see ADR-004) and that lets the message be persisted/replayed losslessly.
 */
class GatewayToolResultDocumentSerializationTest {

  private final ObjectMapper objectMapper = TestObjectMapperSupplier.INSTANCE;
  private final InMemoryDocumentStore documentStore = InMemoryDocumentStore.INSTANCE;
  private final DocumentFactory documentFactory = new DocumentFactoryImpl(documentStore);

  @BeforeEach
  void resetStore() {
    documentStore.clear();
  }

  @Test
  void mcpDocumentContent_serializesAsCamundaDocumentReference() throws Exception {
    final var document = createDocument("hello", "text/plain", "test.txt");
    final var callToolResult =
        new McpClientCallToolResult(
            "tool1",
            List.of(
                McpTextContent.textContent("intro"), new McpDocumentContent(document, Map.of())),
            false);

    final var json = objectMapper.writeValueAsString(callToolResult);
    final var docNode = findFirstCamundaDocumentNode(objectMapper.readTree(json));

    assertCamundaDocumentReference(docNode, document, "text/plain");
  }

  @Test
  void mcpEmbeddedBlobDocumentResource_serializesAsCamundaDocumentReference() throws Exception {
    final var document = createDocument("<pdf>", "application/pdf", "report.pdf");
    final var callToolResult =
        new McpClientCallToolResult(
            "tool1",
            List.of(
                new McpEmbeddedResourceContent(
                    new BlobDocumentResource("uri://doc", "application/pdf", document), Map.of())),
            false);

    final var json = objectMapper.writeValueAsString(callToolResult);
    final var docNode = findFirstCamundaDocumentNode(objectMapper.readTree(json));

    assertCamundaDocumentReference(docNode, document, "application/pdf");
  }

  @Test
  void a2aMessageDocumentContent_serializesAsCamundaDocumentReference() throws Exception {
    final var document = createDocument("hello", "text/plain", "msg.txt");
    final A2aSendMessageResult message =
        A2aMessage.builder()
            .role(A2aMessage.Role.AGENT)
            .messageId("msg-1")
            .contextId("ctx-1")
            .contents(
                List.of(
                    TextContent.textContent("intro"), DocumentContent.documentContent(document)))
            .build();

    final var json = objectMapper.writeValueAsString(message);
    final var docNode = findFirstCamundaDocumentNode(objectMapper.readTree(json));

    assertCamundaDocumentReference(docNode, document, "text/plain");
  }

  @Test
  void a2aTaskArtifactsAndHistoryDocuments_serializeAsCamundaDocumentReferences() throws Exception {
    final var artifactDoc = createDocument("art", "image/png", "image.png");
    final var historyDoc = createDocument("hist", "application/pdf", "history.pdf");

    final var artifact =
        A2aArtifact.builder()
            .artifactId("art-1")
            .contents(List.of(DocumentContent.documentContent(artifactDoc)))
            .build();
    final var historyMessage =
        A2aMessage.builder()
            .role(A2aMessage.Role.AGENT)
            .messageId("msg-1")
            .contextId("ctx-1")
            .contents(List.of(DocumentContent.documentContent(historyDoc)))
            .build();
    final A2aSendMessageResult task =
        A2aTask.builder()
            .id("task-1")
            .contextId("ctx-1")
            .status(A2aTaskStatus.builder().state(A2aTaskStatus.TaskState.COMPLETED).build())
            .artifacts(List.of(artifact))
            .history(List.of(historyMessage))
            .build();

    final var json = objectMapper.writeValueAsString(task);
    final var rootNode = objectMapper.readTree(json);

    final var artifactDocNode = findFirstCamundaDocumentNode(rootNode.path("artifacts"));
    final var historyDocNode = findFirstCamundaDocumentNode(rootNode.path("history"));

    assertCamundaDocumentReference(artifactDocNode, artifactDoc, "image/png");
    assertCamundaDocumentReference(historyDocNode, historyDoc, "application/pdf");
  }

  @Test
  void serializedJsonContainsNoBase64BlobForCamundaDocuments() throws Exception {
    final var document = createDocument("payload bytes", "application/octet-stream", "blob.bin");
    final var callToolResult =
        new McpClientCallToolResult(
            "tool1", List.of(new McpDocumentContent(document, Map.of())), false);

    final var json = objectMapper.writeValueAsString(callToolResult);

    // base64 of "payload bytes" — must NOT appear in serialized output (would mean the document
    // was inlined as raw blob data instead of a reference).
    final var base64 =
        java.util.Base64.getEncoder()
            .encodeToString("payload bytes".getBytes(StandardCharsets.UTF_8));
    assertThat(json)
        .doesNotContain(base64)
        .contains("camunda.document.type")
        .contains("\"documentId\"");
  }

  private void assertCamundaDocumentReference(
      JsonNode docNode, Document expectedDocument, String expectedContentType) {
    assertThat(docNode).as("camunda document reference node found in JSON").isNotNull();
    assertThat(docNode.path("camunda.document.type").asText())
        .as("camunda.document.type discriminator")
        .isEqualTo("camunda");
    assertThat(expectedDocument.reference()).isInstanceOf(CamundaDocumentReference.class);
    final var camundaReference = (CamundaDocumentReference) expectedDocument.reference();
    assertThat(docNode.path("documentId").asText())
        .as("documentId")
        .isEqualTo(camundaReference.getDocumentId());
    if (expectedContentType != null) {
      assertThat(docNode.path("metadata").path("contentType").asText())
          .as("metadata.contentType")
          .isEqualTo(expectedContentType);
    }
  }

  private static JsonNode findFirstCamundaDocumentNode(JsonNode node) {
    if (node == null || node.isMissingNode()) {
      return null;
    }
    if (node.isObject()) {
      if ("camunda".equals(node.path("camunda.document.type").asText(null))) {
        return node;
      }
      final var fields = node.fields();
      while (fields.hasNext()) {
        final var found = findFirstCamundaDocumentNode(fields.next().getValue());
        if (found != null) {
          return found;
        }
      }
    } else if (node.isArray()) {
      for (var element : node) {
        final var found = findFirstCamundaDocumentNode(element);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private Document createDocument(String content, String contentType, String filename) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(filename)
            .build());
  }
}
