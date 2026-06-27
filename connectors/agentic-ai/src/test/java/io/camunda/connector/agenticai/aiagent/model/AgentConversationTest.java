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
      new AgentConfiguration(null, null, null, null, null, null, null, null);

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
  void rehydrate_mergesPendingInput_fromOpenTrailingTurn() {
    // Stored conversation ends with an open turn: an in-process tool result was persisted while an
    // external tool call was still pending (mixed turn). On re-entry the external result arrives as
    // the input; both must be merged into the next turn's input.
    var internalResult = toolCallResultMessage(TOOL_CALL_RESULTS);
    var storedMessages =
        List.<Message>of(
            userMessage("load the skill and fetch the file"),
            assistantMessage("calling tools", TOOL_CALLS),
            internalResult);
    var externalResult = toolCallResultMessage(TOOL_CALL_RESULTS);

    var conv = rehydrate(storedMessages, List.of(externalResult));

    assertThat(conv.turns()).hasSize(1); // the single completed turn (the assistant tool-call turn)
    assertThat(conv.currentTurn().assistantMessage()).isNull();
    assertThat(conv.currentTurn().iterationKey()).isEqualTo(2);
    assertThat(conv.currentTurn().inputMessages()).containsExactly(internalResult, externalResult);
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
  void nextTurn_movesCurrentTurnToPrevious_andCreatesPendingTurn() {
    var conv =
        rehydrate(List.of(), List.of(userMessage("hi")))
            .ingest(assistantMessage("thinking"), new AgentMetrics(1, new TokenUsage(5, 10), 0));
    var toolResultMsg = toolCallResultMessage(TOOL_CALL_RESULTS);
    var next = conv.nextTurn(List.of(toolResultMsg));
    assertThat(next.turns()).hasSize(1);
    assertThat(next.currentTurn().assistantMessage()).isNull();
    assertThat(next.currentTurn().inputMessages()).containsExactly(toolResultMsg);
    assertThat(next.currentTurn().iterationKey()).isEqualTo(2);
  }

  @Test
  void nextTurn_throwsWhenCurrentTurnNotComplete() {
    var conv = rehydrate(List.of(), List.of(userMessage("hi")));
    assertThatThrownBy(() -> conv.nextTurn(List.of(userMessage("result"))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void invocationMetrics_sumsAllCompletedTurns() {
    var conv1 =
        rehydrate(List.of(), List.of(userMessage("hi")))
            .ingest(assistantMessage("thinking"), new AgentMetrics(1, new TokenUsage(5, 10), 0));
    var toolResultMsg = toolCallResultMessage(TOOL_CALL_RESULTS);
    var conv2 =
        conv1
            .nextTurn(List.of(toolResultMsg))
            .ingest(assistantMessage("done"), new AgentMetrics(1, new TokenUsage(3, 7), 0));

    assertThat(conv2.invocationMetrics().modelCalls()).isEqualTo(2);
    assertThat(conv2.invocationMetrics().tokenUsage().inputTokenCount()).isEqualTo(8);
    assertThat(conv2.invocationMetrics().tokenUsage().outputTokenCount()).isEqualTo(17);
  }

  @Test
  void invocationMetrics_singleTurn_equalsCurrentTurnMetrics() {
    var turnMetrics = new AgentMetrics(1, new TokenUsage(10, 5), 0);
    var conv =
        rehydrate(List.of(), List.of(userMessage("hi")))
            .ingest(assistantMessage("hello"), turnMetrics);
    assertThat(conv.invocationMetrics()).isEqualTo(conv.currentTurnMetrics());
  }

  @Test
  void totalMetrics_multiTurn_includesAllInvocationMetrics() {
    var contextWithHistory =
        AgentContext.builder()
            .state(AgentState.READY)
            .metrics(new AgentMetrics(9, new TokenUsage(100, 200), 4))
            .build();
    var history = TurnReconstructor.reconstruct(List.of());
    var conv =
        AgentConversation.rehydrate(
            CONFIG, contextWithHistory, history, null, List.of(userMessage("hi")));
    conv = conv.ingest(assistantMessage("thinking"), new AgentMetrics(1, new TokenUsage(5, 10), 0));
    conv = conv.nextTurn(List.of(toolCallResultMessage(TOOL_CALL_RESULTS)));
    conv = conv.ingest(assistantMessage("done"), new AgentMetrics(1, new TokenUsage(3, 7), 0));

    // totalMetrics = contextMetrics(9) + invocationMetrics(2) = 11 model calls
    assertThat(conv.totalMetrics().modelCalls()).isEqualTo(11);
  }

  @Test
  void withContextProperty_storesPropertyInContext() {
    var conv = rehydrate(List.of(), List.of(userMessage("hi")));
    var updated = conv.withContextProperty("myKey", "myValue");
    assertThat(updated.toAgentContext().properties()).containsEntry("myKey", "myValue");
    // original unchanged
    assertThat(conv.toAgentContext().properties()).doesNotContainKey("myKey");
  }
}
