/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import org.junit.jupiter.api.Test;

class AgentInstanceUpdateRequestTest {

  @Test
  void statusOnly_setsStatusAndLeavesNullDelta() {
    // given / when
    final var request = AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING);

    // then
    assertThat(request.status()).isEqualTo(AgentInstanceUpdateStatus.THINKING);
    assertThat(request.delta()).isNull();
  }

  @Test
  void builder_withStatusAndDelta() {
    // given
    final var delta = new AgentMetrics(1, new TokenUsage(10, 20), 0);

    // when
    final var request =
        AgentInstanceUpdateRequest.builder()
            .status(AgentInstanceUpdateStatus.IDLE)
            .delta(delta)
            .build();

    // then
    assertThat(request.status()).isEqualTo(AgentInstanceUpdateStatus.IDLE);
    assertThat(request.delta()).isEqualTo(delta);
  }

  @Test
  void builder_withoutStatus_leavesNullStatus() {
    // given
    final var delta = new AgentMetrics(0, TokenUsage.empty(), 2);

    // when
    final var request = AgentInstanceUpdateRequest.builder().delta(delta).build();

    // then
    assertThat(request.status()).isNull();
    assertThat(request.delta()).isEqualTo(delta);
  }
}
