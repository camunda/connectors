/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALLS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALL_RESULTS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentInvocationInput;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration.EventHandlingBehavior;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConversationTurnComposerImplTest {

  private ConversationTurnComposerImpl composer;

  private static final AgentContext CTX = AgentContext.builder().state(AgentState.READY).build();
  private static final AgentConfiguration CONFIG =
      new AgentConfiguration(null, null, null, null, null, null);

  @BeforeEach
  void setUp() {
    GatewayToolHandlerRegistry gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    when(gatewayToolHandlers.transformToolCallResults(any(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(gatewayToolHandlers.handlerForToolDefinition(any()))
        .thenReturn(java.util.Optional.empty());
    composer = new ConversationTurnComposerImpl(gatewayToolHandlers);
  }

  @Test
  void firstTurn_withUserPrompt_returnsNextTurn() {
    var input = AgentInvocationInput.from(new UserPromptConfiguration("Hello?", null), List.of());
    var history = TurnReconstructor.reconstruct(List.of());
    var result = composer.compose(history, input, CTX, CONFIG);
    assertThat(result).isInstanceOf(AgentInput.NextTurn.class);
    var nextTurn = (AgentInput.NextTurn) result;
    assertThat(nextTurn.messages()).hasSize(1);
    assertThat(nextTurn.messages().getFirst()).isInstanceOf(UserMessage.class);
  }

  @Test
  void firstTurn_emptyPrompt_returnsCancellation() {
    var input = AgentInvocationInput.from(new UserPromptConfiguration("", null), List.of());
    var history = TurnReconstructor.reconstruct(List.of());
    var result = composer.compose(history, input, CTX, CONFIG);
    assertThat(result).isInstanceOf(AgentInput.Cancellation.class);
    assertThat(((AgentInput.Cancellation) result).errorCode())
        .isEqualTo(AgentErrorCodes.ERROR_CODE_NO_USER_MESSAGE_CONTENT);
  }

  @Test
  void firstTurn_nullPrompt_returnsCancellation() {
    var input = AgentInvocationInput.from(null, List.of());
    var history = TurnReconstructor.reconstruct(List.of());
    var result = composer.compose(history, input, CTX, CONFIG);
    assertThat(result).isInstanceOf(AgentInput.Cancellation.class);
  }

  @Test
  void toolResultTurn_allResultsPresent_returnsNextTurn() {
    var input = AgentInvocationInput.from(null, TOOL_CALL_RESULTS);
    List<Message> storedMessages =
        List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS));
    var history = TurnReconstructor.reconstruct(storedMessages);
    var result = composer.compose(history, input, CTX, CONFIG);
    assertThat(result).isInstanceOf(AgentInput.NextTurn.class);
  }

  @Test
  void toolResultTurn_missingResults_returnsNone() {
    List<ToolCallResult> partialResults = List.of(TOOL_CALL_RESULTS.getFirst());
    var input = AgentInvocationInput.from(null, partialResults);
    List<Message> storedMessages =
        List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS));
    var history = TurnReconstructor.reconstruct(storedMessages);
    var result = composer.compose(history, input, CTX, CONFIG);
    assertThat(result).isInstanceOf(AgentInput.None.class);
  }

  @Test
  void interruptToolCalls_withPartialResultsAndEvent_cancelsMissingAndProceeds() {
    var config =
        new AgentConfiguration(
            null,
            null,
            null,
            null,
            new EventHandlingConfiguration(EventHandlingBehavior.INTERRUPT_TOOL_CALLS),
            null);
    var input =
        AgentInvocationInput.from(
            null,
            List.of(
                TOOL_CALL_RESULTS.getFirst(),
                ToolCallResult.builder().content("An event occurred").build()));
    List<Message> storedMessages =
        List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS));
    var history = TurnReconstructor.reconstruct(storedMessages);

    var result = composer.compose(history, input, CTX, config);

    assertThat(result).isInstanceOf(AgentInput.NextTurn.class);
    var nextTurn = (AgentInput.NextTurn) result;
    assertThat(nextTurn.messages()).hasSizeGreaterThanOrEqualTo(2);
    assertThat(nextTurn.messages().getFirst()).isInstanceOf(ToolCallResultMessage.class);
    var toolResults = ((ToolCallResultMessage) nextTurn.messages().getFirst()).results();
    assertThat(toolResults).hasSize(2);
    assertThat(toolResults.get(1).content()).isEqualTo(ToolCallResult.CONTENT_CANCELLED);
    assertThat(nextTurn.messages().getLast()).isInstanceOf(UserMessage.class);
  }

  @Test
  void interruptToolCalls_withNoEvents_stillWaitsForMissingResults() {
    var config =
        new AgentConfiguration(
            null,
            null,
            null,
            null,
            new EventHandlingConfiguration(EventHandlingBehavior.INTERRUPT_TOOL_CALLS),
            null);
    var input = AgentInvocationInput.from(null, List.of(TOOL_CALL_RESULTS.getFirst()));
    List<Message> storedMessages =
        List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS));
    var history = TurnReconstructor.reconstruct(storedMessages);

    var result = composer.compose(history, input, CTX, config);

    assertThat(result).isInstanceOf(AgentInput.None.class);
  }
}
