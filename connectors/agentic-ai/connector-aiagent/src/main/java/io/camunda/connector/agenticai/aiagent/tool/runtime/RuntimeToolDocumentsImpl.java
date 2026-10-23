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
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentWorkspace;
import io.camunda.connector.agenticai.aiagent.model.document.WorkspaceDocument;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The documents of a workspace, as a runtime tool reads them. */
public final class RuntimeToolDocumentsImpl implements RuntimeToolDocuments {

  private final DocumentWorkspace workspace;
  private final Set<DocumentId> idsWithContent;
  private final ObjectMapper objectMapper;

  /**
   * @param workspace the documents the agent knows
   * @param idsWithContent the ids of the documents whose content the model reads
   * @param objectMapper the mapper that reads a document from its reference
   */
  public RuntimeToolDocumentsImpl(
      DocumentWorkspace workspace, Set<DocumentId> idsWithContent, ObjectMapper objectMapper) {
    this.workspace = workspace;
    this.idsWithContent = Set.copyOf(idsWithContent);
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<AgentDocument> find(DocumentId id) {
    return workspace.findById(id).map(this::documentOf);
  }

  @Override
  public List<AgentDocument> all() {
    return workspace.entries().stream().map(this::documentOf).toList();
  }

  private AgentDocument documentOf(WorkspaceDocument entry) {
    return new AgentDocumentImpl(entry, idsWithContent.contains(entry.id()), objectMapper);
  }

  @Override
  public DocumentReferenceContent reference(DocumentId id) {
    if (!workspace.contains(id)) {
      throw new IllegalArgumentException("The agent does not know the document " + id);
    }
    return new DocumentReferenceContent(id, DocumentDelivery.REFERENCE, null);
  }
}
