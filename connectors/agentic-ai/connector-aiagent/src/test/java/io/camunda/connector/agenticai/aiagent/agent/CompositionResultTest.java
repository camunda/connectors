/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CompositionResultTest {

  private static final AgentContext AGENT_CONTEXT =
      AgentContext.builder().state(AgentState.READY).build();
  private static final TurnInput USER_PROMPT =
      new TurnInput(List.of(UserMessage.builder().content(List.of(textContent("Hello"))).build()));

  @Test
  void readyWithAUserPromptHasNoToolCalls() {
    final var ready =
        new CompositionResult.Ready(
            AGENT_CONTEXT, Set.of(), ToolCallBatch.of(List.of()), USER_PROMPT, TurnInput.empty());

    assertThat(ready.userPrompt()).isEqualTo(USER_PROMPT);
    assertThat(ready.unansweredToolCalls()).isEmpty();
  }

  @Test
  void readyRejectsToolCallsTogetherWithAUserPrompt() {
    final var batch = ToolCallBatch.of(List.of(new ToolCall("call-1", "getWeather", Map.of())));

    assertThatThrownBy(() ->
            new CompositionResult.Ready(
                AGENT_CONTEXT, Set.of(), batch, USER_PROMPT, TurnInput.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("The next turn has either tool call results or a user prompt, not both");
  }
}
