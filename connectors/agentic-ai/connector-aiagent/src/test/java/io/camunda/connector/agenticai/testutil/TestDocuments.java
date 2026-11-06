/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.testutil;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.agenticai.aiagent.document.DocumentExpander;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandles;
import io.camunda.connector.agenticai.aiagent.document.DocumentRestorer;
import io.camunda.connector.agenticai.aiagent.document.ObjectHandleRenderer;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class TestDocuments {

  private TestDocuments() {}

  /** Restores the documents of the in-memory store. */
  public static DocumentRestorer documentRestorer() {
    return new DocumentRestorer(new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE));
  }

  public static ObjectHandleRenderer objectHandleRenderer() {
    return new ObjectHandleRenderer(TestObjectMapperSupplier.INSTANCE);
  }

  public static DocumentExpander documentExpander() {
    return new DocumentExpander(documentRestorer(), objectHandleRenderer());
  }

  /** The handle text the model reads for the document with the given id of the known documents. */
  public static String handleText(AgentDocuments documents, AgentDocumentId id) {
    return DocumentHandles.text(DocumentHandles.of(documents, id));
  }

  /** The handle text the model reads for the live document. */
  public static String handleText(Document document) {
    return DocumentHandles.text(DocumentHandles.of(document));
  }

  /** The id-only handle text the model reads for a document the agent does not know. */
  public static String unknownHandleText(AgentDocumentId id) {
    return DocumentHandles.text(DocumentHandles.ofUnknown(id));
  }

  /** The handle object that replaces the live document in the object of a tool result. */
  public static Map<String, Object> handleObject(Document document) {
    return TestObjectMapperSupplier.INSTANCE.convertValue(
        DocumentHandles.tree(DocumentHandles.of(document)), new TypeReference<>() {});
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
}
