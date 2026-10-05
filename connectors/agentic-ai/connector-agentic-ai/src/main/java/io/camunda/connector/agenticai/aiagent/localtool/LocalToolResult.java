/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.localtool;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The outcome of a local tool call: the content the model reads as the tool result and, optionally,
 * changes of the agent context.
 *
 * <p>The content is handled like the content of any other tool result: text or an object (a map, a
 * list, a record the mapper can serialize); a document inside the content is shown to the model as
 * its tag.
 *
 * @param content the content of the tool result
 * @param error {@code true} if the call failed in a way the model can act on; the content is the
 *     message. The model reads an error result as plain text, as no provider has an error flag in
 *     the agent's tool result model.
 * @param contextUpdates changes of the persisted agent context, applied in order; an error result
 *     carries none
 */
public record LocalToolResult(
    Object content, boolean error, List<UnaryOperator<AgentContext>> contextUpdates) {

  public LocalToolResult {
    contextUpdates = error ? List.of() : List.copyOf(contextUpdates);
  }

  /** A successful result with the given content and no change of the agent context. */
  public static LocalToolResult success(Object content) {
    return new LocalToolResult(content, false, List.of());
  }

  /** An error result; {@code message} is what the model reads, so it says what to change. */
  public static LocalToolResult error(String message) {
    return new LocalToolResult(message, true, List.of());
  }

  /**
   * Returns a copy that also applies {@code update} to the persisted agent context. Updates run in
   * call order, one call after the other, so a later call sees the state an earlier one left. The
   * update receives the context as it is at that moment and must not depend on anything else, as it
   * is applied by the framework after the tool returned. Ignored for an error result.
   */
  public LocalToolResult withContextUpdate(UnaryOperator<AgentContext> update) {
    final var updates = new ArrayList<>(contextUpdates);
    updates.add(update);
    return new LocalToolResult(content, error, updates);
  }
}
