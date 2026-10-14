/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.agent.RuntimeToolCallAnswers.RuntimeToolCallAnswer;
import io.camunda.connector.agenticai.aiagent.agent.RuntimeToolCallHandler.RuntimeToolCallSplit;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

/**
 * A split whose answered calls and their answers are given, for tests of the code that consumes a
 * split.
 */
final class TestRuntimeToolCallSplit implements RuntimeToolCallSplit {

  private final List<ToolCall> answeredCalls;
  private final RuntimeToolCallAnswers answers;
  private final AtomicInteger answerCount = new AtomicInteger();

  private TestRuntimeToolCallSplit(List<ToolCall> answeredCalls, RuntimeToolCallAnswers answers) {
    this.answeredCalls = List.copyOf(answeredCalls);
    this.answers = answers;
  }

  /** A split that answers the given calls with the given results, in the order of the calls. */
  static TestRuntimeToolCallSplit answering(
      List<ToolCall> answeredCalls, List<ToolCallResultContent> results) {
    return answering(answeredCalls, results, List.of());
  }

  static TestRuntimeToolCallSplit answering(
      List<ToolCall> answeredCalls,
      List<ToolCallResultContent> results,
      List<UnaryOperator<AgentContext>> contextUpdates) {
    return answeringWith(
        answeredCalls, results.stream().map(RuntimeToolCallAnswer::of).toList(), contextUpdates);
  }

  /** A split that answers the given calls with the given answers, in the order of the calls. */
  static TestRuntimeToolCallSplit answeringWith(
      List<ToolCall> answeredCalls,
      List<RuntimeToolCallAnswer> answers,
      List<UnaryOperator<AgentContext>> contextUpdates) {
    return new TestRuntimeToolCallSplit(
        answeredCalls, new RuntimeToolCallAnswers(answers, contextUpdates));
  }

  /** How often the answers were computed. */
  int answerCount() {
    return answerCount.get();
  }

  @Override
  public List<ToolCall> remainingCalls() {
    return List.of();
  }

  @Override
  public List<ToolCall> answeredCalls() {
    return answeredCalls;
  }

  @Override
  public RuntimeToolCallAnswers answer() {
    answerCount.incrementAndGet();
    return answers;
  }
}
