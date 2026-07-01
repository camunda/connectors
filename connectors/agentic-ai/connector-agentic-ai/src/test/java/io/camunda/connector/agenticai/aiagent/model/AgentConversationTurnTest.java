/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AgentConversationTurnTest {

  @Test
  void hasToolCalls_returnsTrueWhenAssistantMessageHasToolCalls() {
    var turn =
        new AgentConversationTurn(
            1,
            List.of(userMessage("hi")),
            assistantMessage("thinking", TOOL_CALLS),
            AgentMetrics.empty());
    assertThat(turn.hasToolCalls()).isTrue();
  }

  @Test
  void hasToolCalls_returnsFalseWhenNoToolCalls() {
    var turn =
        new AgentConversationTurn(
            1, List.of(userMessage("hi")), assistantMessage("done"), AgentMetrics.empty());
    assertThat(turn.hasToolCalls()).isFalse();
  }

  @Test
  void hasToolCalls_returnsFalseWhenPending() {
    var turn = new AgentConversationTurn(1, List.of(userMessage("hi")), null, AgentMetrics.empty());
    assertThat(turn.hasToolCalls()).isFalse();
  }
}
