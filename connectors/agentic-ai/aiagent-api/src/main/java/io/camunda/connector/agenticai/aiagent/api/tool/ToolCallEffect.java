/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;

/**
 * What the result of a local tool call asks of the agent besides its content. The agent applies the
 * effects of a batch of tool calls after all calls ran, in call order; a call does not see the
 * effects of another call. Add an effect to a result with {@link
 * RuntimeToolResult.Success#withEffect}. {@link Experimental}, see {@link RuntimeTool}.
 */
@Experimental
public sealed interface ToolCallEffect {

  /**
   * The content of a known document is added to the request that follows the tool results, once per
   * batch, also when an earlier turn added it.
   *
   * @param id the id of a document the agent knows when the batch starts, or of a document the same
   *     result returns ({@link AgentDocumentId#of(Document)}). An unknown id fails the job.
   */
  record AddDocumentContent(AgentDocumentId id) implements ToolCallEffect {}

  /**
   * A known document is selected for the final response of the agent, under the key of the
   * selection. A selection under a key that is already used replaces the earlier one.
   *
   * @param selection the document, its key and its description; the document is one the agent
   *     knows, or one the same result returns
   */
  record SelectResponseDocument(ResponseDocumentSelection selection) implements ToolCallEffect {}
}
