/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.api.document.Document;

/**
 * Builds and renders the {@link DocumentHandle} the model reads for a document.
 *
 * <p>The text form is compact JSON with the keys in the order of the handle, stable across requests
 * (prompt caching). The tree form is a JSON object node with the same keys in the same order, for a
 * handle that sits inside the object of a tool result.
 *
 * <p>The file name, content type and size are read from the reference of the document, never by
 * reading the document. A document the agent does not know is a handle with the id only.
 */
public final class DocumentHandles {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private DocumentHandles() {}

  /** The handle of the document with the given id, read from the known documents. */
  public static DocumentHandle of(AgentDocuments documents, AgentDocumentId id) {
    return documents.find(id).map(DocumentHandle::of).orElseGet(() -> ofUnknown(id));
  }

  /**
   * The handle of the given document.
   *
   * @see DocumentHandle#of(Document)
   */
  public static DocumentHandle of(Document document) {
    return DocumentHandle.of(document);
  }

  /** The handle of a document the agent does not know: the id only. */
  public static DocumentHandle ofUnknown(AgentDocumentId id) {
    return DocumentHandle.of(id);
  }

  /** The handle as compact JSON text. */
  public static String text(DocumentHandle handle) {
    try {
      return MAPPER.writeValueAsString(handle);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to write the document handle", e);
    }
  }

  /** The handle as a JSON object node. */
  public static JsonNode tree(DocumentHandle handle) {
    return MAPPER.valueToTree(handle);
  }
}
