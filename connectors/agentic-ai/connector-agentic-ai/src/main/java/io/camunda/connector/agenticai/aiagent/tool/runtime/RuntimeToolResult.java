/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * The outcome of a runtime tool call: the content the model reads as the tool result and,
 * optionally, changes of the agent context.
 *
 * <p>The content is handled like the content of any other tool result: text or an object (a map, a
 * list, a record the mapper can serialize); a document inside the content is shown to the model as
 * its tag. A list of {@link Content} blocks is the content as it is: the model reads each text and
 * each document reference as a part of its own.
 *
 * @param content the content of the tool result
 * @param error {@code true} if the call failed in a way the model can act on; the content is the
 *     message. The model reads an error result as plain text, as no provider has an error flag in
 *     the agent's tool result model.
 * @param contextUpdates changes of the persisted agent context, applied in order; an error result
 *     carries none
 * @param properties properties of the tool call result the agent creates from this result (see
 *     {@code ToolCallResult.properties()}); an error result carries none
 */
public record RuntimeToolResult(
    Object content,
    boolean error,
    List<UnaryOperator<AgentContext>> contextUpdates,
    Map<String, Object> properties) {

  public RuntimeToolResult {
    contextUpdates = error ? List.of() : List.copyOf(contextUpdates);
    properties = error ? Map.of() : Map.copyOf(properties);
  }

  /** A successful result with the given content and no change of the agent context. */
  public static RuntimeToolResult success(Object content) {
    return new RuntimeToolResult(content, false, List.of(), Map.of());
  }

  /** An error result; {@code message} is what the model reads, so it says what to change. */
  public static RuntimeToolResult error(String message) {
    return new RuntimeToolResult(message, true, List.of(), Map.of());
  }

  /**
   * A successful result whose content is the given blocks, text and document references in the
   * order the model reads them, and no change of the agent context.
   */
  public static RuntimeToolResult successWithContent(List<Content> blocks) {
    return new RuntimeToolResult(List.copyOf(blocks), false, List.of(), Map.of());
  }

  /**
   * The content as typed blocks when the tool answered with a non-empty list of them ({@link
   * #successWithContent}), {@code null} for text and object content.
   */
  public @Nullable List<Content> contentBlocks() {
    if (content instanceof List<?> list
        && !list.isEmpty()
        && list.stream().allMatch(Content.class::isInstance)) {
      return list.stream().<Content>map(Content.class::cast).toList();
    }
    return null;
  }

  /**
   * Returns a copy that also applies {@code update} to the persisted agent context. Updates run in
   * call order, one call after the other, so a later call sees the state an earlier one left. The
   * update receives the context as it is at that moment and must not depend on anything else, as it
   * is applied by the framework after the tool returned. Ignored for an error result.
   */
  public RuntimeToolResult withContextUpdate(UnaryOperator<AgentContext> update) {
    final var updates = new ArrayList<>(contextUpdates);
    updates.add(update);
    return new RuntimeToolResult(content, error, updates, properties);
  }

  /** Returns a copy whose tool call result carries the property. Ignored for an error result. */
  public RuntimeToolResult withProperty(String key, Object value) {
    final var updated = new LinkedHashMap<>(properties);
    updated.put(key, value);
    return new RuntimeToolResult(content, error, contextUpdates, updated);
  }
}
