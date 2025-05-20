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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AgentContextTest {
  private static final AgentContext EMPTY_CONTEXT =
      new AgentContext(AgentState.READY, AgentMetrics.empty(), List.of());

  @Test
  void emptyContext() {
    final var context = AgentContext.empty();
    assertThat(context.state()).isEqualTo(AgentState.READY);
    assertThat(context.metrics()).isEqualTo(AgentMetrics.empty());
    assertThat(context.memory()).isEmpty();
    assertThat(context).isNotSameAs(EMPTY_CONTEXT).isEqualTo(EMPTY_CONTEXT);
  }

  @Test
  void withState() {
    final var initialContext = AgentContext.empty();
    final var updatedContext = initialContext.withState(AgentState.WAITING_FOR_TOOL_INPUT);

    assertThat(updatedContext).isNotEqualTo(initialContext);
    assertThat(initialContext.state()).isEqualTo(EMPTY_CONTEXT.state());

    assertThat(updatedContext.state()).isEqualTo(AgentState.WAITING_FOR_TOOL_INPUT);
    assertThat(updatedContext.isInState(AgentState.WAITING_FOR_TOOL_INPUT)).isTrue();
    assertThat(updatedContext.isInState(AgentState.READY)).isFalse();
  }

  @Test
  void withMetrics() {
    final var updatedMetrics = new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20));

    final var initialContext = AgentContext.empty();
    final var updatedContext = initialContext.withMetrics(updatedMetrics);

    assertThat(updatedContext).isNotEqualTo(initialContext);
    assertThat(initialContext.metrics()).isEqualTo(EMPTY_CONTEXT.metrics());

    assertThat(updatedContext.metrics())
        .isEqualTo(updatedMetrics)
        .isNotEqualTo(initialContext.metrics());
  }

  @Test
  void withMemory() {
    final var newMessage = Map.of("message", new Object());
    final var updatedMemory = List.of(newMessage);

    final var initialContext = AgentContext.empty();
    final var updatedContext = initialContext.withMemory(updatedMemory);

    assertThat(updatedContext).isNotEqualTo(initialContext);
    assertThat(initialContext.memory()).isEqualTo(EMPTY_CONTEXT.memory());

    assertThat(updatedContext.memory())
        .isEqualTo(updatedMemory)
        .isNotEqualTo(initialContext.memory())
        .containsExactly(newMessage);
  }

  @ParameterizedTest
  @MethodSource("invalidConstructorParameters")
  void throwsExceptionOnInvalidConstructorParameters(
      AgentState state,
      AgentMetrics metrics,
      List<Map<String, Object>> memory,
      String exceptionMessage) {
    assertThatThrownBy(() -> new AgentContext(state, metrics, memory))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(exceptionMessage);
  }

  static Stream<Arguments> invalidConstructorParameters() {
    final var state = AgentState.READY;
    final var metrics = AgentMetrics.empty();
    final var memory = Collections.emptyList();

    return Stream.of(
        arguments(null, metrics, memory, "Agent state must not be null"),
        arguments(state, null, memory, "Agent metrics must not be null"),
        arguments(state, metrics, null, "Agent memory must not be null"));
  }
}
