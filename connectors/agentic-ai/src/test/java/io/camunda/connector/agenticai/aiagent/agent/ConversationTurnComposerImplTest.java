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
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentInvocationInput;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConversationTurnComposerImplTest {

  private ConversationTurnComposerImpl composer;
  private GatewayToolHandlerRegistry gatewayToolHandlers;

  @BeforeEach
  void setUp() {
    gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    when(gatewayToolHandlers.transformToolCallResults(any(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(gatewayToolHandlers.handlerForToolDefinition(any()))
        .thenReturn(java.util.Optional.empty());
    composer = new ConversationTurnComposerImpl(gatewayToolHandlers);
  }

  private static AgentConversation emptyConversation(AgentInvocationInput input) {
    var ctx = AgentContext.builder().state(AgentState.READY).build();
    var config = new AgentConfiguration(null, null, null, null, null, null);
    return AgentConversation.rehydrate(List.of(), ctx, input, config);
  }

  @Test
  void firstTurn_withUserPrompt_returnsNextTurn() {
    var input = AgentInvocationInput.from(new UserPromptConfiguration("Hello?", null), List.of());
    var conv = emptyConversation(input);
    var result = composer.compose(conv);
    assertThat(result).isInstanceOf(AgentInput.NextTurn.class);
    var nextTurn = (AgentInput.NextTurn) result;
    assertThat(nextTurn.messages()).hasSize(1);
    assertThat(nextTurn.messages().getFirst()).isInstanceOf(UserMessage.class);
  }

  @Test
  void firstTurn_emptyPrompt_returnsCancellation() {
    var input = AgentInvocationInput.from(new UserPromptConfiguration("", null), List.of());
    var conv = emptyConversation(input);
    var result = composer.compose(conv);
    assertThat(result).isInstanceOf(AgentInput.Cancellation.class);
    assertThat(((AgentInput.Cancellation) result).errorCode())
        .isEqualTo(AgentErrorCodes.ERROR_CODE_NO_USER_MESSAGE_CONTENT);
  }

  @Test
  void firstTurn_nullPrompt_returnsCancellation() {
    var input = AgentInvocationInput.from(null, List.of());
    var conv = emptyConversation(input);
    var result = composer.compose(conv);
    assertThat(result).isInstanceOf(AgentInput.Cancellation.class);
  }

  @Test
  void toolResultTurn_allResultsPresent_returnsNextTurn() {
    var ctx = AgentContext.builder().state(AgentState.READY).build();
    var config = new AgentConfiguration(null, null, null, null, null, null);
    var input = AgentInvocationInput.from(null, TOOL_CALL_RESULTS);
    List<Message> history = List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS));
    var conv = AgentConversation.rehydrate(history, ctx, input, config);
    var result = composer.compose(conv);
    assertThat(result).isInstanceOf(AgentInput.NextTurn.class);
  }

  @Test
  void toolResultTurn_missingResults_returnsNone() {
    var ctx = AgentContext.builder().state(AgentState.READY).build();
    var config = new AgentConfiguration(null, null, null, null, null, null);
    // only partial results (fewer than expected tool calls)
    List<ToolCallResult> partialResults = List.of(TOOL_CALL_RESULTS.getFirst());
    var input = AgentInvocationInput.from(null, partialResults);
    List<Message> history = List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS));
    var conv = AgentConversation.rehydrate(history, ctx, input, config);
    var result = composer.compose(conv);
    assertThat(result).isInstanceOf(AgentInput.None.class);
  }
}
