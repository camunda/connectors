/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Restores the live document of an agent document, for the model and for the history. */
public final class DocumentRestorer {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentRestorer.class);

  private final DocumentFactory documentFactory;

  public DocumentRestorer(DocumentFactory documentFactory) {
    this.documentFactory = documentFactory;
  }

  /**
   * The document of the agent document with the given id, empty when the agent does not know the
   * id. Restoring reads nothing from the store; a missing document fails where its content is read.
   */
  public Optional<Document> restore(AgentDocuments documents, AgentDocumentId id) {
    final var agentDocument = documents.find(id);
    if (agentDocument.isEmpty()) {
      LOGGER.debug("Document {} is not known to the agent, leaving its handle without content", id);
      return Optional.empty();
    }
    return Optional.of(agentDocument.get().toDocument(documentFactory));
  }
}
