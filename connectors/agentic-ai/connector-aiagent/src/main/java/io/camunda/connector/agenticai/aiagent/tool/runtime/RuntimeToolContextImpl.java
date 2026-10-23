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
   * @param objectMapper the mapper that reads a document from its reference
   */
  public static RuntimeToolContextImpl of(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      ObjectMapper objectMapper) {
    return new RuntimeToolContextImpl(
        executionContext,
        agentContext,
        new RuntimeToolDocumentsImpl(agentContext.documentRegistry(), objectMapper));
  }
}
