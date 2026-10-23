/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolDocuments;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import java.util.Set;

/** The agent as of a call of a runtime tool. */
public record RuntimeToolContextImpl(
    AgentExecutionContext executionContext,
    AgentContext agentContext,
    RuntimeToolDocuments documents)
    implements RuntimeToolContext {

  /**
   * The context of the given agent state. Its documents are the ones of the document registry of
   * {@code agentContext}.
   *
   * @param documentIdsWithContent the ids of the documents whose content the model reads
   * @param objectMapper the mapper that reads a document from its reference
   */
  public static RuntimeToolContextImpl of(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      Set<DocumentId> documentIdsWithContent,
      ObjectMapper objectMapper) {
    return new RuntimeToolContextImpl(
        executionContext,
        agentContext,
        new RuntimeToolDocumentsImpl(
            agentContext.documentRegistry(), documentIdsWithContent, objectMapper));
  }

  /**
   * The context of the given agent state outside a batch of calls, in which no document has its
   * content in the conversation.
   */
  public static RuntimeToolContextImpl of(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      ObjectMapper objectMapper) {
    return of(executionContext, agentContext, Set.of(), objectMapper);
  }
}
