/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.camunda.connector.agenticai.aiagent.memory.ConversationRecord;
import io.camunda.connector.agenticai.aiagent.memory.InProcessConversationRecord;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AgentContextTest {
  private static final AgentContext EMPTY_CONTEXT =
      new AgentContext(
          AgentState.READY,
          AgentMetrics.empty(),
          List.of(),
          new InProcessConversationRecord(List.of()));

  @Test
  void emptyContext() {
    final var context = AgentContext.empty();
    assertThat(context.state()).isEqualTo(AgentState.READY);
    assertThat(context.metrics()).isEqualTo(AgentMetrics.empty());
    assertThat(context.toolDefinitions()).isEmpty();
    assertThat(context.conversation()).isEqualTo(new InProcessConversationRecord(List.of()));
    assertThat(context).isNotSameAs(EMPTY_CONTEXT).isEqualTo(EMPTY_CONTEXT);
  }

  @Test
  void withState() {
    final var initialContext = AgentContext.empty();
    final var updatedContext = initialContext.withState(AgentState.WAITING_FOR_TOOL_INPUT);

    assertThat(updatedContext).isNotEqualTo(initialContext);
    assertThat(initialContext.state()).isEqualTo(EMPTY_CONTEXT.state());

    assertThat(updatedContext.state()).isEqualTo(AgentState.WAITING_FOR_TOOL_INPUT);
    assertThat(updatedContext.isInState(AgentState.WAITING_FOR_TOOL_INPUT)).isTrue();
    assertThat(updatedContext.isInState(AgentState.READY)).isFalse();
  }

  @Test
  void withMetrics() {
    final var updatedMetrics = new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20));

    final var initialContext = AgentContext.empty();
    final var updatedContext = initialContext.withMetrics(updatedMetrics);

    assertThat(updatedContext).isNotEqualTo(initialContext);
    assertThat(initialContext.metrics()).isEqualTo(EMPTY_CONTEXT.metrics());

    assertThat(updatedContext.metrics())
        .isEqualTo(updatedMetrics)
        .isNotEqualTo(initialContext.metrics());
  }

  @Test
  void withConversation() {
    final var newMessage = UserMessage.userMessage("Hello");
    final var updatedConversation = new InProcessConversationRecord(List.of(newMessage));

    final var initialContext = AgentContext.empty();
    final var updatedContext = initialContext.withConversation(updatedConversation);

    assertThat(updatedContext).isNotEqualTo(initialContext);
    assertThat(initialContext.conversation()).isEqualTo(EMPTY_CONTEXT.conversation());

    assertThat(updatedContext.conversation())
        .isEqualTo(updatedConversation)
        .isNotEqualTo(initialContext.conversation());

    assertThat(((InProcessConversationRecord) updatedContext.conversation()).messages())
        .containsExactly(newMessage);
  }

  @ParameterizedTest
  @MethodSource("invalidConstructorParameters")
  void throwsExceptionOnInvalidConstructorParameters(
      AgentState state,
      AgentMetrics metrics,
      List<ToolDefinition> toolDefinitions,
      ConversationRecord conversation,
      String exceptionMessage) {
    assertThatThrownBy(() -> new AgentContext(state, metrics, toolDefinitions, conversation))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(exceptionMessage);
  }

  static Stream<Arguments> invalidConstructorParameters() {
    final var state = AgentState.READY;
    final var metrics = AgentMetrics.empty();
    final var toolDefinitions = List.of();
    final var conversation = new InProcessConversationRecord(Collections.emptyList());

    return Stream.of(
        arguments(null, metrics, toolDefinitions, conversation, "Agent state must not be null"),
        arguments(state, null, toolDefinitions, conversation, "Agent metrics must not be null"),
        arguments(state, metrics, null, conversation, "Tool definitions must not be null"),
        arguments(state, metrics, toolDefinitions, null, "Agent conversation must not be null"));
  }
}
