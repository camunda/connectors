/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.api.tool.AgentDocument;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolDocuments;
import io.camunda.connector.agenticai.aiagent.document.DocumentReferences;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentWorkspace;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import java.util.List;
import java.util.Optional;

/** The documents of a workspace, as a runtime tool reads them. */
public final class RuntimeToolDocumentsImpl implements RuntimeToolDocuments {

  private final DocumentWorkspace workspace;
  private final ObjectMapper objectMapper;

  /**
   * @param workspace the documents the agent knows
   * @param objectMapper the mapper that reads a document from its reference
   */
  public RuntimeToolDocumentsImpl(DocumentWorkspace workspace, ObjectMapper objectMapper) {
    this.workspace = workspace;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<AgentDocument> find(DocumentId id) {
    return workspace.findById(id).map(entry -> new AgentDocumentImpl(entry, objectMapper));
  }

  @Override
  public List<AgentDocument> all() {
    return workspace.entries().stream()
        .<AgentDocument>map(entry -> new AgentDocumentImpl(entry, objectMapper))
        .toList();
  }

  @Override
  public DocumentReferenceContent reference(DocumentId id) {
    return DocumentReferences.from(
        workspace
            .findById(id)
            .orElseThrow(
                () -> new IllegalArgumentException("The agent does not know the document " + id)));
  }
}
