/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolDocuments;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.api.document.DocumentFactory;
import java.util.Set;

public record RuntimeToolContextImpl(
    AgentExecutionContext executionContext,
    AgentContext agentContext,
    RuntimeToolDocuments documents)
    implements RuntimeToolContext {

  /**
   * The context of the given agent state. Its documents are the agent documents of {@code
   * agentContext}.
   *
   * @param documentIdsWithContent the ids of the documents whose content the model reads as of the
   *     start of the batch of calls, none outside a batch
   * @param documentFactory restores a document from its reference
   */
  public static RuntimeToolContextImpl of(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      Set<AgentDocumentId> documentIdsWithContent,
      DocumentFactory documentFactory) {
    return new RuntimeToolContextImpl(
        executionContext,
        agentContext,
        new RuntimeToolDocumentsImpl(
            agentContext.documents(), documentIdsWithContent, documentFactory));
  }
}
