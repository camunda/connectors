/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.model.message.Message;
import java.util.List;
import org.junit.jupiter.api.Test;

class TurnReconstructorTest {

  @Test
  void emptyMessages_producesNoTurns_andNoSystemMessage() {
    var result = TurnReconstructor.reconstruct(List.of());
    assertThat(result.systemMessage()).isEmpty();
    assertThat(result.turns()).isEmpty();
  }

  @Test
  void extractsSystemMessageAsPreamble() {
    var sys = systemMessage("You are helpful.");
    var u = userMessage("hi");
    var a = assistantMessage("hello");
    var result = TurnReconstructor.reconstruct(List.of(sys, u, a));
    assertThat(result.systemMessage()).contains(sys);
    assertThat(result.turns()).hasSize(1);
    assertThat(result.turns().getFirst().inputMessages()).containsExactly(u);
  }

  @Test
  void singleTurn_noToolCalls() {
    var u = userMessage("hi");
    var a = assistantMessage("hello");
    var result = TurnReconstructor.reconstruct(List.of(u, a));
    assertThat(result.turns()).hasSize(1);
    var turn = result.turns().getFirst();
    assertTurn(turn, 1, false, u);
    assertThat(turn.assistantMessage()).isEqualTo(a);
  }

  @Test
  void twoTurns_withToolCalls() {
    var u = userMessage("what's the weather?");
    var a1 = assistantMessage("let me check", TOOL_CALLS);
    var tcr = toolCallResultMessage(TOOL_CALL_RESULTS);
    var a2 = assistantMessage("It's sunny.");
    var result = TurnReconstructor.reconstruct(List.of(u, a1, tcr, a2));
    assertThat(result.turns()).hasSize(2);
    assertTurn(result.turns().get(0), 1, true, u);
    assertTurn(result.turns().get(1), 2, false, tcr);
  }

  @Test
  void threeTurns_toolCallsThenUserFeedback() {
    var u1 = userMessage("what's the weather?");
    var a1 = assistantMessage("let me check", TOOL_CALLS);
    var tcr = toolCallResultMessage(TOOL_CALL_RESULTS);
    var a2 = assistantMessage("It's sunny.");
    var u2 = userMessage("and tomorrow?");
    var a3 = assistantMessage("Also sunny.");
    var result = TurnReconstructor.reconstruct(List.of(u1, a1, tcr, a2, u2, a3));
    assertThat(result.turns()).hasSize(3);
    assertTurn(result.turns().get(0), 1, true, u1);
    assertTurn(result.turns().get(1), 2, false, tcr);
    assertTurn(result.turns().get(2), 3, false, u2);
    assertThat(result.turns().get(2).assistantMessage()).isEqualTo(a3);
  }

  @Test
  void trailingNonAssistantMessage_throwsIllegalStateException() {
    var u1 = userMessage("hi");
    var a1 = assistantMessage("hello");
    var u2 = userMessage("trailing input not yet answered");
    assertThatThrownBy(() -> TurnReconstructor.reconstruct(List.of(u1, a1, u2)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("UserMessage");
  }

  @Test
  void reconstructsIterationKey_fromPosition() {
    var u1 = userMessage("turn1");
    var a1 = assistantMessage("r1");
    var u2 = userMessage("turn2");
    var a2 = assistantMessage("r2");
    var result = TurnReconstructor.reconstruct(List.of(u1, a1, u2, a2));
    assertThat(result.turns().get(0).iterationKey()).isEqualTo(1);
    assertThat(result.turns().get(1).iterationKey()).isEqualTo(2);
  }

  private static void assertTurn(
      AgentConversationTurn turn,
      int expectedKey,
      boolean expectedHasToolCalls,
      Message... expectedInput) {
    assertThat(turn.iterationKey()).isEqualTo(expectedKey);
    assertThat(turn.inputMessages()).containsExactly(expectedInput);
    assertThat(turn.hasToolCalls()).isEqualTo(expectedHasToolCalls);
  }
}
