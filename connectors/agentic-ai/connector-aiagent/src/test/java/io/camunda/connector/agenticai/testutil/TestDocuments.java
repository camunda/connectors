/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.testutil;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.agenticai.aiagent.document.DocumentExpander;
import io.camunda.connector.agenticai.aiagent.document.DocumentRestorer;
import io.camunda.connector.agenticai.aiagent.document.ObjectHandleRenderer;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.message.MessageId;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class TestDocuments {

  /** The id of the message that a document of a test entered the conversation with. */
  public static final MessageId MESSAGE_ID = MessageId.of("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a2b");

  private TestDocuments() {}

  /** The document factory of the in-memory store, the document factory of a job in a test. */
  public static DocumentFactory documentFactory() {
    return new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
  }

  /** Restores the documents of the in-memory store. */
  public static DocumentRestorer documentRestorer() {
    return new DocumentRestorer(documentFactory());
  }

  /**
   * The metadata of the user message that holds the documents of tool calls: the timestamp of the
   * message and the marker for the documents.
   */
  public static Map<String, Object> toolCallDocumentsMetadata(UserMessage message) {
    return Map.of(
        "timestamp",
        message.metadata().get("timestamp"),
        UserMessage.METADATA_TOOL_CALL_DOCUMENTS,
        true);
  }

  public static ObjectHandleRenderer objectHandleRenderer() {
    return new ObjectHandleRenderer(TestObjectMapperSupplier.INSTANCE);
  }

  public static DocumentExpander documentExpander() {
    return new DocumentExpander(objectHandleRenderer());
  }

  /** The handle text the model reads for the document with the given id of the known documents. */
  public static String handleText(AgentDocuments documents, AgentDocumentId id) {
    return objectHandleRenderer().text(AgentDocumentHandle.of(documents, id));
  }

  /** The handle text the model reads for the live document. */
  public static String handleText(Document document) {
    return objectHandleRenderer().text(AgentDocumentHandle.of(document));
  }

  /** The id-only handle text the model reads for a document the agent does not know. */
  public static String unknownHandleText(AgentDocumentId id) {
    return objectHandleRenderer().text(AgentDocumentHandle.of(id));
  }

  /** The handle object that replaces the live document in the object of a tool result. */
  public static Map<String, Object> handleObject(Document document) {
    return TestObjectMapperSupplier.INSTANCE.convertValue(
        objectHandleRenderer().tree(AgentDocumentHandle.of(document)), new TypeReference<>() {});
  }

  /** A {@code text/plain} document with the given content and file name in the in-memory store. */
  public static Document createDocument(String content, String fileName) {
    return new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
        .create(
            DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName(fileName)
                .build());
  }

  /** The document reference of an agent document in the JSON format it is persisted in. */
  public static Map<String, Object> referenceJson(AgentDocument document) {
    final var mapper = ConnectorsObjectMapperSupplier.getCopy();
    return mapper.convertValue(
        mapper.valueToTree(document).get("reference"), new TypeReference<>() {});
  }
}
