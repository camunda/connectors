/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AgentMetricsTest {
  private static final AgentMetrics EMPTY_METRICS = AgentMetrics.empty();

  @Test
  void emptyMetrics() {
    final var metrics = AgentMetrics.empty();
    assertThat(metrics.modelCalls()).isEqualTo(0);
    assertThat(metrics.tokenUsage()).isEqualTo(TokenUsage.empty());
    assertThat(metrics).isNotSameAs(EMPTY_METRICS).isEqualTo(EMPTY_METRICS);
  }

  @Test
  void withModelCalls() {
    final var initialMetrics = AgentMetrics.empty();
    final var updatedMetrics = initialMetrics.withModelCalls(1);

    assertThat(updatedMetrics).isNotEqualTo(initialMetrics);
    assertThat(initialMetrics.modelCalls()).isEqualTo(EMPTY_METRICS.modelCalls());

    assertThat(updatedMetrics.modelCalls()).isEqualTo(1);
  }

  @Test
  void incrementModelCalls() {
    final var initialMetrics = AgentMetrics.empty().withModelCalls(1);
    final var updatedMetrics = initialMetrics.incrementModelCalls(3);

    assertThat(updatedMetrics).isNotEqualTo(initialMetrics);
    assertThat(initialMetrics.modelCalls()).isEqualTo(1);

    assertThat(updatedMetrics.modelCalls()).isEqualTo(4);
  }

  @Test
  void withTokenUsage() {
    final var initialMetrics = AgentMetrics.empty();
    final var updatedMetrics = initialMetrics.withTokenUsage(new TokenUsage(10, 20));

    assertThat(updatedMetrics).isNotEqualTo(initialMetrics);
    assertThat(initialMetrics.tokenUsage()).isEqualTo(EMPTY_METRICS.tokenUsage());

    assertThat(updatedMetrics.tokenUsage()).isEqualTo(new TokenUsage(10, 20));
    assertThat(updatedMetrics.tokenUsage().totalTokenCount()).isEqualTo(30);
  }

  @Test
  void incrementTokenUsage() {
    final var initialMetrics = AgentMetrics.empty().withTokenUsage(new TokenUsage(10, 20));
    final var updatedMetrics = initialMetrics.incrementTokenUsage(new TokenUsage(1, 2));

    assertThat(updatedMetrics).isNotEqualTo(initialMetrics);
    assertThat(initialMetrics.tokenUsage()).isEqualTo(new TokenUsage(10, 20));

    assertThat(updatedMetrics.tokenUsage()).isEqualTo(new TokenUsage(11, 22));
    assertThat(updatedMetrics.tokenUsage().totalTokenCount()).isEqualTo(33);
  }

  @Test
  void withToolCalls() {
    final var initialMetrics = AgentMetrics.empty();
    final var updatedMetrics = initialMetrics.withToolCalls(5);

    assertThat(updatedMetrics).isNotEqualTo(initialMetrics);
    assertThat(initialMetrics.toolCalls()).isEqualTo(0);

    assertThat(updatedMetrics.toolCalls()).isEqualTo(5);
  }

  @Test
  void incrementToolCalls() {
    final var initialMetrics = AgentMetrics.empty().withToolCalls(3);
    final var updatedMetrics = initialMetrics.incrementToolCalls(4);

    assertThat(updatedMetrics).isNotEqualTo(initialMetrics);
    assertThat(initialMetrics.toolCalls()).isEqualTo(3);

    assertThat(updatedMetrics.toolCalls()).isEqualTo(7);
  }

  @Test
  void minusAgentMetrics() {
    final var a = new AgentMetrics(5, new TokenUsage(100, 200), 3);
    final var b = new AgentMetrics(2, new TokenUsage(30, 70), 1);

    final var delta = a.minus(b);

    assertThat(delta.modelCalls()).isEqualTo(3);
    assertThat(delta.tokenUsage()).isEqualTo(new TokenUsage(70, 130));
    assertThat(delta.toolCalls()).isEqualTo(2);
  }

  @Test
  void minusAgentMetricsWithZeroYieldsOriginal() {
    final var metrics = new AgentMetrics(2, new TokenUsage(10, 20), 1);
    assertThat(metrics.minus(AgentMetrics.empty())).isEqualTo(metrics);
  }

  @ParameterizedTest
  @MethodSource("negativeDeltaAgentMetrics")
  void minusThrowsWhenResultIsNegative(
      AgentMetrics minuend, AgentMetrics subtrahend, String message) {
    assertThatThrownBy(() -> minuend.minus(subtrahend))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(message);
  }

  static Stream<Arguments> negativeDeltaAgentMetrics() {
    return Stream.of(
        arguments(
            new AgentMetrics(1, TokenUsage.empty(), 0),
            new AgentMetrics(2, TokenUsage.empty(), 0),
            "modelCalls value is negative after subtraction. Actual value: -1"),
        arguments(
            new AgentMetrics(0, TokenUsage.empty(), 1),
            new AgentMetrics(0, TokenUsage.empty(), 2),
            "toolCalls value is negative after subtraction. Actual value: -1"));
  }

  @Test
  void tokenUsageMinusTokenUsage() {
    final var a = new TokenUsage(50, 80);
    final var b = new TokenUsage(20, 30);

    final var delta = a.minus(b);

    assertThat(delta.inputTokenCount()).isEqualTo(30);
    assertThat(delta.outputTokenCount()).isEqualTo(50);
    assertThat(delta.totalTokenCount()).isEqualTo(80);
  }

  @ParameterizedTest
  @MethodSource("negativeDeltaTokenUsage")
  void tokenUsageMinusThrowsWhenResultIsNegative(
      TokenUsage minuend, TokenUsage subtrahend, String message) {
    assertThatThrownBy(() -> minuend.minus(subtrahend))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(message);
  }

  static Stream<Arguments> negativeDeltaTokenUsage() {
    return Stream.of(
        arguments(
            new TokenUsage(10, 0),
            new TokenUsage(20, 0),
            "inputTokenCount value is negative after subtraction. Actual value: -10"),
        arguments(
            new TokenUsage(0, 10),
            new TokenUsage(0, 20),
            "outputTokenCount value is negative after subtraction. Actual value: -10"));
  }

  @ParameterizedTest
  @MethodSource("invalidConstructorParameters")
  void throwsExceptionOnInvalidConstructorParameters(
      int modelCalls,
      TokenUsage tokenUsage,
      int toolCalls,
      Class<? extends Throwable> exceptionClass,
      String exceptionMessage) {
    assertThatThrownBy(() -> new AgentMetrics(modelCalls, tokenUsage, toolCalls))
        .isInstanceOf(exceptionClass)
        .hasMessage(exceptionMessage);
  }

  static Stream<Arguments> invalidConstructorParameters() {
    return Stream.of(
        arguments(
            -10,
            TokenUsage.empty(),
            0,
            IllegalArgumentException.class,
            "Model calls must be non-negative"),
        arguments(10, null, 0, NullPointerException.class, "Token usage must not be null"),
        arguments(
            0,
            TokenUsage.empty(),
            -5,
            IllegalArgumentException.class,
            "Tool calls must be non-negative"));
  }
}
