/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.testutil;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.agenticai.aiagent.document.DocumentExpander;
import io.camunda.connector.agenticai.aiagent.document.ObjectHandleRenderer;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.api.document.Document;
import java.util.Map;

public final class TestDocumentHandles {

  private TestDocumentHandles() {}

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
}
