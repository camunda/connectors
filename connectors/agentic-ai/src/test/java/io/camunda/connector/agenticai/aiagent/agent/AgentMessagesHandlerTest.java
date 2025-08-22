/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.EVENT_TOOL_CALL_RESULTS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALLS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALL_RESULTS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration.EventHandlingBehavior.INTERRUPT_TOOL_CALLS;
import static io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration.EventHandlingBehavior.WAIT_FOR_TOOL_CALL_RESULTS;
import static io.camunda.connector.agenticai.model.message.content.ObjectContent.objectContent;
import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.memory.conversation.TestConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.runtime.DefaultRuntimeMemory;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentMessagesHandlerTest {

  @Mock private GatewayToolHandlerRegistry gatewayToolHandlers;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AgentExecutionContext executionContext;

  private AgentMessagesHandler messagesHandler;
  private RuntimeMemory runtimeMemory;

  @BeforeEach
  void setUp() {
    messagesHandler = new AgentMessagesHandlerImpl(gatewayToolHandlers);
    runtimeMemory = spy(new DefaultRuntimeMemory());
  }

  @Nested
  class SystemMessagesTest {

    @Test
    void addsSystemMessage() {
      final var systemPrompt = new SystemPromptConfiguration("You are a helpful assistant.");
      messagesHandler.addSystemMessage(
          executionContext, AgentContext.empty(), runtimeMemory, systemPrompt);

      assertThat(runtimeMemory.allMessages())
          .hasSize(1)
          .containsExactly(systemMessage("You are a helpful assistant."));
    }
  }

  @Nested
  class UserMessagesTest {

    private List<Document> documents;
    private UserPromptConfiguration userPromptWithDocuments;

    @BeforeEach
    void setUp() {
      documents = List.of(mock(Document.class), mock(Document.class));
      userPromptWithDocuments = new UserPromptConfiguration("Tell me a story", documents);
    }

    @Test
    void throwsExceptionWhenReceivingToolCallResultsOnEmptyConversation() {
      assertThatThrownBy(
              () ->
                  messagesHandler.addUserMessages(
                      executionContext,
                      AgentContext.empty(),
                      runtimeMemory,
                      userPromptWithDocuments,
                      TOOL_CALL_RESULTS))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo("TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT"));
    }

    @Nested
    class UserMessageTest {
      private final AgentContext AGENT_CONTEXT =
          AgentContext.empty()
              .withState(AgentState.READY)
              .withConversation(new TestConversationContext("dummy"));

      @Test
      void addsUserMessageWhenNoPreviousMessage() {
        final var addedUserMessage = assertUserMessageAdded();
        assertThat(runtimeMemory.allMessages()).containsExactly(addedUserMessage);
      }

      @Test
      void addsUserMessageWhenPreviousMessageWasSystemMessage() {
        final var systemMessage = systemMessage("You are a helpful assistant.");
        runtimeMemory.addMessage(systemMessage);

        final var addedUserMessage = assertUserMessageAdded();

        assertThat(runtimeMemory.allMessages()).containsExactly(systemMessage, addedUserMessage);
      }

      @Test
      void addsUserMessageWhenPreviousMessageWasAssistantMessageWithoutToolCallRequests() {
        final var assistantMessage =
            assistantMessage("Previous assistant message without tool calls", List.of());
        runtimeMemory.addMessage(assistantMessage);

        final var addedUserMessage = assertUserMessageAdded();

        assertThat(runtimeMemory.allMessages()).containsExactly(assistantMessage, addedUserMessage);
      }
        assertThat(addedUserMessages).isEmpty();
        assertThat(runtimeMemory.allMessages()).isEmpty();
      }

      @Test
      void addsUserMessageTogetherWithEventMessages() {
        final var addedMessages =
            messagesHandler.addUserMessages(
                executionContext,
                AGENT_CONTEXT,
                runtimeMemory,
                new UserPromptConfiguration("Tell me a story", Map.of(), List.of()),
                EVENT_TOOL_CALL_RESULTS);

        assertThat(addedMessages)
            .hasSize(3)
            .asInstanceOf(InstanceOfAssertFactories.list(UserMessage.class))
            .satisfiesExactly(
                userMessage -> {
                  assertThat(userMessage.content())
                      .hasSize(1)
                      .satisfiesExactly(
                          c -> assertThat(c).isEqualTo(textContent("Tell me a story")));
                  assertThat((ZonedDateTime) userMessage.metadata().get("timestamp"))
                      .isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS));
                },
                userMessage -> {
                  assertThat(userMessage.content())
                      .hasSize(1)
                      .satisfiesExactly(c -> assertThat(c).isEqualTo(textContent("Event data")));
                  assertThat((ZonedDateTime) userMessage.metadata().get("timestamp"))
                      .isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS));
                },
                userMessage -> {
                  assertThat(userMessage.content())
                      .hasSize(1)
                      .satisfiesExactly(
                          c ->
                              assertThat(c)
                                  .isEqualTo(objectContent(Map.of("another", "event data"))));
                  assertThat((ZonedDateTime) userMessage.metadata().get("timestamp"))
                      .isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS));
                });

        assertThat(runtimeMemory.allMessages()).containsExactlyElementsOf(addedMessages);
      }
    }

      @Test
      void addsUserMessageWhenPreviousMessageWasUserMessage() {
        final var userMessage = userMessage("Previous user message");
        runtimeMemory.addMessage(userMessage);

        final var addedUserMessage = assertUserMessageAdded();

        assertThat(runtimeMemory.allMessages()).containsExactly(userMessage, addedUserMessage);
      }

      private UserMessage assertUserMessageAdded() {
        final var addedMessages =
            messagesHandler.addUserMessages(
                executionContext,
                AGENT_CONTEXT,
                runtimeMemory,
                new UserPromptConfiguration("Tell me a story", List.of()),
                TOOL_CALL_RESULTS);

        assertThat(addedMessages)
            .hasSize(1)
            .first(InstanceOfAssertFactories.type(UserMessage.class))
            .satisfies(
                userMessage -> {
                  assertThat(userMessage.content())
                      .hasSize(1)
                      .first()
                      .isEqualTo(textContent("Tell me a story"));
                  assertThat(userMessage.metadata()).containsOnlyKeys("timestamp");
                  assertThat((ZonedDateTime) userMessage.metadata().get("timestamp"))
                      .isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS));
                });

        return (UserMessage) addedMessages.getFirst();
      }

      @Test
      void addsDocumentsToUserMessage() {
        final var addedMessages =
            messagesHandler.addUserMessages(
                executionContext,
                AGENT_CONTEXT,
                runtimeMemory,
                new UserPromptConfiguration(null, documents),
                TOOL_CALL_RESULTS);

        assertThat(addedMessages)
            .hasSize(1)
            .first(InstanceOfAssertFactories.type(UserMessage.class))
            .satisfies(
                userMessage -> {
                  assertThat(userMessage.content())
                      .hasSize(2)
                      .satisfiesExactly(
                          c -> assertThat(c).isEqualTo(new DocumentContent(documents.get(0))),
                          c -> assertThat(c).isEqualTo(new DocumentContent(documents.get(1))));
                });

        assertThat(runtimeMemory.allMessages()).containsExactlyElementsOf(addedMessages);
      }

      @Test
      void addsBothUserPromptAndDocuments() {
        final var addedMessages =
            messagesHandler.addUserMessages(
                executionContext,
                AGENT_CONTEXT,
                runtimeMemory,
                new UserPromptConfiguration("Tell me a story", documents),
                TOOL_CALL_RESULTS);

        assertThat(addedMessages)
            .hasSize(1)
            .first(InstanceOfAssertFactories.type(UserMessage.class))
            .satisfies(
                userMessage -> {
                  assertThat(userMessage.content())
                      .hasSize(3)
                      .satisfiesExactly(
                          c -> assertThat(c).isEqualTo(textContent("Tell me a story")),
                          c -> assertThat(c).isEqualTo(new DocumentContent(documents.get(0))),
                          c -> assertThat(c).isEqualTo(new DocumentContent(documents.get(1))));
                });

        assertThat(runtimeMemory.allMessages()).containsExactlyElementsOf(addedMessages);
      }

      @ParameterizedTest
      @NullAndEmptySource
      @ValueSource(strings = {" "})
      void returnsNoMessageWhenNoUserMessageContentToAdd(String prompt) {
        final var userPrompt = new UserPromptConfiguration(prompt, List.of());
        final var addedUserMessages =
            messagesHandler.addUserMessages(
                executionContext, AGENT_CONTEXT, runtimeMemory, userPrompt, List.of());

        assertThat(addedUserMessages).isEmpty();
        assertThat(runtimeMemory.allMessages()).isEmpty();
      }
    }

    @Nested
    class ToolCallResultsTest {

      private final AgentContext AGENT_CONTEXT =
          AgentContext.empty()
              .withState(AgentState.READY)
              .withConversation(new TestConversationContext("dummy"));

      @Test
      void addsToolCallResultsWhenPreviousMessageWasAssistantMessageWithToolCallRequests() {
        final var assistantMessage =
            assistantMessage("Assistant message with tool calls", TOOL_CALLS);
        runtimeMemory.addMessage(assistantMessage);

        when(gatewayToolHandlers.transformToolCallResults(AGENT_CONTEXT, TOOL_CALL_RESULTS))
            .thenReturn(TOOL_CALL_RESULTS.stream().toList());

        final var addedMessages =
            messagesHandler.addUserMessages(
                executionContext,
                AGENT_CONTEXT,
                runtimeMemory,
                userPromptWithDocuments,
                TOOL_CALL_RESULTS);

        assertThat(addedMessages)
            .hasSize(1)
            .first(InstanceOfAssertFactories.type(ToolCallResultMessage.class))
            .satisfies(
                toolCallResultMessage -> {
                  assertThat(toolCallResultMessage.results())
                      .containsExactlyElementsOf(TOOL_CALL_RESULTS);
                  assertThat(toolCallResultMessage.metadata()).containsOnlyKeys("timestamp");
                  assertThat((ZonedDateTime) toolCallResultMessage.metadata().get("timestamp"))
                      .isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS));
                });

        assertThat(runtimeMemory.allMessages())
            .containsExactly(assistantMessage, addedMessages.getFirst());
      }

      @Test
      void ordersToolCallResultsByRequestOrder() {
        final var reversedToolCallResults = TOOL_CALL_RESULTS.reversed();

        final var assistantMessage =
            assistantMessage("Assistant message with tool calls", TOOL_CALLS);
        runtimeMemory.addMessage(assistantMessage);

        when(gatewayToolHandlers.transformToolCallResults(AGENT_CONTEXT, reversedToolCallResults))
            .thenReturn(reversedToolCallResults.stream().toList());

        final var addedMessages =
            messagesHandler.addUserMessages(
                executionContext,
                AGENT_CONTEXT,
                runtimeMemory,
                userPromptWithDocuments,
                reversedToolCallResults);

        assertThat(addedMessages)
            .hasSize(1)
            .first(InstanceOfAssertFactories.type(ToolCallResultMessage.class))
            .satisfies(
                toolCallResultMessage -> {
                  assertThat(toolCallResultMessage.results())
                      .containsExactlyElementsOf(TOOL_CALL_RESULTS); // ordered by request order
                  assertThat(toolCallResultMessage.metadata()).containsOnlyKeys("timestamp");
                  assertThat((ZonedDateTime) toolCallResultMessage.metadata().get("timestamp"))
                      .isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS));
                });

        assertThat(runtimeMemory.allMessages())
            .containsExactly(assistantMessage, addedMessages.getFirst());
      }

      @Test
      void transformsToolCallResultsViaGatewayToolHandlerRegistry() {
        final var assistantMessage =
            assistantMessage("Assistant message with tool calls", TOOL_CALLS);
        runtimeMemory.addMessage(assistantMessage);

        final var transformedToolCallResults =
            TOOL_CALL_RESULTS.stream()
                .map(
                    toolCallResult -> {
                      if (toolCallResult.name().equals("getWeather")) {
                        return toolCallResult.withContent(
                            "Transformed Weather Result: " + toolCallResult.content());
                      } else {
                        return toolCallResult;
                      }
                    })
                .toList();

        when(gatewayToolHandlers.transformToolCallResults(AGENT_CONTEXT, TOOL_CALL_RESULTS))
            .thenReturn(transformedToolCallResults);

        final var addedMessages =
            messagesHandler.addUserMessages(
                executionContext,
                AGENT_CONTEXT,
                runtimeMemory,
                userPromptWithDocuments,
                TOOL_CALL_RESULTS);

        assertThat(addedMessages)
            .hasSize(1)
            .first(InstanceOfAssertFactories.type(ToolCallResultMessage.class))
            .satisfies(
                toolCallResultMessage -> {
                  assertThat(toolCallResultMessage.results())
                      .containsExactlyElementsOf(transformedToolCallResults);
                  assertThat(toolCallResultMessage.metadata()).containsOnlyKeys("timestamp");
                  assertThat((ZonedDateTime) toolCallResultMessage.metadata().get("timestamp"))
                      .isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS));
                });

        assertThat(runtimeMemory.allMessages())
            .containsExactly(assistantMessage, addedMessages.getFirst());
      }

      @Test
      void doesNotReturnMessageWhenToolCallResultsAreEmpty() {
        final List<ToolCallResult> toolCallResults = Collections.emptyList();

        final var assistantMessage =
            assistantMessage("Assistant message with tool calls", TOOL_CALLS);
        runtimeMemory.addMessage(assistantMessage);

        when(gatewayToolHandlers.transformToolCallResults(AGENT_CONTEXT, toolCallResults))
            .thenReturn(toolCallResults.stream().toList());

        final var addedMessages =
            messagesHandler.addUserMessages(
                executionContext,
                AGENT_CONTEXT,
                runtimeMemory,
                userPromptWithDocuments,
                toolCallResults);
        assertThat(addedMessages).isEmpty();
        assertThat(runtimeMemory.allMessages()).containsExactly(assistantMessage);
      }

      @Test
      void doesNotReturnMessageWhenToolCallResultsArePartiallyMissing() {
        final var toolCallResults = TOOL_CALL_RESULTS.subList(0, 1);

        final var assistantMessage =
            assistantMessage("Assistant message with tool calls", TOOL_CALLS);
        runtimeMemory.addMessage(assistantMessage);

        when(gatewayToolHandlers.transformToolCallResults(AGENT_CONTEXT, toolCallResults))
            .thenReturn(toolCallResults.stream().toList());

        final var addedMessages =
            messagesHandler.addUserMessages(
                executionContext,
                AGENT_CONTEXT,
                runtimeMemory,
                userPromptWithDocuments,
                toolCallResults);
        assertThat(addedMessages).isEmpty();
        assertThat(runtimeMemory.allMessages()).containsExactly(assistantMessage);
      }

      @ParameterizedTest
      @MethodSource("toolCallResultsWithEventsAndEventBehavior")
      void returnsMessagesWithToolCallResultsAndEventMessages(
          List<ToolCallResult> toolCallResultsWithEvents,
          EventHandlingConfiguration eventHandlingConfiguration) {
        when(executionContext.events()).thenReturn(eventHandlingConfiguration);

        final var assistantMessage =
            assistantMessage("Assistant message with tool calls", TOOL_CALLS);
        runtimeMemory.addMessage(assistantMessage);

        when(gatewayToolHandlers.transformToolCallResults(eq(AGENT_CONTEXT), anyList()))
            .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(1));

        final var addedMessages =
            messagesHandler.addUserMessages(
                executionContext,
                AGENT_CONTEXT,
                runtimeMemory,
                userPromptWithDocuments,
                toolCallResultsWithEvents);

        assertThat(addedMessages)
            .hasSize(3)
            .satisfiesExactly(
                message ->
                    assertThat(message)
                        .isInstanceOfSatisfying(
                            ToolCallResultMessage.class,
                            toolCallResultMessage -> {
                              assertThat(toolCallResultMessage.results())
                                  .containsExactlyElementsOf(TOOL_CALL_RESULTS);
                              assertThat(toolCallResultMessage.metadata())
                                  .containsOnlyKeys("timestamp");
                              assertThat(
                                      (ZonedDateTime)
                                          toolCallResultMessage.metadata().get("timestamp"))
                                  .isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS));
                            }),
                message ->
                    assertThat(message)
                        .isInstanceOfSatisfying(
                            UserMessage.class,
                            userMessage -> {
                              assertThat(userMessage.content())
                                  .hasSize(1)
                                  .satisfiesExactly(
                                      c -> assertThat(c).isEqualTo(textContent("Event data")));
                              assertThat((ZonedDateTime) userMessage.metadata().get("timestamp"))
                                  .isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS));
                            }),
                message ->
                    assertThat(message)
                        .isInstanceOfSatisfying(
                            UserMessage.class,
                            userMessage -> {
                              assertThat(userMessage.content())
                                  .hasSize(1)
                                  .satisfiesExactly(
                                      c ->
                                          assertThat(c)
                                              .isEqualTo(
                                                  objectContent(Map.of("another", "event data"))));
                              assertThat((ZonedDateTime) userMessage.metadata().get("timestamp"))
                                  .isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS));
                            }));

        final var expectedMessages = new ArrayList<Message>();
        expectedMessages.add(assistantMessage);
        expectedMessages.addAll(addedMessages);
        assertThat(runtimeMemory.allMessages()).containsExactlyElementsOf(expectedMessages);
      }

      @ParameterizedTest
      @MethodSource("partialToolCallResultsWithEvents")
      void doesNotReturnMessageWhenEventBehaviorIsSetToWaitForToolCallResults(
          List<ToolCallResult> partialToolCallResultsWithEvents) {
        when(executionContext.events().behavior()).thenReturn(WAIT_FOR_TOOL_CALL_RESULTS);

        final var assistantMessage =
            assistantMessage("Assistant message with tool calls", TOOL_CALLS);
        runtimeMemory.addMessage(assistantMessage);

        when(gatewayToolHandlers.transformToolCallResults(eq(AGENT_CONTEXT), anyList()))
            .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(1));

        final var addedMessages =
            messagesHandler.addUserMessages(
                executionContext,
                AGENT_CONTEXT,
                runtimeMemory,
                userPromptWithDocuments,
                partialToolCallResultsWithEvents);

        assertThat(addedMessages).isEmpty();
        assertThat(runtimeMemory.allMessages()).containsExactly(assistantMessage);
      }

      @ParameterizedTest
      @MethodSource("partialToolCallResultsWithEvents")
      void interruptsToolCallsOnEventResultsWhenEventBehaviorIsSetToInterrupt(
          List<ToolCallResult> partialToolCallResultsWithEvents) {
        when(executionContext.events().behavior()).thenReturn(INTERRUPT_TOOL_CALLS);

        final var assistantMessage =
            assistantMessage("Assistant message with tool calls", TOOL_CALLS);
        runtimeMemory.addMessage(assistantMessage);

        when(gatewayToolHandlers.transformToolCallResults(eq(AGENT_CONTEXT), anyList()))
            .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(1));

        final var addedMessages =
            messagesHandler.addUserMessages(
                executionContext,
                AGENT_CONTEXT,
                runtimeMemory,
                userPromptWithDocuments,
                partialToolCallResultsWithEvents);

        assertThat(addedMessages)
            .hasSize(3)
            .satisfiesExactly(
                message ->
                    assertThat(message)
                        .isInstanceOfSatisfying(
                            ToolCallResultMessage.class,
                            toolCallResultMessage -> {
                              assertThat(toolCallResultMessage.results())
                                  .containsExactly(
                                      ToolCallResult.builder()
                                          .id(TOOL_CALL_RESULTS.get(0).id())
                                          .name(TOOL_CALL_RESULTS.get(0).name())
                                          .content(ToolCallResult.CONTENT_CANCELLED)
                                          .properties(
                                              Map.of(ToolCallResult.PROPERTY_INTERRUPTED, true))
                                          .build(),
                                      TOOL_CALL_RESULTS.get(1));
                              assertThat(toolCallResultMessage.metadata())
                                  .containsOnlyKeys("timestamp");
                              assertThat(
                                      (ZonedDateTime)
                                          toolCallResultMessage.metadata().get("timestamp"))
                                  .isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS));
                            }),
                message ->
                    assertThat(message)
                        .isInstanceOfSatisfying(
                            UserMessage.class,
                            userMessage -> {
                              assertThat(userMessage.content())
                                  .hasSize(1)
                                  .satisfiesExactly(
                                      c -> assertThat(c).isEqualTo(textContent("Event data")));
                              assertThat((ZonedDateTime) userMessage.metadata().get("timestamp"))
                                  .isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS));
                            }),
                message ->
                    assertThat(message)
                        .isInstanceOfSatisfying(
                            UserMessage.class,
                            userMessage -> {
                              assertThat(userMessage.content())
                                  .hasSize(1)
                                  .satisfiesExactly(
                                      c ->
                                          assertThat(c)
                                              .isEqualTo(
                                                  objectContent(Map.of("another", "event data"))));
                              assertThat((ZonedDateTime) userMessage.metadata().get("timestamp"))
                                  .isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS));
                            }));

        final var expectedMessages = new ArrayList<Message>();
        expectedMessages.add(assistantMessage);
        expectedMessages.addAll(addedMessages);
        assertThat(runtimeMemory.allMessages()).containsExactlyElementsOf(expectedMessages);
      }

      static List<Arguments> toolCallResultsWithEventsAndEventBehavior() {
        final List<Arguments> arguments = new ArrayList<>();
        toolCallResultsWithEvents()
            .forEach(
                toolCallResults -> {
                  arguments.add(
                      arguments(
                          toolCallResults,
                          new EventHandlingConfiguration(WAIT_FOR_TOOL_CALL_RESULTS)));
                  arguments.add(
                      arguments(
                          toolCallResults, new EventHandlingConfiguration(INTERRUPT_TOOL_CALLS)));
                  arguments.add(arguments(toolCallResults, new EventHandlingConfiguration(null)));
                  arguments.add(arguments(toolCallResults, null));
                });

        return arguments;
      }

      static List<List<ToolCallResult>> toolCallResultsWithEvents() {
        return List.of(
            List.of(
                TOOL_CALL_RESULTS.get(0),
                TOOL_CALL_RESULTS.get(1),
                EVENT_TOOL_CALL_RESULTS.get(0),
                EVENT_TOOL_CALL_RESULTS.get(1)),
            List.of(
                TOOL_CALL_RESULTS.get(0),
                EVENT_TOOL_CALL_RESULTS.get(0),
                TOOL_CALL_RESULTS.get(1),
                EVENT_TOOL_CALL_RESULTS.get(1)),
            List.of(
                EVENT_TOOL_CALL_RESULTS.get(0),
                EVENT_TOOL_CALL_RESULTS.get(1),
                TOOL_CALL_RESULTS.get(0),
                TOOL_CALL_RESULTS.get(1)));
      }

      static List<List<ToolCallResult>> partialToolCallResultsWithEvents() {
        return toolCallResultsWithEvents().stream()
            .map(
                toolCallResults ->
                    toolCallResults.stream()
                        .filter(
                            toolCallResult ->
                                !Objects.equals(TOOL_CALL_RESULTS.get(0).id(), toolCallResult.id()))
                        .toList())
            .toList();
      }
    }
  }
}
