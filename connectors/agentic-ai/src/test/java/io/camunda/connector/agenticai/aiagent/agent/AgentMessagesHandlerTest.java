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
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposerImpl;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
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

  private static final String EVENT_INTERRUPT_TOOL_CALLS_EMPTY_MESSAGE =
      "An event was triggered but no content was returned. All in-flight tool executions were canceled.";
  private static final String EVENT_WAIT_FOR_TOOL_CALL_RESULTS_EMPTY_MESSAGE =
      "An event was triggered but no content was returned. Execution waited for all in-flight tool executions to complete before proceeding.";

  private static final InMemoryDocumentStore documentStore = InMemoryDocumentStore.INSTANCE;
  private static final DocumentFactoryImpl documentFactory = new DocumentFactoryImpl(documentStore);

  @Mock private GatewayToolHandlerRegistry gatewayToolHandlers;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AgentExecutionContext executionContext;

  private AgentMessagesHandler messagesHandler;
  private RuntimeMemory runtimeMemory;
  private SystemPromptComposer systemPromptComposer;

  @BeforeEach
  void setUp() {
    documentStore.clear();
    systemPromptComposer = new SystemPromptComposerImpl(List.of());
    messagesHandler =
        new AgentMessagesHandlerImpl(
            gatewayToolHandlers, systemPromptComposer, new ToolCallResultDocumentExtractor());
    runtimeMemory = spy(new DefaultRuntimeMemory());
  }

  private static Document createDocument(String content, String contentType, String filename) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(filename)
            .build());
  }

  private static String documentShortId(Document document) {
    if (document.reference() instanceof CamundaDocumentReference ref) {
      var id = ref.getDocumentId();
      int dash = id.indexOf('-');
      return dash > 0 ? id.substring(0, dash) : id;
    }
    return null;
  }

  @Nested
  class DocumentXmlTagTest {

    @Test
    void generatesFullTagWithAllAttributes() {
      var doc = mock(Document.class);
      var ref = mock(CamundaDocumentReference.class);
      var metadata = mock(io.camunda.connector.api.document.DocumentMetadata.class);
      when(doc.reference()).thenReturn(ref);
      when(ref.getDocumentId()).thenReturn("25ece9fa-aeea-423d-98ed-67c1f08b137b");
      when(doc.metadata()).thenReturn(metadata);
      when(metadata.getFileName()).thenReturn("report.pdf");

      assertThat(AgentMessagesHandlerImpl.documentXmlTag(doc, "search", "call_abc"))
          .isEqualTo(
              "<document tool=\"search\" call-id=\"call_abc\" document-short-id=\"25ece9fa\" filename=\"report.pdf\" />");
    }

    @Test
    void generatesTagWithoutToolAndCallId() {
      var doc = mock(Document.class);
      var ref = mock(CamundaDocumentReference.class);
      var metadata = mock(io.camunda.connector.api.document.DocumentMetadata.class);
      when(doc.reference()).thenReturn(ref);
      when(ref.getDocumentId()).thenReturn("f7b3a1d0-1234-5678-9abc-def012345678");
      when(doc.metadata()).thenReturn(metadata);
      when(metadata.getFileName()).thenReturn(null);

      assertThat(AgentMessagesHandlerImpl.documentXmlTag(doc))
          .isEqualTo("<document document-short-id=\"f7b3a1d0\" />");
    }

    @Test
    void generatesMinimalTagForMockedDocument() {
      var doc = mock(Document.class);
      assertThat(AgentMessagesHandlerImpl.documentXmlTag(doc)).isEqualTo("<document />");
    }

    @Test
    void handlesDocumentIdWithoutDash() {
      var doc = mock(Document.class);
      var ref = mock(CamundaDocumentReference.class);
      when(doc.reference()).thenReturn(ref);
      when(ref.getDocumentId()).thenReturn("simpledocid");

      assertThat(AgentMessagesHandlerImpl.documentXmlTag(doc))
          .isEqualTo("<document document-short-id=\"simpledocid\" />");
    }

    @Test
    void escapesSpecialCharactersInFilename() {
      var doc = mock(Document.class);
      var metadata = mock(io.camunda.connector.api.document.DocumentMetadata.class);
      when(doc.metadata()).thenReturn(metadata);
      when(metadata.getFileName()).thenReturn("file\"with<special>&chars'.pdf");

      assertThat(AgentMessagesHandlerImpl.documentXmlTag(doc))
          .isEqualTo("<document filename=\"file&quot;with&lt;special&gt;&amp;chars&apos;.pdf\" />");
    }

    @Test
    void escapesSpecialCharactersInToolName() {
      var doc = mock(Document.class);
      var ref = mock(CamundaDocumentReference.class);
      when(doc.reference()).thenReturn(ref);
      when(ref.getDocumentId()).thenReturn("abc12345-0000-0000-0000-000000000000");

      assertThat(AgentMessagesHandlerImpl.documentXmlTag(doc, "tool<with\"quotes>", "call_1"))
          .isEqualTo(
              "<document tool=\"tool&lt;with&quot;quotes&gt;\" call-id=\"call_1\" document-short-id=\"abc12345\" />");
    }
  }

  @Nested
  class EscapeXmlAttributeTest {

    @Test
    void escapesAllSpecialCharacters() {
      assertThat(AgentMessagesHandlerImpl.escapeXmlAttribute("a&b<c>d\"e'f"))
          .isEqualTo("a&amp;b&lt;c&gt;d&quot;e&apos;f");
    }

    @Test
    void returnsNullForNull() {
      assertThat(AgentMessagesHandlerImpl.escapeXmlAttribute(null)).isNull();
    }

    @Test
    void returnsUnchangedForSafeString() {
      assertThat(AgentMessagesHandlerImpl.escapeXmlAttribute("safe-value_123"))
          .isEqualTo("safe-value_123");
    }
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
                          c ->
                              assertThat(c)
                                  .isEqualTo(DocumentContent.documentContent(documents.get(0))),
                          c ->
                              assertThat(c)
                                  .isEqualTo(DocumentContent.documentContent(documents.get(1))));
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
                          c ->
                              assertThat(c)
                                  .isEqualTo(DocumentContent.documentContent(documents.get(0))),
                          c ->
                              assertThat(c)
                                  .isEqualTo(DocumentContent.documentContent(documents.get(1))));
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

      @Test
      void addsUserMessageTogetherWithEventMessages() {
        final var addedMessages =
            messagesHandler.addUserMessages(
                executionContext,
                AGENT_CONTEXT,
                runtimeMemory,
                new UserPromptConfiguration("Tell me a story", List.of()),
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

      @ParameterizedTest
      @MethodSource(
          "io.camunda.connector.agenticai.aiagent.agent.AgentMessagesHandlerTest#emptyEventContents")
      void createsUserMessageFromEventWhenEventContentIsEmpty(Object eventContent) {
        final var event = ToolCallResult.builder().content(eventContent).build();

        final var addedMessages =
            messagesHandler.addUserMessages(
                executionContext,
                AGENT_CONTEXT,
                runtimeMemory,
                new UserPromptConfiguration("Tell me a story", List.of()),
                List.of(event));

        assertThat(addedMessages)
            .hasSize(2)
            .asInstanceOf(InstanceOfAssertFactories.list(UserMessage.class))
            .satisfiesExactly(
                userMessage ->
                    assertThat(userMessage.content())
                        .hasSize(1)
                        .satisfiesExactly(
                            c -> assertThat(c).isEqualTo(textContent("Tell me a story"))),
                userMessage ->
                    assertThat(userMessage.content())
                        .hasSize(1)
                        .satisfiesExactly(
                            c ->
                                assertThat(c)
                                    .isEqualTo(
                                        textContent(
                                            EVENT_WAIT_FOR_TOOL_CALL_RESULTS_EMPTY_MESSAGE))));
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

      @ParameterizedTest
      @MethodSource(
          "io.camunda.connector.agenticai.aiagent.agent.AgentMessagesHandlerTest#emptyEventContents")
      void interruptsToolCallsOnEventResultsWhenEventContentIsEmpty(Object eventContent) {
        when(executionContext.events().behavior()).thenReturn(INTERRUPT_TOOL_CALLS);

        final var assistantMessage =
            assistantMessage("Assistant message with tool calls", TOOL_CALLS);
        runtimeMemory.addMessage(assistantMessage);

        when(gatewayToolHandlers.transformToolCallResults(eq(AGENT_CONTEXT), anyList()))
            .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(1));

        final var eventWithNullContent = ToolCallResult.builder().content(eventContent).build();

        final var addedMessages =
            messagesHandler.addUserMessages(
                executionContext,
                AGENT_CONTEXT,
                runtimeMemory,
                userPromptWithDocuments,
                List.of(TOOL_CALL_RESULTS.get(1), eventWithNullContent));

        assertThat(addedMessages)
            .hasSize(2)
            .satisfiesExactly(
                message ->
                    assertThat(message)
                        .isInstanceOfSatisfying(
                            ToolCallResultMessage.class,
                            toolCallResultMessage ->
                                assertThat(toolCallResultMessage.results())
                                    .containsExactly(
                                        ToolCallResult.builder()
                                            .id(TOOL_CALL_RESULTS.get(0).id())
                                            .name(TOOL_CALL_RESULTS.get(0).name())
                                            .content(ToolCallResult.CONTENT_CANCELLED)
                                            .properties(
                                                Map.of(ToolCallResult.PROPERTY_INTERRUPTED, true))
                                            .build(),
                                        TOOL_CALL_RESULTS.get(1))),
                message ->
                    assertThat(message)
                        .isInstanceOfSatisfying(
                            UserMessage.class,
                            userMessage ->
                                assertThat(userMessage.content())
                                    .hasSize(1)
                                    .satisfiesExactly(
                                        c ->
                                            assertThat(c)
                                                .isEqualTo(
                                                    textContent(
                                                        EVENT_INTERRUPT_TOOL_CALLS_EMPTY_MESSAGE)))));
      }

      @Test
      void createsDocumentUserMessageWhenToolResultsContainDocuments() {
        final var doc1 = createDocument("weather data", "text/plain", "weather.txt");
        final var doc2 = createDocument("time report", "application/pdf", "report.pdf");
        final var shortId1 = documentShortId(doc1);
        final var shortId2 = documentShortId(doc2);

        final var toolCallResultsWithDocs =
            List.of(
                ToolCallResult.builder()
                    .id("abcdef")
                    .name("getWeather")
                    .content(Map.of("result", "Sunny", "attachment", doc1))
                    .build(),
                ToolCallResult.builder()
                    .id("fedcba")
                    .name("getDateTime")
                    .content(Map.of("report", doc2))
                    .build());

        final var assistantMessage =
            assistantMessage("Assistant message with tool calls", TOOL_CALLS);
        runtimeMemory.addMessage(assistantMessage);

        when(gatewayToolHandlers.transformToolCallResults(AGENT_CONTEXT, toolCallResultsWithDocs))
            .thenReturn(toolCallResultsWithDocs);

        final var addedMessages =
            messagesHandler.addUserMessages(
                executionContext,
                AGENT_CONTEXT,
                runtimeMemory,
                userPromptWithDocuments,
                toolCallResultsWithDocs);

        assertThat(addedMessages)
            .hasSize(2)
            .satisfiesExactly(
                message -> assertThat(message).isInstanceOf(ToolCallResultMessage.class),
                message ->
                    assertThat(message)
                        .isInstanceOfSatisfying(
                            UserMessage.class,
                            userMessage -> {
                              assertThat(userMessage.metadata())
                                  .containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true)
                                  .containsKey("timestamp");
                              assertThat(userMessage.content())
                                  .hasSize(5)
                                  .satisfiesExactly(
                                      c ->
                                          assertThat(c)
                                              .isEqualTo(
                                                  textContent(
                                                      "Documents extracted from tool call results:")),
                                      c ->
                                          assertThat(c)
                                              .isEqualTo(
                                                  textContent(
                                                      "<document tool=\"getWeather\" call-id=\"abcdef\" document-short-id=\"%s\" filename=\"weather.txt\" />"
                                                          .formatted(shortId1))),
                                      c ->
                                          assertThat(c)
                                              .isEqualTo(DocumentContent.documentContent(doc1)),
                                      c ->
                                          assertThat(c)
                                              .isEqualTo(
                                                  textContent(
                                                      "<document tool=\"getDateTime\" call-id=\"fedcba\" document-short-id=\"%s\" filename=\"report.pdf\" />"
                                                          .formatted(shortId2))),
                                      c ->
                                          assertThat(c)
                                              .isEqualTo(DocumentContent.documentContent(doc2)));
                            }));
      }

      @Test
      void doesNotCreateDocumentUserMessageWhenNoDocumentsInToolResults() {
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

        // no document user message - only the tool call result message
        assertThat(addedMessages).hasSize(1).first().isInstanceOf(ToolCallResultMessage.class);
      }

      @Test
      void ordersDocumentUserMessageBetweenToolResultsAndEvents() {
        when(executionContext.events())
            .thenReturn(new EventHandlingConfiguration(WAIT_FOR_TOOL_CALL_RESULTS));

        final var doc = createDocument("weather data", "text/plain", "weather.txt");
        final var shortId = documentShortId(doc);
        final var toolCallResultsWithDocsAndEvents =
            List.of(
                ToolCallResult.builder()
                    .id("abcdef")
                    .name("getWeather")
                    .content(Map.of("file", doc))
                    .build(),
                ToolCallResult.builder().id("fedcba").name("getDateTime").content("15:00").build(),
                EVENT_TOOL_CALL_RESULTS.get(0));

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
                toolCallResultsWithDocsAndEvents);

        // order: ToolCallResultMessage -> document UserMessage -> event UserMessage
        assertThat(addedMessages)
            .hasSize(3)
            .satisfiesExactly(
                message -> assertThat(message).isInstanceOf(ToolCallResultMessage.class),
                message ->
                    assertThat(message)
                        .isInstanceOfSatisfying(
                            UserMessage.class,
                            um ->
                                assertThat(um.content())
                                    .hasSize(3)
                                    .satisfiesExactly(
                                        c ->
                                            assertThat(c)
                                                .isEqualTo(
                                                    textContent(
                                                        "Documents extracted from tool call results:")),
                                        c ->
                                            assertThat(c)
                                                .isEqualTo(
                                                    textContent(
                                                        "<document tool=\"getWeather\" call-id=\"abcdef\" document-short-id=\"%s\" filename=\"weather.txt\" />"
                                                            .formatted(shortId))),
                                        c ->
                                            assertThat(c)
                                                .isEqualTo(DocumentContent.documentContent(doc)))),
                message ->
                    assertThat(message)
                        .isInstanceOfSatisfying(
                            UserMessage.class,
                            um ->
                                assertThat(um.content())
                                    .first()
                                    .isEqualTo(textContent("Event data"))));
      }

      @Test
      void appendsDocumentsToEventMessage() {
        final var doc = createDocument("event data", "application/pdf", "event.pdf");
        final var shortId = documentShortId(doc);
        final var eventWithDoc =
            ToolCallResult.builder().content(Map.of("text", "event", "file", doc)).build();

        final var assistantMessage =
            assistantMessage("Assistant message with tool calls", TOOL_CALLS);
        runtimeMemory.addMessage(assistantMessage);

        when(gatewayToolHandlers.transformToolCallResults(eq(AGENT_CONTEXT), anyList()))
            .thenReturn(TOOL_CALL_RESULTS.stream().toList());

        final var addedMessages =
            messagesHandler.addUserMessages(
                executionContext,
                AGENT_CONTEXT,
                runtimeMemory,
                userPromptWithDocuments,
                List.of(TOOL_CALL_RESULTS.get(0), TOOL_CALL_RESULTS.get(1), eventWithDoc));

        // find the event message (last one)
        final var eventMessage = addedMessages.getLast();
        assertThat(eventMessage)
            .isInstanceOfSatisfying(
                UserMessage.class,
                um ->
                    assertThat(um.content())
                        .hasSize(3)
                        .satisfiesExactly(
                            c ->
                                assertThat(c)
                                    .isEqualTo(objectContent(Map.of("text", "event", "file", doc))),
                            c ->
                                assertThat(c)
                                    .isEqualTo(
                                        textContent(
                                            "<document document-short-id=\"%s\" filename=\"event.pdf\" />"
                                                .formatted(shortId))),
                            c -> assertThat(c).isEqualTo(DocumentContent.documentContent(doc))));
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

  static List<Object> emptyEventContents() {
    final var contents = new ArrayList<>();
    contents.add(null);
    contents.add("");
    contents.add("  ");
    contents.add(List.of());
    contents.add(Map.of());
    return contents;
  }
}
