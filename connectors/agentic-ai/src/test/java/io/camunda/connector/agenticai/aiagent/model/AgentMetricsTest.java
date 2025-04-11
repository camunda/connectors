/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AgentMetricsTest {
  private static final AgentMetrics EMPTY_METRICS = AgentMetrics.empty();

  @Test
  void emptyMetrics() {
    final var metrics = AgentMetrics.empty();
    assertEquals(0, metrics.modelCalls());
    assertEquals(0, metrics.tokenUsage());
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
    final var updatedMetrics = initialMetrics.withTokenUsage(2);

    assertThat(updatedMetrics).isNotEqualTo(initialMetrics);
    assertThat(initialMetrics.tokenUsage()).isEqualTo(EMPTY_METRICS.tokenUsage());

    assertThat(updatedMetrics.tokenUsage()).isEqualTo(2);
  }

  @Test
  void incrementTokenUsage() {
    final var initialMetrics = AgentMetrics.empty().withTokenUsage(2);
    final var updatedMetrics = initialMetrics.incrementTokenUsage(3);

    assertThat(updatedMetrics).isNotEqualTo(initialMetrics);
    assertThat(initialMetrics.tokenUsage()).isEqualTo(2);

    assertThat(updatedMetrics.tokenUsage()).isEqualTo(5);
  }
}
