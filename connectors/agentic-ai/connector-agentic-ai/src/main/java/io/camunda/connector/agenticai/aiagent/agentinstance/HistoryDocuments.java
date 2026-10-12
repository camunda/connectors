/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;
import java.util.Objects;

/**
 * What the agent instance history needs to show the arguments of a tool call with the documents
 * they refer to: the tool definitions, which declare the document parameters of a tool, and the
 * registry of the documents the agent knows. The history shows the reference of a document where
 * the model passed its id.
 *
 * <p>The history of an agent instance created without the documents feature has no document
 * registry: its messages are reported as they are stored. The history of an agent instance created
 * without the local tools feature has no local tools either.
 *
 * @param toolDefinitions the tools offered in the invocation (process tools and available local
 *     tools)
 * @param documents the documents known to the agent at the time of the write
 * @param documentFlow whether the agent instance has the documents feature
 * @param localTools whether the agent instance has the local tools feature
 */
public record HistoryDocuments(
    List<ToolDefinition> toolDefinitions,
    DocumentRegistry documents,
    boolean documentFlow,
    boolean localTools) {

  public HistoryDocuments {
    Objects.requireNonNull(toolDefinitions);
    Objects.requireNonNull(documents);
    toolDefinitions = List.copyOf(toolDefinitions);
  }

  /** The documents of an agent instance with the documents feature. */
  public HistoryDocuments(List<ToolDefinition> toolDefinitions, DocumentRegistry documents) {
    this(toolDefinitions, documents, true, true);
  }

  /**
   * The documents of an agent instance created without the documents feature; {@code localTools}
   * tells whether it has the local tools feature.
   */
  public static HistoryDocuments withoutDocumentFlow(
      List<ToolDefinition> toolDefinitions, boolean localTools) {
    return new HistoryDocuments(toolDefinitions, DocumentRegistry.empty(), false, localTools);
  }
}
