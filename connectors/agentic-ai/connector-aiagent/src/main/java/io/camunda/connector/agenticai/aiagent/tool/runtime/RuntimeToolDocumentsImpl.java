/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.api.document.Document;
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
    return documents.find(id);
  }

  @Override
  public List<AgentDocument> all() {
    return documents.all();
  }

  @Override
  public AgentDocumentContent reference(AgentDocumentId id) {
    return AgentDocumentContent.of(known(id).id());
  }

  @Override
  public Document document(AgentDocumentId id) {
    final var known = known(id);
    try {
      return known.toDocument(documentFactory);
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "The document %s cannot be restored from its reference".formatted(id), e);
    }
  }

  @Override
  public boolean hasContentInConversation(AgentDocumentId id) {
    return idsWithContent.contains(id);
  }

  private AgentDocument known(AgentDocumentId id) {
    return documents
        .find(id)
        .orElseThrow(
            () -> new IllegalArgumentException("The agent does not know the document " + id));
  }
}
