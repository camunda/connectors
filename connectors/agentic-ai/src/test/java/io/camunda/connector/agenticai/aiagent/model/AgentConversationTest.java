/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.*;
import static org.assertj.core.api.Assertions.*;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.model.message.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentConversationTest {

  private static final AgentContext BASE_CONTEXT =
      AgentContext.builder().state(AgentState.READY).toolDefinitions(TOOL_DEFINITIONS).build();

  private static final AgentConfiguration CONFIG =
      new AgentConfiguration(null, null, null, null, null, null);

  private static final AgentInvocationInput EMPTY_INPUT =
      AgentInvocationInput.from(null, List.of());

  @Test
  void rehydrate_emptyHistory_producesZeroTurns() {
    var conv = AgentConversation.rehydrate(List.of(), BASE_CONTEXT, EMPTY_INPUT, CONFIG);
    assertThat(conv.turns()).isEmpty();
    assertThat(conv.systemMessage()).isEmpty();
  }

  @Test
  void rehydrate_reconstructsTurnsFromHistory() {
    var messages =
        List.<Message>of(
            userMessage("hi"),
            assistantMessage("thinking", TOOL_CALLS),
            toolCallResultMessage(TOOL_CALL_RESULTS),
            assistantMessage("done"));
    var conv = AgentConversation.rehydrate(messages, BASE_CONTEXT, EMPTY_INPUT, CONFIG);
    assertThat(conv.turns()).hasSize(2);
    assertThat(conv.turns().get(0).iterationKey()).isEqualTo(1);
    assertThat(conv.turns().get(1).iterationKey()).isEqualTo(2);
  }

  @Test
  void updateSystemMessage_returnsNewInstance_withSystemMessage() {
    var conv = AgentConversation.rehydrate(List.of(), BASE_CONTEXT, EMPTY_INPUT, CONFIG);
    var updated = conv.updateSystemMessage("You are helpful.");
    assertThat(updated).isNotSameAs(conv);
    assertThat(updated.systemMessage()).isPresent();
    assertThat(conv.systemMessage()).isEmpty();
  }

  @Test
  void updateSystemMessage_blank_removesSystemMessage() {
    var conv =
        AgentConversation.rehydrate(
            List.of(systemMessage("old")), BASE_CONTEXT, EMPTY_INPUT, CONFIG);
    var updated = conv.updateSystemMessage("");
    assertThat(updated.systemMessage()).isEmpty();
  }

  @Test
  void addNextTurn_returnsNewInstance_withPendingMessages() {
    var conv = AgentConversation.rehydrate(List.of(), BASE_CONTEXT, EMPTY_INPUT, CONFIG);
    var withInput = conv.addNextTurn(List.of(userMessage("hello")));
    assertThat(withInput).isNotSameAs(conv);
    assertThat(withInput.currentTurn().get().inputMessages()).containsExactly(userMessage("hello"));
  }

  @Test
  void ingest_completesNewTurn_andClearsPending() {
    var conv =
        AgentConversation.rehydrate(List.of(), BASE_CONTEXT, EMPTY_INPUT, CONFIG)
            .addNextTurn(List.of(userMessage("hi")));
    var tokenUsage = new TokenUsage(10, 5);
    var response = assistantMessage("hello");
    var ingested = conv.ingest(response, tokenUsage);
    assertThat(ingested.turns()).hasSize(1);
    assertThat(ingested.turns().getFirst().iterationKey()).isEqualTo(1);
    assertThat(ingested.turns().getFirst().assistantMessage()).isEqualTo(response);
    assertThat(ingested.currentTurn().get().assistantMessage()).isEqualTo(response);
  }

  @Test
  void ingest_throwsWhenNoPendingInput() {
    var conv = AgentConversation.rehydrate(List.of(), BASE_CONTEXT, EMPTY_INPUT, CONFIG);
    assertThatThrownBy(() -> conv.ingest(assistantMessage("hi"), TokenUsage.empty()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void allMessages_includesSystemMessageAndTurnMessages() {
    var u = userMessage("hi");
    var a = assistantMessage("hello");
    var conv =
        AgentConversation.rehydrate(
            List.of(systemMessage("sys"), u, a), BASE_CONTEXT, EMPTY_INPUT, CONFIG);
    assertThat(conv.allMessages()).containsExactly(systemMessage("sys"), u, a);
  }

  @Test
  void window_returnsConversationSnapshot_withToolDefinitions() {
    var conv =
        AgentConversation.rehydrate(
            List.of(userMessage("hi"), assistantMessage("hello")),
            BASE_CONTEXT,
            EMPTY_INPUT,
            CONFIG);
    var snapshot = conv.window();
    assertThat(snapshot.toolDefinitions()).isEqualTo(TOOL_DEFINITIONS);
    assertThat(snapshot.messages()).isNotEmpty();
  }

  @Test
  void checkLimits_valid_whenUnderLimit() {
    var conv = AgentConversation.rehydrate(List.of(), BASE_CONTEXT, EMPTY_INPUT, CONFIG);
    var result = conv.checkLimits(new LimitsConfiguration(10));
    assertThat(result.hasViolations()).isFalse();
  }

  @Test
  void checkLimits_violation_whenAtLimit() {
    var metricsCtx = BASE_CONTEXT.withMetrics(AgentMetrics.builder().modelCalls(10).build());
    var conv = AgentConversation.rehydrate(List.of(), metricsCtx, EMPTY_INPUT, CONFIG);
    var result = conv.checkLimits(new LimitsConfiguration(10));
    assertThat(result.hasViolations()).isTrue();
    assertThat(result.violations().getFirst().errorCode())
        .isEqualTo("MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED");
  }

  @Test
  void toAgentContext_updatesMetricsDeltaFromIngestedTurns() {
    var conv =
        AgentConversation.rehydrate(List.of(), BASE_CONTEXT, EMPTY_INPUT, CONFIG)
            .addNextTurn(List.of(userMessage("hi")))
            .ingest(assistantMessage("hello"), new TokenUsage(10, 5));
    var ctx = conv.toAgentContext();
    assertThat(ctx.metrics().modelCalls()).isEqualTo(1);
  }

  @Test
  void expectingToolCallResults_trueWhenLastTurnHasToolCalls() {
    var messages = List.<Message>of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS));
    var conv = AgentConversation.rehydrate(messages, BASE_CONTEXT, EMPTY_INPUT, CONFIG);
    assertThat(conv.expectingToolCallResults()).isTrue();
  }

  @Test
  void expectingToolCallResults_falseWhenNoTurns() {
    var conv = AgentConversation.rehydrate(List.of(), BASE_CONTEXT, EMPTY_INPUT, CONFIG);
    assertThat(conv.expectingToolCallResults()).isFalse();
  }
}
