/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.conversation;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentConversationTurnTest {

  @Test
  void hasToolCalls_returnsTrueWhenAssistantMessageHasToolCalls() {
    var turn =
        new AgentConversationTurn(
            1,
            List.of(userMessage("hi")),
            assistantMessage("thinking", TOOL_CALLS),
            AgentMetrics.empty(),
            null);
    assertThat(turn.hasToolCalls()).isTrue();
  }

  @Test
  void hasToolCalls_returnsFalseWhenNoToolCalls() {
    var turn =
        new AgentConversationTurn(
            1, List.of(userMessage("hi")), assistantMessage("done"), AgentMetrics.empty(), null);
    assertThat(turn.hasToolCalls()).isFalse();
  }

  @Test
  void hasToolCalls_returnsFalseWhenPending() {
    var turn =
        new AgentConversationTurn(1, List.of(userMessage("hi")), null, AgentMetrics.empty(), null);
    assertThat(turn.hasToolCalls()).isFalse();
  }

  @Test
  void toolCallsById_keysAssistantToolCallsById() {
    var turn =
        new AgentConversationTurn(
            1,
            List.of(userMessage("hi")),
            assistantMessage("thinking", TOOL_CALLS),
            AgentMetrics.empty(),
            null);
    var byId = turn.toolCallsById();
    assertThat(byId).containsOnlyKeys("abcdef", "fedcba");
    assertThat(byId.get("abcdef").arguments()).containsEntry("location", "MUC");
  }

  @Test
  void toolCallsById_emptyWhenNoToolCalls() {
    var turn =
        new AgentConversationTurn(
            1, List.of(userMessage("hi")), assistantMessage("done"), AgentMetrics.empty(), null);
    assertThat(turn.toolCallsById()).isEmpty();
  }

  @Test
  void toolCallsById_emptyWhenPending() {
    var turn =
        new AgentConversationTurn(1, List.of(userMessage("hi")), null, AgentMetrics.empty(), null);
    assertThat(turn.toolCallsById()).isEmpty();
  }

  private static AgentConversationTurn turnWithCalls(ToolCall... calls) {
    return new AgentConversationTurn(
        1,
        List.of(userMessage("hi")),
        assistantMessage("thinking", List.of(calls)),
        AgentMetrics.empty(),
        null);
  }

  @Test
  void groupsToolCallsByKindInCallOrder() {
    var runtimeFirst = new ToolCall("r1", "acme_a", Map.of()).withKind(ToolKind.RUNTIME);
    var process = new ToolCall("p1", "getWeather", Map.of());
    var runtimeSecond = new ToolCall("r2", "acme_b", Map.of()).withKind(ToolKind.RUNTIME);
    var turn = turnWithCalls(runtimeFirst, process, runtimeSecond);

    assertThat(turn.toolCalls()).containsExactly(runtimeFirst, process, runtimeSecond);
    assertThat(turn.runtimeToolCalls()).containsExactly(runtimeFirst, runtimeSecond);
    assertThat(turn.processToolCalls()).containsExactly(process);
    assertThat(turn.hasRuntimeToolCalls()).isTrue();
    assertThat(turn.hasProcessToolCalls()).isTrue();
  }

  @Test
  void aTurnOfRuntimeToolCallsOnlyHasNoProcessToolCalls() {
    var turn = turnWithCalls(new ToolCall("r1", "acme_a", Map.of()).withKind(ToolKind.RUNTIME));

    assertThat(turn.hasRuntimeToolCalls()).isTrue();
    assertThat(turn.hasProcessToolCalls()).isFalse();
    assertThat(turn.processToolCalls()).isEmpty();
  }

  @Test
  void aTurnWithoutToolCallsHasNeitherKind() {
    var turn =
        new AgentConversationTurn(
            1, List.of(userMessage("hi")), assistantMessage("done"), AgentMetrics.empty(), null);
    var pending =
        new AgentConversationTurn(1, List.of(userMessage("hi")), null, AgentMetrics.empty(), null);

    for (var t : List.of(turn, pending)) {
      assertThat(t.hasRuntimeToolCalls()).isFalse();
      assertThat(t.hasProcessToolCalls()).isFalse();
      assertThat(t.runtimeToolCalls()).isEmpty();
      assertThat(t.processToolCalls()).isEmpty();
    }
  }
}
