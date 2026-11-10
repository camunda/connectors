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
import java.util.Optional;

/** The agent documents, as a runtime tool reads them. */
public final class RuntimeToolDocumentsImpl implements RuntimeToolDocuments {

  private final AgentDocuments documents;

  /**
   * @param documents the documents the agent knows
   */
  public RuntimeToolDocumentsImpl(AgentDocuments documents) {
    this.documents = documents;
  }

  @Override
  public Optional<AgentDocument> find(AgentDocumentId id) {
    return documents.find(id);
  }
}
