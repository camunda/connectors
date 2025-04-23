/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentContextTest {
  private static final AgentContext EMPTY_CONTEXT = AgentContext.empty();

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
}
