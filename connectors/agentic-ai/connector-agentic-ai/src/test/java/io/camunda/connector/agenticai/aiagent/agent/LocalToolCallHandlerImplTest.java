/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_CHAT_MODEL;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_SYSTEM_PROMPT;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalToolCallHandlerImplTest {

  private static final ToolDefinition TOOL =
      ToolDefinition.builder().name("tool").inputSchema(Map.of()).build();

  private static final ToolCall CALL_A = new ToolCall("a", "tool", Map.of("flag", "bad"));
  private static final ToolCall CALL_B = new ToolCall("b", "tool", Map.of("flag", "ok"));

  private final AgentConversation conversation =
      AgentConversation.rehydrate(
              new AgentConfiguration(
                      TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null)
                  .withToolDefinitions(List.of(TOOL)),
              AgentContext.builder().state(AgentState.READY).build(),
              TurnReconstructor.reconstruct(List.of()),
              null,
              List.of(userMessage("hi")))
          .ingest(assistantMessage("calling", List.of(CALL_A, CALL_B)), AgentMetrics.empty());

  /** Answers calls whose "flag" argument is "bad". */
  private static final ToolCallInspector REJECT_BAD =
      (toolCall, context) ->
          "bad".equals(toolCall.arguments().get("flag"))
              ? new ToolCallVerdict.Answer("rejected")
              : new ToolCallVerdict.Pass();

  @Test
  void leavesEveryCallToTheProcessWithoutInspectors() {
    final var split = new LocalToolCallHandlerImpl(List.of()).handle(conversation, List.of(CALL_A));

    assertThat(split.hasLocalResults()).isFalse();
    assertThat(split.remainingCalls()).containsExactly(CALL_A);
  }

  @Test
  void leavesEveryCallToTheProcessWhenEveryCallPasses() {
    final var split =
        new LocalToolCallHandlerImpl(List.of(REJECT_BAD)).handle(conversation, List.of(CALL_B));

    assertThat(split.hasLocalResults()).isFalse();
    assertThat(split.answersAllCalls()).isFalse();
    assertThat(split.remainingCalls()).containsExactly(CALL_B);
  }

  @Test
  void splitsTheBatchPerCall() {
    final var split =
        new LocalToolCallHandlerImpl(List.of(REJECT_BAD))
            .handle(conversation, List.of(CALL_A, CALL_B));

    assertThat(split.answersAllCalls()).isFalse();
    assertThat(split.remainingCalls()).containsExactly(CALL_B);
    assertThat(split.localResults())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.id()).isEqualTo("a");
              assertThat(result.name()).isEqualTo("tool");
              assertThat(result.content()).isEqualTo("rejected");
              assertThat(result.completedAt()).isNotNull();
            });
  }

  @Test
  void answersEveryCallLocallyWhenNoCallIsLeft() {
    final ToolCallInspector answerAll = (toolCall, context) -> new ToolCallVerdict.Answer("done");

    final var split =
        new LocalToolCallHandlerImpl(List.of(answerAll))
            .handle(conversation, List.of(CALL_A, CALL_B));

    assertThat(split.answersAllCalls()).isTrue();
    assertThat(split.remainingCalls()).isEmpty();
    assertThat(split.localResults()).extracting(r -> r.id()).containsExactly("a", "b");
  }

  @Test
  void keepsTheOrderOfTheCallsInBothParts() {
    final var third = new ToolCall("c", "tool", Map.of("flag", "bad"));
    final var fourth = new ToolCall("d", "tool", Map.of("flag", "ok"));

    final var split =
        new LocalToolCallHandlerImpl(List.of(REJECT_BAD))
            .handle(conversation, List.of(CALL_B, CALL_A, fourth, third));

    assertThat(split.localResults()).extracting(r -> r.id()).containsExactly("a", "c");
    assertThat(split.remainingCalls()).containsExactly(CALL_B, fourth);
  }

  @Test
  void firstAnsweringInspectorWins() {
    final ToolCallInspector other = (toolCall, context) -> new ToolCallVerdict.Answer("other");

    final var split =
        new LocalToolCallHandlerImpl(List.of(REJECT_BAD, other))
            .handle(conversation, List.of(CALL_A));

    assertThat(split.localResults())
        .singleElement()
        .satisfies(r -> assertThat(r.content()).isEqualTo("rejected"));
  }

  @Test
  void collectsTheReturnedDocumentsOfTheAnswers() {
    final ToolCallInspector selecting =
        (toolCall, context) ->
            "a".equals(toolCall.id())
                ? new ToolCallVerdict.Answer("selected", List.of("doc-1", "doc-2"))
                : new ToolCallVerdict.Pass();

    final var split =
        new LocalToolCallHandlerImpl(List.of(selecting))
            .handle(conversation, List.of(CALL_A, CALL_B));

    assertThat(split.returnDocumentIds()).containsExactly("doc-1", "doc-2");
    assertThat(split.localResults()).hasSize(1);
    assertThat(split.remainingCalls()).containsExactly(CALL_B);
  }
}
