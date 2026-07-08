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
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentConversationTest {

  private static final AgentContext BASE_CONTEXT =
      AgentContext.builder().state(AgentState.READY).toolDefinitions(TOOL_DEFINITIONS).build();

  private static final AgentConfiguration CONFIG =
      new AgentConfiguration(null, null, null, null, null, null, null);

  private static AgentConversation rehydrate(
      List<Message> storedMessages, List<Message> inputMessages) {
    var history = TurnReconstructor.reconstruct(storedMessages);
    return AgentConversation.rehydrate(
        CONFIG, BASE_CONTEXT, history, systemMessage("sys"), inputMessages);
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
  void rehydrate_usesStoredLastIterationKey_whenPresent() {
    // stored key (5) disagrees with the reconstructed count (1 turn) on purpose: the stored value
    // must win regardless
    var contextWithStoredKey =
        AgentContext.builder()
            .state(AgentState.READY)
            .metadata(new AgentMetadata(1L, 1L, null, 5))
            .build();
    var storedMessages = List.<Message>of(userMessage("hi"), assistantMessage("hello"));
    var history = TurnReconstructor.reconstruct(storedMessages);
    var conv =
        AgentConversation.rehydrate(
            CONFIG,
            contextWithStoredKey,
            history,
            systemMessage("sys"),
            List.of(userMessage("next")));

    assertThat(conv.currentTurn().iterationKey()).isEqualTo(6);
  }

  @Test
  void rehydrate_fallsBackToReconstructedCount_whenStoredKeyAbsent() {
    var contextWithMetadataNoKey =
        AgentContext.builder()
            .state(AgentState.READY)
            .metadata(new AgentMetadata(1L, 1L, null, null))
            .build();
    var storedMessages = List.<Message>of(userMessage("hi"), assistantMessage("hello"));
    var history = TurnReconstructor.reconstruct(storedMessages);
    var conv =
        AgentConversation.rehydrate(
            CONFIG,
            contextWithMetadataNoKey,
            history,
            systemMessage("sys"),
            List.of(userMessage("next")));

    assertThat(conv.currentTurn().iterationKey()).isEqualTo(2);
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
    var ingested = conv.ingest(response, new AgentMetrics(1, tokenUsage, 0));
    assertThat(ingested.turns()).hasSize(1);
    assertThat(ingested.turns().getFirst().iterationKey()).isEqualTo(1);
    assertThat(ingested.turns().getFirst().assistantMessage()).isEqualTo(response);
    assertThat(ingested.currentTurn().assistantMessage()).isEqualTo(response);
  }

  @Test
  void ingest_throwsWhenAlreadyIngested() {
    var conv =
        rehydrate(List.of(), List.of(userMessage("hi")))
            .ingest(assistantMessage("hello"), AgentMetrics.empty());
    assertThatThrownBy(() -> conv.ingest(assistantMessage("again"), AgentMetrics.empty()))
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
  void allMessages_omitsSystemMessage_whenNull() {
    var history = TurnReconstructor.reconstruct(List.of());
    var conv =
        AgentConversation.rehydrate(
            CONFIG, BASE_CONTEXT, history, null, List.of(userMessage("hi")));
    assertThat(conv.systemMessage()).isNull();
    assertThat(conv.allMessages()).noneMatch(SystemMessage.class::isInstance);
    assertThat(conv.allMessages()).containsExactly(userMessage("hi"));
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
  void toAgentContext_updatesCurrentTurnMetricsFromIngestedTurns() {
    var conv =
        rehydrate(List.of(), List.of(userMessage("hi")))
            .ingest(assistantMessage("hello"), new AgentMetrics(1, new TokenUsage(10, 5), 0));
    var ctx = conv.toAgentContext();
    assertThat(ctx.metrics().modelCalls()).isEqualTo(1);
  }

  @Test
  void toAgentContext_stampsLastIterationKeyOnMetadata() {
    var contextWithMetadata =
        AgentContext.builder()
            .state(AgentState.READY)
            .metadata(new AgentMetadata(1L, 1L, null, null))
            .build();
    var history = TurnReconstructor.reconstruct(List.of());
    var conv =
        AgentConversation.rehydrate(
                CONFIG,
                contextWithMetadata,
                history,
                systemMessage("sys"),
                List.of(userMessage("hi")))
            .ingest(assistantMessage("hello"), AgentMetrics.empty());

    var ctx = conv.toAgentContext();
    assertThat(ctx.metadata().lastIterationKey()).isEqualTo(1);
  }

  @Test
  void toAgentContext_doesNotStampLastIterationKey_whileTurnPending() {
    var contextWithStoredKey =
        AgentContext.builder()
            .state(AgentState.READY)
            .metadata(new AgentMetadata(1L, 1L, null, 4))
            .build();
    var history = TurnReconstructor.reconstruct(List.of());
    var conv =
        AgentConversation.rehydrate(
            CONFIG,
            contextWithStoredKey,
            history,
            systemMessage("sys"),
            List.of(userMessage("hi")));

    assertThat(conv.currentTurn().assistantMessage()).isNull();
    var ctx = conv.toAgentContext();
    assertThat(ctx.metadata().lastIterationKey()).isEqualTo(4);
  }

  @Test
  void toAgentContext_leavesMetadataNull_whenAbsent() {
    var conv =
        rehydrate(List.of(), List.of(userMessage("hi")))
            .ingest(assistantMessage("hello"), AgentMetrics.empty());

    var ctx = conv.toAgentContext();
    assertThat(ctx.metadata()).isNull();
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
            CONFIG,
            contextWithHistory,
            history,
            systemMessage("sys"),
            List.of(userMessage("next")));

    assertThat(conv.turns()).hasSize(2);
    assertThat(conv.turns()).allSatisfy(t -> assertThat(t.metrics().modelCalls()).isZero());
    assertThat(conv.totalMetrics().modelCalls()).isEqualTo(9);
  }

  @Test
  void nextContinuationRound_movesIngestedTurnToPreviousTurns_andOpensPendingTurn() {
    var conv =
        rehydrate(List.of(), List.of(userMessage("hi")))
            .ingest(assistantMessage("partial"), new AgentMetrics(1, new TokenUsage(10, 5), 0));

    var next = conv.nextContinuationRound();

    assertThat(next.turns()).hasSize(1);
    assertThat(next.turns().getFirst().iterationKey()).isEqualTo(1);
    assertThat(next.turns().getFirst().assistantMessage()).isEqualTo(assistantMessage("partial"));

    assertThat(next.currentTurn().iterationKey()).isEqualTo(2);
    assertThat(next.currentTurn().assistantMessage()).isNull();
    assertThat(next.currentTurn().inputMessages()).isEmpty();
  }

  @Test
  void nextContinuationRound_throwsWhenCurrentTurnStillPending() {
    var conv = rehydrate(List.of(), List.of(userMessage("hi")));
    assertThatThrownBy(() -> conv.nextContinuationRound())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void totalMetrics_accumulatesAcrossRoundsWithinOneInvocation() {
    var conv =
        rehydrate(List.of(), List.of(userMessage("hi")))
            .ingest(assistantMessage("partial"), new AgentMetrics(1, new TokenUsage(10, 5), 0))
            .nextContinuationRound()
            .ingest(assistantMessage("done"), new AgentMetrics(1, new TokenUsage(20, 8), 1));

    var total = conv.totalMetrics();
    assertThat(total.modelCalls()).isEqualTo(2);
    assertThat(total.tokenUsage().inputTokenCount()).isEqualTo(30);
    assertThat(total.tokenUsage().outputTokenCount()).isEqualTo(13);
    assertThat(total.toolCalls()).isEqualTo(1);
  }

  @Test
  void lastTurn_whileCurrentPending_isTurnPrecedingCurrent() {
    // while the current turn is still pending, lastTurn() resolves to the turn preceding it — the
    // one whose assistant message requested the tools answered by the current turn's results
    var conv =
        rehydrate(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)),
            List.of(toolCallResultMessage(TOOL_CALL_RESULTS)));

    assertThat(conv.lastTurn()).isPresent();
    assertThat(conv.lastTurn().get().toolCalls()).isEqualTo(TOOL_CALLS);
  }
}
