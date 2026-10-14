/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The answers of the agent to the tool calls it answers itself.
 *
 * @param results the tool call results, in the order of the calls
 * @param contextUpdates the changes of the agent context the calls made, in call order
 */
public record RuntimeToolCallAnswers(
    List<ToolCallResultContent> results, List<UnaryOperator<AgentContext>> contextUpdates) {

  public RuntimeToolCallAnswers {
    results = List.copyOf(results);
    contextUpdates = List.copyOf(contextUpdates);
  }

  /** The answers of a batch the agent answers no call of. */
  public static RuntimeToolCallAnswers none() {
    return new RuntimeToolCallAnswers(List.of(), List.of());
  }

  /** Returns the conversation with the context updates applied. */
  public AgentConversation applyTo(AgentConversation conversation) {
    var updated = conversation;
    for (final var update : contextUpdates) {
      updated = updated.withContextUpdate(update);
    }
    return updated;
  }

  /** Returns the agent context with {@code contextUpdates} applied in order. */
  public static AgentContext applyContextUpdates(
      AgentContext agentContext, List<UnaryOperator<AgentContext>> contextUpdates) {
    var updated = agentContext;
    for (final var update : contextUpdates) {
      updated = update.apply(updated);
    }
    return updated;
  }
}
