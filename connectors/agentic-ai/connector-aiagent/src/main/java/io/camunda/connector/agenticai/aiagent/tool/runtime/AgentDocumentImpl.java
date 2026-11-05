/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.api.tool.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import org.jspecify.annotations.Nullable;

/**
 * A document the agent knows; the document is restored from its reference when it is asked for.
 * Safe to read from several threads: restoring the reference twice gives equal documents.
 */
final class AgentDocumentImpl implements AgentDocument {

  private final io.camunda.connector.agenticai.aiagent.model.document.AgentDocument agentDocument;
  private final boolean contentInConversation;
  private final DocumentFactory documentFactory;
  private volatile @Nullable Document document;

  AgentDocumentImpl(
      io.camunda.connector.agenticai.aiagent.model.document.AgentDocument agentDocument,
      boolean contentInConversation,
      DocumentFactory documentFactory) {
    this.agentDocument = agentDocument;
    this.contentInConversation = contentInConversation;
    this.documentFactory = documentFactory;
  }

  @Override
  public AgentDocumentId id() {
    return agentDocument.id();
  }

  @Override
  public AgentDocumentOrigin origin() {
    return agentDocument.origin();
  }

  @Override
  public boolean contentInConversation() {
    return contentInConversation;
  }

  @Override
  public Document document() {
    var restored = document;
    if (restored == null) {
      try {
        restored = agentDocument.toDocument(documentFactory);
      } catch (RuntimeException e) {
        throw new IllegalStateException(
            "The document %s cannot be restored from its reference".formatted(agentDocument.id()),
            e);
      }
      document = restored;
    }
    return restored;
  }
}
