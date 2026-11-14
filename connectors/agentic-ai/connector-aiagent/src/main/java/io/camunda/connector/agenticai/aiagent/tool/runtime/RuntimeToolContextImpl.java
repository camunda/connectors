/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.api.document.Document;
import java.util.Optional;

public record RuntimeToolContextImpl(
    AgentExecutionContext executionContext, AgentContext agentContext)
    implements RuntimeToolContext {

  /** Restores a document of the agent context with the document factory of the job. */
  @Override
  public Optional<Document> document(AgentDocumentId id) {
    return switch (agentContext.documentHandling()) {
      case DocumentHandling.Untracked ignored -> Optional.empty();
      case DocumentHandling.Tracked tracked ->
          tracked
              .documents()
              .find(id)
              .map(document -> document.toDocument(executionContext.documentFactory()));
    };
  }
}
