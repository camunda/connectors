/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.api.tool.AgentDocument;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.api.document.DocumentFactory;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The agent documents, as a runtime tool reads them. */
public final class RuntimeToolDocumentsImpl implements RuntimeToolDocuments {

  private final AgentDocuments documents;
  private final Set<AgentDocumentId> idsWithContent;
  private final DocumentFactory documentFactory;

  /**
   * @param documents the documents the agent knows
   * @param idsWithContent the ids of the documents whose content the model reads
   * @param documentFactory restores a document from its reference
   */
  public RuntimeToolDocumentsImpl(
      AgentDocuments documents,
      Set<AgentDocumentId> idsWithContent,
      DocumentFactory documentFactory) {
    this.documents = documents;
    this.idsWithContent = Set.copyOf(idsWithContent);
    this.documentFactory = documentFactory;
  }

  @Override
  public Optional<AgentDocument> find(AgentDocumentId id) {
    return documents.find(id).map(this::documentOf);
  }

  @Override
  public List<AgentDocument> all() {
    return documents.all().stream().map(this::documentOf).toList();
  }

  private AgentDocument documentOf(
      io.camunda.connector.agenticai.aiagent.model.document.AgentDocument agentDocument) {
    return new AgentDocumentImpl(
        agentDocument, idsWithContent.contains(agentDocument.id()), documentFactory);
  }

  @Override
  public AgentDocumentContent reference(AgentDocumentId id) {
    if (documents.find(id).isEmpty()) {
      throw new IllegalArgumentException("The agent does not know the document " + id);
    }
    return AgentDocumentContent.of(id);
  }
}
