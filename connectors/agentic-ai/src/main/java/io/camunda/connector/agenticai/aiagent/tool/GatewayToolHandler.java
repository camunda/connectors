/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool;

import io.camunda.connector.agenticai.adhoctoolsschema.schema.GatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.aiagent.agent.ContentTreeDocumentWalker;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.api.document.Document;
import java.util.List;

/**
 * A specific gateway tool handler, implementing all the logic necessary to handle gateway tools for
 * a specific type.
 *
 * <p>A single handler is expected to handle gateway tools identified by a specific {@link
 * GatewayToolDefinitionResolver}.
 */
public interface GatewayToolHandler extends GatewayToolCallTransformer {
  String type();

  /**
   * Determines whether a tool is managed by this gateway handler.
   *
   * @param toolName The name of the tool to check
   * @return true if this handler manages the tool definition, false otherwise
   */
  boolean isGatewayManaged(String toolName);

  GatewayToolDiscoveryInitiationResult initiateToolDiscovery(
      AgentContext agentContext, List<GatewayToolDefinition> gatewayToolDefinitions);

  /**
   * Resolves updated gateway tool definitions compared to what is stored on the agent context. Used
   * for tool reconciliation.
   */
  GatewayToolDefinitionUpdates resolveUpdatedGatewayToolDefinitions(
      AgentContext agentContext, List<GatewayToolDefinition> gatewayToolDefinitions);

  /**
   * Determines whether requested tool discovery results are present in the list of tool call
   * results.
   */
  boolean allToolDiscoveryResultsPresent(
      AgentContext agentContext, List<ToolCallResult> toolCallResults);

  /** Defines whether this specific handler is able to handle the given tool call result. */
  boolean handlesToolDiscoveryResult(ToolCallResult toolCallResult);

  /** Handles tool discovery results matching the handlesToolDiscoveryResult() predicate. */
  List<ToolDefinition> handleToolDiscoveryResults(
      AgentContext agentContext, List<ToolCallResult> toolCallResults);

  /**
   * Extracts {@link Document} instances from a tool call result managed by this handler. Called
   * after {@link #transformToolCallResults} so the {@code toolCallResult.content()} carries this
   * handler's transformed shape (typically a typed domain object).
   *
   * <p>The default implementation delegates to {@link ContentTreeDocumentWalker}, which walks
   * {@link java.util.Map}, {@link java.util.Collection}, {@code Object[]} and {@link Document}
   * nodes. This is sufficient for handlers whose transformed content remains a raw tree.
   *
   * <p>Handlers that return typed records or POJOs as content must override this method and walk
   * their own structure (typically via a sealed-type switch). For mixed shapes — typed wrappers
   * around nested raw subtrees — call {@link
   * ContentTreeDocumentWalker#extractDocumentsFromContent(Object)} on the raw parts.
   */
  default List<Document> extractDocuments(ToolCallResult toolCallResult) {
    return ContentTreeDocumentWalker.extractDocumentsFromContent(toolCallResult.content());
  }
}
