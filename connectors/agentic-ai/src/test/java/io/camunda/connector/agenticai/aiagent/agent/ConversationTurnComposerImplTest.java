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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
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
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.document.Document;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConversationTurnComposerImplTest {

  private ConversationTurnComposerImpl composer;

  private static final AgentContext CTX = AgentContext.builder().state(AgentState.READY).build();
  // a context with a previous conversation cursor — the realistic state when tool results arrive
  private static final AgentContext CTX_WITH_CONVERSATION =
      AgentContext.builder()
          .state(AgentState.READY)
          .conversation(InProcessConversationContext.builder("conv").build())
          .build();
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
  void toolResultsOnEmptyContext_throwsConnectorException() {
    // tool call results arriving with no previous conversation is a modeling error, not a no-op
    var input = AgentInvocationInput.from(null, TOOL_CALL_RESULTS);
    var history = TurnReconstructor.reconstruct(List.of());

    assertThatThrownBy(() -> composer.compose(history, input, CTX, CONFIG))
        .isInstanceOfSatisfying(
            io.camunda.connector.api.error.ConnectorException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(AgentErrorCodes.ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT));
  }

  @Test
  void toolResultTurn_allResultsPresent_returnsNextTurn() {
    var input = AgentInvocationInput.from(null, TOOL_CALL_RESULTS);
    List<Message> storedMessages =
        List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS));
    var history = TurnReconstructor.reconstruct(storedMessages);
    var result = composer.compose(history, input, CTX_WITH_CONVERSATION, CONFIG);
    assertThat(result).isInstanceOf(AgentInput.NextTurn.class);
  }

  @Test
  void toolResultTurn_missingResults_returnsNone() {
    List<ToolCallResult> partialResults = List.of(TOOL_CALL_RESULTS.getFirst());
    var input = AgentInvocationInput.from(null, partialResults);
    List<Message> storedMessages =
        List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS));
    var history = TurnReconstructor.reconstruct(storedMessages);
    var result = composer.compose(history, input, CTX_WITH_CONVERSATION, CONFIG);
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

    var result = composer.compose(history, input, CTX_WITH_CONVERSATION, config);

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
  void firstTurn_withUserPromptDocuments_addsDocumentContent() {
    var document = mock(Document.class);
    var input =
        AgentInvocationInput.from(
            new UserPromptConfiguration("Tell me a story", List.of(document)), List.of());
    var history = TurnReconstructor.reconstruct(List.of());

    var result = composer.compose(history, input, CTX, CONFIG);

    assertThat(result).isInstanceOf(AgentInput.NextTurn.class);
    var userMessage = (UserMessage) ((AgentInput.NextTurn) result).messages().getFirst();
    assertThat(userMessage.content()).contains(DocumentContent.documentContent(document));
  }

  @Test
  void toolResultTurn_reordersResultsToMatchToolCallOrder() {
    // results supplied in reverse order (getDateTime, getWeather)
    var input =
        AgentInvocationInput.from(
            null, List.of(TOOL_CALL_RESULTS.get(1), TOOL_CALL_RESULTS.get(0)));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(history, input, CTX_WITH_CONVERSATION, CONFIG);

    var message = (ToolCallResultMessage) ((AgentInput.NextTurn) result).messages().getFirst();
    // ordered to match the tool calls (getWeather=abcdef, getDateTime=fedcba), not input order
    assertThat(message.results())
        .extracting(ToolCallResult::id)
        .containsExactly("abcdef", "fedcba");
  }

  @Test
  void waitForToolResults_allResultsPresentWithEvent_appendsEventAsUserMessage() {
    var config =
        new AgentConfiguration(
            null,
            null,
            null,
            null,
            new EventHandlingConfiguration(EventHandlingBehavior.WAIT_FOR_TOOL_CALL_RESULTS),
            null);
    var input =
        AgentInvocationInput.from(
            null,
            List.of(
                TOOL_CALL_RESULTS.get(0),
                TOOL_CALL_RESULTS.get(1),
                ToolCallResult.builder().content("An event occurred").build()));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(history, input, CTX_WITH_CONVERSATION, config);

    assertThat(result).isInstanceOf(AgentInput.NextTurn.class);
    var messages = ((AgentInput.NextTurn) result).messages();
    assertThat(messages.getFirst()).isInstanceOf(ToolCallResultMessage.class);
    assertThat(((ToolCallResultMessage) messages.getFirst()).results()).hasSize(2);
    // the event is appended as a trailing user message once all tool results are present
    assertThat(messages.getLast()).isInstanceOf(UserMessage.class);
  }

  @Test
  void waitForToolResults_missingResultWithEvent_returnsNone() {
    // in WAIT mode an event must not force proceeding while tool results are still missing
    var config =
        new AgentConfiguration(
            null,
            null,
            null,
            null,
            new EventHandlingConfiguration(EventHandlingBehavior.WAIT_FOR_TOOL_CALL_RESULTS),
            null);
    var input =
        AgentInvocationInput.from(
            null,
            List.of(
                TOOL_CALL_RESULTS.getFirst(),
                ToolCallResult.builder().content("An event occurred").build()));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(history, input, CTX_WITH_CONVERSATION, config);

    assertThat(result).isInstanceOf(AgentInput.None.class);
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

    var result = composer.compose(history, input, CTX_WITH_CONVERSATION, config);

    assertThat(result).isInstanceOf(AgentInput.None.class);
  }
}
