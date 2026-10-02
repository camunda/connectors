/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;

/**
 * Conversation state an inspector may consult.
 *
 * @param toolDefinitions the tool definitions in effect for this invocation
 * @param documents the documents known to the agent, including the ones that entered the
 *     conversation with this invocation's input
 * @param returnDocumentsEnabled whether the built-in {@code return_documents} tool is offered
 */
public record LocalToolCallContext(
    List<ToolDefinition> toolDefinitions,
    DocumentRegistry documents,
    boolean returnDocumentsEnabled) {

  public LocalToolCallContext(List<ToolDefinition> toolDefinitions, DocumentRegistry documents) {
    this(toolDefinitions, documents, false);
  }

  public static LocalToolCallContext from(AgentConversation conversation) {
    return new LocalToolCallContext(
        conversation.configuration().toolDefinitions(),
        conversation.documentRegistry(),
        conversation.configuration().returnsDocuments());
  }
}
