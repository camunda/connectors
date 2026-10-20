/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeTool;
import java.util.List;

/**
 * Splits the tool calls of an assistant message into calls the agent answers itself and calls it
 * hands to the process.
 *
 * <p>Every call is decided on its own. A call of an available {@link RuntimeTool} is answered by
 * the agent; a call of a process tool whose document parameter is not a known document id is
 * answered with an error result; every other call is left to the process. Deciding runs no tool:
 * the answers are computed by {@link RuntimeToolCallSplit#answer()}, in the order of the calls. The
 * request handler proceeds depending on what is left:
 *
 * <ul>
 *   <li>no call is left for the process: the call answers become the input of another round of the
 *       same job and the model is called again;
 *   <li>calls are left for the process: only those are activated and the job ends. The calls the
 *       agent answers itself are answered when the process results arrive, in the job that composes
 *       the next turn input, so the model sees one result per tool call without an extra model
 *       call.
 * </ul>
 */
public interface RuntimeToolCallHandler {

  /**
   * The definitions of the runtime tools available for the agent execution; they are offered to the
   * model next to the tools of the process.
   */
  List<ToolDefinition> availableToolDefinitions(
      AgentExecutionContext executionContext, AgentContext agentContext);

  /**
   * Decides, for each of {@code toolCalls}, whether the agent answers it or the process does. The
   * calls are the tool calls of the last assistant message of {@code conversation}. No tool runs.
   */
  RuntimeToolCallSplit split(
      AgentExecutionContext executionContext,
      AgentConversation conversation,
      List<ToolCall> toolCalls);

  /** The outcome of splitting a batch. */
  interface RuntimeToolCallSplit {

    /** The calls that go to the process, in the order of the calls. */
    List<ToolCall> remainingCalls();

    /** The calls the agent answers itself, in the order of the calls. */
    List<ToolCall> answeredCalls();

    /**
     * Answers {@link #answeredCalls()}: runs the calls of runtime tools one after the other, so a
     * later call sees the context updates of an earlier one, and answers the rejected calls with
     * their error results. The answers are computed from the state of the conversation the split
     * was made for; computing them again gives the same answers.
     */
    RuntimeToolCallAnswers answer();

    /** Returns {@code true} if the agent answers at least one call itself. */
    default boolean hasAnsweredCalls() {
      return !answeredCalls().isEmpty();
    }

    /** Returns {@code true} if the agent answers every call (and there is at least one). */
    default boolean answersAllCalls() {
      return hasAnsweredCalls() && remainingCalls().isEmpty();
    }

    /** A split in which every call goes to the process. */
    static RuntimeToolCallSplit allRemaining(List<ToolCall> toolCalls) {
      final var remainingCalls = List.copyOf(toolCalls);
      return new RuntimeToolCallSplit() {
        @Override
        public List<ToolCall> remainingCalls() {
          return remainingCalls;
        }

        @Override
        public List<ToolCall> answeredCalls() {
          return List.of();
        }

        @Override
        public RuntimeToolCallAnswers answer() {
          return RuntimeToolCallAnswers.none();
        }
      };
    }
  }
}
