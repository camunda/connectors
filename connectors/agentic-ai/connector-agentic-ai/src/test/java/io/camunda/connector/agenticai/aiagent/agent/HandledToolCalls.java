/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.agent.RuntimeToolCallHandler.RuntimeToolCallSplit;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import java.util.List;

/**
 * The outcome of a batch of tool calls handled in one go, for tests of runtime tools: the split,
 * the answers of the agent and the conversation with the context updates of the answers.
 */
public record HandledToolCalls(
    RuntimeToolCallSplit split, RuntimeToolCallAnswers answers, AgentConversation conversation) {

  /** Splits the batch and answers the calls the agent answers itself. */
  public static HandledToolCalls of(
      RuntimeToolCallHandler handler,
      AgentExecutionContext executionContext,
      AgentConversation conversation,
      List<ToolCall> toolCalls) {
    final var split = handler.split(executionContext, conversation, toolCalls);
    final var answers = split.answer();
    return new HandledToolCalls(split, answers, answers.applyTo(conversation));
  }

  public List<ToolCallResultContent> runtimeResults() {
    return answers.results();
  }

  public List<ToolCall> remainingCalls() {
    return split.remainingCalls();
  }

  public boolean hasRuntimeResults() {
    return split.hasAnsweredCalls();
  }

  public boolean answersAllCalls() {
    return split.answersAllCalls();
  }
}
