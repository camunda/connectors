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
  void answersNothingWithoutInspectors() {
    assertThat(new LocalToolCallHandlerImpl(List.of()).handle(conversation, List.of(CALL_A)))
        .isEmpty();
  }

  @Test
  void answersNothingWhenEveryCallPasses() {
    assertThat(
            new LocalToolCallHandlerImpl(List.of(REJECT_BAD)).handle(conversation, List.of(CALL_B)))
        .isEmpty();
  }

  @Test
  void answersTheWholeBatchWhenOneCallIsAnswered() {
    final var results =
        new LocalToolCallHandlerImpl(List.of(REJECT_BAD))
            .handle(conversation, List.of(CALL_A, CALL_B))
            .orElseThrow();

    assertThat(results)
        .satisfiesExactly(
            first -> {
              assertThat(first.id()).isEqualTo("a");
              assertThat(first.name()).isEqualTo("tool");
              assertThat(first.content()).isEqualTo("rejected");
              assertThat(first.completedAt()).isNotNull();
            },
            second -> {
              assertThat(second.id()).isEqualTo("b");
              assertThat(second.name()).isEqualTo("tool");
              assertThat(second.content()).isEqualTo(LocalToolCallHandlerImpl.NOT_EXECUTED);
              assertThat(second.completedAt()).isNotNull();
            });
  }

  @Test
  void firstAnsweringInspectorWins() {
    final ToolCallInspector other = (toolCall, context) -> new ToolCallVerdict.Answer("other");

    final var results =
        new LocalToolCallHandlerImpl(List.of(REJECT_BAD, other))
            .handle(conversation, List.of(CALL_A))
            .orElseThrow();

    assertThat(results)
        .singleElement()
        .satisfies(r -> assertThat(r.content()).isEqualTo("rejected"));
  }
}
