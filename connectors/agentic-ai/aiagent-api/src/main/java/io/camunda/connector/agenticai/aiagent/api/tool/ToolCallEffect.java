/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.common.annotation.Experimental;
import io.camunda.connector.api.document.Document;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

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
   * <p>Only for an agent instance that tracks its documents: for any other, the effect fails the
   * job. A document in the content of the result works for every agent instance.
   *
   * @param id the id of a document the agent knows when the batch starts, or of a document the same
   *     result returns ({@link AgentDocumentId#of(Document)}). An unknown id fails the job.
   */
  static ToolCallEffect addDocumentContent(AgentDocumentId id) {
    return new AddDocumentContent(id);
  }

  /** See {@link #addDocumentContent}. */
  record AddDocumentContent(AgentDocumentId id) implements ToolCallEffect {

    public AddDocumentContent {
      Objects.requireNonNull(id, "id must not be null");
    }
  }

  /**
   * A known document is selected for the final response of the agent. Every later final response
   * carries the selection. A selection under a key that is already used replaces the earlier one.
   *
   * <p>Only for an agent instance that tracks its documents: for any other, the effect fails the
   * job.
   *
   * @param key the key of the document in the response, see {@link
   *     ResponseDocumentSelection#KEY_PATTERN}
   * @param id the id of a document the agent knows when the batch starts, or of a document the same
   *     result returns. An unknown id fails the job.
   * @param description a short description of the document, or {@code null}
   * @throws IllegalArgumentException if the key does not match the pattern
   */
  static ToolCallEffect selectResponseDocument(
      String key, AgentDocumentId id, @Nullable String description) {
    return new SelectResponseDocument(new ResponseDocumentSelection(key, id, description));
  }

  /** See {@link #selectResponseDocument}. */
  record SelectResponseDocument(ResponseDocumentSelection selection) implements ToolCallEffect {

    public SelectResponseDocument {
      Objects.requireNonNull(selection, "selection must not be null");
    }
  }
}
