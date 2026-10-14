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
 * @param answers the answer to each call, in the order of the calls
 * @param contextUpdates the changes of the agent context the calls made, in call order
 */
public record RuntimeToolCallAnswers(
    List<RuntimeToolCallAnswer> answers, List<UnaryOperator<AgentContext>> contextUpdates) {

  /**
   * The answer to one tool call.
   *
   * @param result the tool call result that becomes part of the conversation
   * @param documentIdsWithContent the ids of the documents whose content the agent adds to the
   *     request that follows the tool results; held in memory only, never part of the result
   */
  public record RuntimeToolCallAnswer(
      ToolCallResultContent result, List<String> documentIdsWithContent) {

    public RuntimeToolCallAnswer {
      documentIdsWithContent = List.copyOf(documentIdsWithContent);
    }

    /** An answer that adds no document content. */
    public static RuntimeToolCallAnswer of(ToolCallResultContent result) {
      return new RuntimeToolCallAnswer(result, List.of());
    }
  }

  public RuntimeToolCallAnswers {
    answers = List.copyOf(answers);
    contextUpdates = List.copyOf(contextUpdates);
  }

  /** The tool call results of the answers, in the order of the calls. */
  public List<ToolCallResultContent> results() {
    return answers.stream().map(RuntimeToolCallAnswer::result).toList();
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
