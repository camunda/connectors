/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AgentContextTest {
  private static final AgentContext EMPTY_CONTEXT = AgentContext.empty();

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void emptyContext() {
    final var context = AgentContext.empty();
    assertThat(context.state()).isEqualTo(AgentState.INITIALIZING);
    assertThat(context.metrics()).isEqualTo(AgentMetrics.empty());
    assertThat(context.toolDefinitions()).isEmpty();
    assertThat(context.conversation()).isNull();
    assertThat(context).isNotSameAs(EMPTY_CONTEXT).isEqualTo(EMPTY_CONTEXT);
  }

  @Test
  void withState() {
    final var initialContext = AgentContext.empty();
    final var updatedContext = initialContext.withState(AgentState.WAITING_FOR_TOOL_INPUT);

    assertThat(updatedContext).isNotEqualTo(initialContext);
    assertThat(initialContext.state()).isEqualTo(EMPTY_CONTEXT.state());

    assertThat(updatedContext.state()).isEqualTo(AgentState.WAITING_FOR_TOOL_INPUT);
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
  void withToolDefinitions() {
    final var toolDefinition = ToolDefinition.builder().name("toolA").description("A tool").build();
    final var toolDefinitions = List.of(toolDefinition);

    final var initialContext = AgentContext.empty();
    final var updatedContext = initialContext.withToolDefinitions(toolDefinitions);

    assertThat(updatedContext).isNotEqualTo(initialContext);
    assertThat(initialContext.toolDefinitions()).isEqualTo(EMPTY_CONTEXT.toolDefinitions());

    assertThat(updatedContext.toolDefinitions())
        .isNotNull()
        .containsExactlyElementsOf(toolDefinitions);
  }

  @Test
  void withConversation() {
    final var newMessage = userMessage("Hello");
    final var newConversationContext =
        InProcessConversationContext.builder("test-conversation")
            .messages(List.of(newMessage))
            .build();

    final var initialContext = AgentContext.empty();
    final var updatedContext = initialContext.withConversation(newConversationContext);

    assertThat(updatedContext).isNotEqualTo(initialContext);
    assertThat(initialContext.conversation()).isEqualTo(EMPTY_CONTEXT.conversation());

    assertThat(updatedContext.conversation())
        .isNotNull()
        .isEqualTo(newConversationContext)
        .isNotEqualTo(initialContext.conversation());

    assertThat(((InProcessConversationContext) updatedContext.conversation()).messages())
        .containsExactly(newMessage);
  }

  @Test
  void withPropertyAddsToExistingProperties() {
    final var initialContext = AgentContext.builder().properties(Map.of("foo", "bar")).build();
    final var updatedContext = initialContext.withProperty("baz", "qux");

    assertThat(updatedContext).isNotEqualTo(initialContext);
    assertThat(updatedContext.properties())
        .containsExactlyInAnyOrderEntriesOf(Map.of("foo", "bar", "baz", "qux"));
  }

  @ParameterizedTest
  @MethodSource("invalidConstructorParameters")
  void throwsExceptionOnInvalidConstructorParameters(
      AgentState state,
      AgentMetrics metrics,
      List<ToolDefinition> toolDefinitions,
      String exceptionMessage) {
    assertThatThrownBy(
            () ->
                new AgentContext(
                    state,
                    metrics,
                    toolDefinitions,
                    InProcessConversationContext.builder("test-conversation").build(),
                    Map.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(exceptionMessage);
  }

  @Test
  void canBeSerializedAndDeserialized() throws JsonProcessingException {
    final var agentContext =
        AgentContext.builder()
            .metrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20)))
            .toolDefinitions(
                List.of(
                    ToolDefinition.builder()
                        .name("toolA")
                        .description("A tool")
                        .inputSchema(Map.of("type", "object"))
                        .build()))
            .conversation(
                InProcessConversationContext.builder("test-conversation")
                    .messages(List.of(userMessage("Hello")))
                    .build())
            .build();

    final var serialized =
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(agentContext);

    final var deserialized = objectMapper.readValue(serialized, AgentContext.class);

    assertThat(deserialized).usingRecursiveComparison().isEqualTo(agentContext);
  }

  static Stream<Arguments> invalidConstructorParameters() {
    final var state = AgentState.READY;
    final var metrics = AgentMetrics.empty();
    final var toolDefinitions = List.of();

    return Stream.of(
        arguments(null, metrics, toolDefinitions, "Agent state must not be null"),
        arguments(state, null, toolDefinitions, "Agent metrics must not be null"),
        arguments(state, metrics, null, "Tool definitions must not be null"));
  }
}
