/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.connector.agenticai.aiagent.document.DocumentRestorer;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;

/**
 * How the history of one job shows the documents of the agent.
 *
 * @param handling the document handling of the agent, which decides how the documents of the
 *     messages are shown
 * @param restorer restores the documents from the document store of the physical tenant of the job
 */
public record HistoryDocuments(DocumentHandling handling, DocumentRestorer restorer) {

  public static HistoryDocuments of(
      AgentExecutionContext executionContext, AgentContext agentContext) {
    return new HistoryDocuments(
        agentContext.documentHandling(), new DocumentRestorer(executionContext.documentFactory()));
  }
}
