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
import io.camunda.connector.agenticai.model.message.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentConversationTest {

  private static final AgentContext BASE_CONTEXT =
      AgentContext.builder().state(AgentState.READY).toolDefinitions(TOOL_DEFINITIONS).build();

  private static final AgentConfiguration CONFIG =
      new AgentConfiguration(null, null, null, null, null, null);

  private static AgentConversation rehydrate(
      List<Message> storedMessages, List<Message> inputMessages) {
    var history = TurnReconstructor.reconstruct(storedMessages);
    return AgentConversation.rehydrate(
        history, systemMessage("sys"), inputMessages, BASE_CONTEXT, CONFIG);
  }

  @Test
  void rehydrate_emptyHistory_producesZeroTurns() {
    var conv = rehydrate(List.of(), List.of(userMessage("hi")));
    assertThat(conv.turns()).isEmpty();
    assertThat(conv.systemMessage()).isEqualTo(systemMessage("sys"));
  }

  @Test
  void rehydrate_reconstructsTurnsFromHistory() {
    var storedMessages =
        List.<Message>of(
            userMessage("hi"),
            assistantMessage("thinking", TOOL_CALLS),
            toolCallResultMessage(TOOL_CALL_RESULTS),
            assistantMessage("done"));
    var conv = rehydrate(storedMessages, List.of(userMessage("next")));
    assertThat(conv.turns()).hasSize(2);
    assertThat(conv.turns().get(0).iterationKey()).isEqualTo(1);
    assertThat(conv.turns().get(1).iterationKey()).isEqualTo(2);
  }

  @Test
  void rehydrate_createsPendingTurn_withInputMessages() {
    var inputMessages = List.<Message>of(userMessage("hello"));
    var conv = rehydrate(List.of(), inputMessages);
    assertThat(conv.currentTurn().inputMessages()).containsExactly(userMessage("hello"));
    assertThat(conv.currentTurn().assistantMessage()).isNull();
  }

  @Test
  void ingest_completesCurrentTurn() {
    var conv = rehydrate(List.of(), List.of(userMessage("hi")));
    var tokenUsage = new TokenUsage(10, 5);
    var response = assistantMessage("hello");
    var ingested = conv.ingest(response, tokenUsage);
    assertThat(ingested.turns()).hasSize(1);
    assertThat(ingested.turns().getFirst().iterationKey()).isEqualTo(1);
    assertThat(ingested.turns().getFirst().assistantMessage()).isEqualTo(response);
    assertThat(ingested.currentTurn().assistantMessage()).isEqualTo(response);
  }

  @Test
  void ingest_throwsWhenAlreadyIngested() {
    var conv =
        rehydrate(List.of(), List.of(userMessage("hi")))
            .ingest(assistantMessage("hello"), TokenUsage.empty());
    assertThatThrownBy(() -> conv.ingest(assistantMessage("again"), TokenUsage.empty()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void allMessages_includesSystemMessageAndTurnMessages() {
    var u = userMessage("hi");
    var a = assistantMessage("hello");
    var storedMessages = List.<Message>of(u, a);
    var conv = rehydrate(storedMessages, List.of(userMessage("next")));
    // systemMessage("sys") + stored turn messages + pending input
    assertThat(conv.allMessages()).contains(systemMessage("sys"), u, a);
  }

  @Test
  void window_returnsConversationSnapshot_withToolDefinitions() {
    var storedMessages = List.<Message>of(userMessage("hi"), assistantMessage("hello"));
    var conv = rehydrate(storedMessages, List.of(userMessage("next")));
    var snapshot = conv.window(20);
    assertThat(snapshot.toolDefinitions()).isEqualTo(TOOL_DEFINITIONS);
    assertThat(snapshot.messages()).isNotEmpty();
  }

  @Test
  void toAgentContext_updatesMetricsDeltaFromIngestedTurns() {
    var conv =
        rehydrate(List.of(), List.of(userMessage("hi")))
            .ingest(assistantMessage("hello"), new TokenUsage(10, 5));
    var ctx = conv.toAgentContext();
    assertThat(ctx.metrics().modelCalls()).isEqualTo(1);
  }

  @Test
  void totalMetrics_readsDurableContextMetrics_notReconstructedTurns() {
    // reconstructed history turns always carry AgentMetrics.empty(); the cumulative model-call
    // count lives on the durable AgentContext and must be what totalMetrics() reports so the
    // model-call limit is enforced across rehydrations.
    var contextWithHistory =
        AgentContext.builder()
            .state(AgentState.READY)
            .metrics(new AgentMetrics(9, new TokenUsage(100, 200), 4))
            .build();
    var storedMessages =
        List.<Message>of(
            userMessage("hi"),
            assistantMessage("first"),
            userMessage("again"),
            assistantMessage("second"));
    var history = TurnReconstructor.reconstruct(storedMessages);
    var conv =
        AgentConversation.rehydrate(
            history,
            systemMessage("sys"),
            List.of(userMessage("next")),
            contextWithHistory,
            CONFIG);

    assertThat(conv.turns()).hasSize(2);
    assertThat(conv.turns()).allSatisfy(t -> assertThat(t.metrics().modelCalls()).isZero());
    assertThat(conv.totalMetrics().modelCalls()).isEqualTo(9);
  }
}
