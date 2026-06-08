/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.*;
import static io.camunda.connector.agenticai.model.message.MessageUtil.singleTextContent;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.model.message.*;
import java.util.List;
import java.util.Map;
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
    assertThat(turn.iterationKey()).isEqualTo(1);
    assertThat(turn.inputMessages()).containsExactly(u);
    assertThat(turn.assistantMessage()).isEqualTo(a);
    assertThat(turn.hasToolCalls()).isFalse();
  }

  @Test
  void twoTurns_withToolCalls() {
    var u = userMessage("what's the weather?");
    var a1 = assistantMessage("let me check", TOOL_CALLS);
    var tcr = toolCallResultMessage(TOOL_CALL_RESULTS);
    var a2 = assistantMessage("It's sunny.");
    var result = TurnReconstructor.reconstruct(List.of(u, a1, tcr, a2));
    assertThat(result.turns()).hasSize(2);
    assertThat(result.turns().get(0).iterationKey()).isEqualTo(1);
    assertThat(result.turns().get(0).hasToolCalls()).isTrue();
    assertThat(result.turns().get(1).iterationKey()).isEqualTo(2);
    assertThat(result.turns().get(1).inputMessages()).containsExactly(tcr);
    assertThat(result.turns().get(1).hasToolCalls()).isFalse();
  }

  @Test
  void usesIterationKeyFromMetadata_whenPresent() {
    var u =
        UserMessage.builder()
            .content(singleTextContent("hi"))
            .metadata(Map.of(ConversationTurn.METADATA_ITERATION_KEY, 5))
            .build();
    var a = assistantMessage("hello");
    var result = TurnReconstructor.reconstruct(List.of(u, a));
    assertThat(result.turns().getFirst().iterationKey()).isEqualTo(5);
  }

  @Test
  void reconstructsIterationKey_whenMetadataMissing() {
    var u1 = userMessage("turn1");
    var a1 = assistantMessage("r1");
    var u2 = userMessage("turn2");
    var a2 = assistantMessage("r2");
    var result = TurnReconstructor.reconstruct(List.of(u1, a1, u2, a2));
    assertThat(result.turns().get(0).iterationKey()).isEqualTo(1);
    assertThat(result.turns().get(1).iterationKey()).isEqualTo(2);
  }
}
