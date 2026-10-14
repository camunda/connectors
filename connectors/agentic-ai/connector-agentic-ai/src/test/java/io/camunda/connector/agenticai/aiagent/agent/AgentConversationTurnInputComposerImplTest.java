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
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.agent.RuntimeToolCallHandler.RuntimeToolCallSplit;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentInput;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration.EventHandlingBehavior;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.aiagent.tool.runtime.builtin.GetDocumentTool;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentConversationTurnInputComposerImplTest {

  private static final RuntimeToolCallSplit NO_DEFERRED_CALLS =
      RuntimeToolCallSplit.allRemaining(List.of());

  private AgentConversationTurnInputComposerImpl composer;
  private GatewayToolHandlerRegistry gatewayToolHandlers;

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  // the contexts of an agent instance with the documents feature
  private static final AgentContext CTX =
      AgentContext.builder()
          .state(AgentState.READY)
          .features(AgentContext.newInstanceFeatures())
          .build();
  // a context with a previous conversation cursor — the realistic state when tool results arrive
  private static final AgentContext CTX_WITH_CONVERSATION =
      AgentContext.builder()
          .state(AgentState.READY)
          .features(AgentContext.newInstanceFeatures())
          .conversation(InProcessConversationContext.builder("conv").build())
          .build();
  private static final AgentConfiguration CONFIG =
      new AgentConfiguration(null, null, null, null, null, null, null);

  @BeforeEach
  void setUp() {
    gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    when(gatewayToolHandlers.transformToolCallResults(any(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(gatewayToolHandlers.handlerForToolDefinition(any()))
        .thenReturn(java.util.Optional.empty());
    composer = new AgentConversationTurnInputComposerImpl(gatewayToolHandlers);
  }

  private Document createDocument(String content, String contentType, String fileName) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(fileName)
            .build());
  }

  @Test
  void firstTurn_withUserPrompt_returnsNextTurn() {
    var input = AgentInput.from(new UserPromptConfiguration("Hello?", List.of()), List.of());
    var history = TurnReconstructor.reconstruct(List.of());
    var result = composer.compose(CONFIG, CTX, history, input, NO_DEFERRED_CALLS);
    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
    var nextTurn = (CompositionResult.NextTurn) result;
    assertThat(nextTurn.messages()).hasSize(1);
    assertThat(nextTurn.messages().getFirst()).isInstanceOf(UserMessage.class);
  }

  @Test
  void firstTurn_emptyPrompt_returnsNoInput() {
    var input = AgentInput.from(new UserPromptConfiguration("", List.of()), List.of());
    var history = TurnReconstructor.reconstruct(List.of());
    var result = composer.compose(CONFIG, CTX, history, input, NO_DEFERRED_CALLS);
    assertThat(result).isInstanceOf(CompositionResult.NoInput.class);
  }

  @Test
  void toolResultsOnEmptyContext_throwsConnectorException() {
    // tool call results arriving with no previous conversation is a modeling error, not a no-op
    var input =
        AgentInput.from(new UserPromptConfiguration("user input", List.of()), TOOL_CALL_RESULTS);
    var history = TurnReconstructor.reconstruct(List.of());

    assertThatThrownBy(() -> composer.compose(CONFIG, CTX, history, input, NO_DEFERRED_CALLS))
        .isInstanceOfSatisfying(
            io.camunda.connector.api.error.ConnectorException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(AgentErrorCodes.ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT));
  }

  @Test
  void toolResultTurn_allResultsPresent_returnsNextTurn() {
    var input =
        AgentInput.from(new UserPromptConfiguration("user input", List.of()), TOOL_CALL_RESULTS);
    List<Message> storedMessages =
        List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS));
    var history = TurnReconstructor.reconstruct(storedMessages);
    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input, NO_DEFERRED_CALLS);
    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
  }

  @Test
  void toolResultTurn_missingResults_returnsNone() {
    List<ToolCallResult> partialResults = List.of(TOOL_CALL_RESULTS.getFirst());
    var input =
        AgentInput.from(new UserPromptConfiguration("user input", List.of()), partialResults);
    List<Message> storedMessages =
        List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS));
    var history = TurnReconstructor.reconstruct(storedMessages);
    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input, NO_DEFERRED_CALLS);
    assertThat(result).isInstanceOf(CompositionResult.Deferred.class);
    // the arrived result is carried on Deferred so the caller can report it early (ADR 011)
    assertThat(((CompositionResult.Deferred) result).arrivedResults())
        .containsExactly(TOOL_CALL_RESULTS.getFirst());
  }

  @Test
  void toolResultTurn_missingResults_deferredCarriesOnlyCorrelatingResult() {
    // a stray/redelivered result for an id this turn isn't waiting on must not appear in
    // Deferred's arrivedResults, and must not cause a failure either
    var strayResult = ToolCallResult.builder().id("stray-id").name("unrelated").build();
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(TOOL_CALL_RESULTS.getFirst(), strayResult));
    List<Message> storedMessages =
        List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS));
    var history = TurnReconstructor.reconstruct(storedMessages);
    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input, NO_DEFERRED_CALLS);
    assertThat(result).isInstanceOf(CompositionResult.Deferred.class);
    assertThat(((CompositionResult.Deferred) result).arrivedResults())
        .containsExactly(TOOL_CALL_RESULTS.getFirst());
  }

  @Test
  void interruptToolCalls_withPartialResultsAndEvent_cancelsMissingAndProceeds() {
    var config =
        new AgentConfiguration(
            null,
            null,
            null,
            null,
            null,
            new EventHandlingConfiguration(EventHandlingBehavior.INTERRUPT_TOOL_CALLS),
            null);
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(
                TOOL_CALL_RESULTS.getFirst(),
                ToolCallResult.builder().content("An event occurred").build()));
    List<Message> storedMessages =
        List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS));
    var history = TurnReconstructor.reconstruct(storedMessages);

    var result = composer.compose(config, CTX_WITH_CONVERSATION, history, input, NO_DEFERRED_CALLS);

    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
    var nextTurn = (CompositionResult.NextTurn) result;
    assertThat(nextTurn.messages()).hasSizeGreaterThanOrEqualTo(2);
    assertThat(nextTurn.messages().getFirst()).isInstanceOf(ToolCallResultMessage.class);
    var toolResults = ((ToolCallResultMessage) nextTurn.messages().getFirst()).results();
    assertThat(toolResults).hasSize(2);
    assertThat(toolResults.get(1).content())
        .containsExactly(TextContent.textContent(ToolCallResult.CONTENT_CANCELLED));
    assertThat(nextTurn.messages().getLast()).isInstanceOf(UserMessage.class);
  }

  @Test
  void firstTurn_withUserPromptDocuments_addsTheFlaggedTagWithoutTheContent() {
    var document = createDocument("story notes", "text/plain", "notes.txt");
    var input =
        AgentInput.from(
            new UserPromptConfiguration("Tell me a story", List.of(document)), List.of());
    var history = TurnReconstructor.reconstruct(List.of());

    var result = composer.compose(CONFIG, CTX, history, input, NO_DEFERRED_CALLS);

    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
    var userMessage = (UserMessage) ((CompositionResult.NextTurn) result).messages().getFirst();
    assertThat(userMessage.content())
        .containsExactly(
            textContent("Tell me a story"),
            documentReference(
                document, "notes.txt", "text/plain", "user_message", DocumentDelivery.REFERENCE));
    assertThat(((DocumentReferenceContent) userMessage.content().get(1)).toTag())
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"notes.txt\" contentType=\"text/plain\" source=\"user_message\" />"
                .formatted(DocumentHandle.idFor(document)));
  }

  private static DocumentReferenceContent documentReference(
      Document document,
      String fileName,
      String contentType,
      String source,
      DocumentDelivery delivery) {
    return new DocumentReferenceContent(
        DocumentHandle.idFor(document), fileName, contentType, null, source, null, null, delivery);
  }

  @Test
  void toolResultTurn_reordersResultsToMatchToolCallOrder() {
    // results supplied in reverse order (getDateTime, getWeather)
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(TOOL_CALL_RESULTS.get(1), TOOL_CALL_RESULTS.get(0)));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input, NO_DEFERRED_CALLS);

    var message =
        (ToolCallResultMessage) ((CompositionResult.NextTurn) result).messages().getFirst();
    // ordered to match the tool calls (getWeather=abcdef, getDateTime=fedcba), not input order
    assertThat(message.results())
        .extracting(ToolCallResultContent::id)
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
            null,
            new EventHandlingConfiguration(EventHandlingBehavior.WAIT_FOR_TOOL_CALL_RESULTS),
            null);
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(
                TOOL_CALL_RESULTS.get(0),
                TOOL_CALL_RESULTS.get(1),
                ToolCallResult.builder().content("An event occurred").build()));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(config, CTX_WITH_CONVERSATION, history, input, NO_DEFERRED_CALLS);

    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
    var messages = ((CompositionResult.NextTurn) result).messages();
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
            null,
            new EventHandlingConfiguration(EventHandlingBehavior.WAIT_FOR_TOOL_CALL_RESULTS),
            null);
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(
                TOOL_CALL_RESULTS.getFirst(),
                ToolCallResult.builder().content("An event occurred").build()));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(config, CTX_WITH_CONVERSATION, history, input, NO_DEFERRED_CALLS);

    assertThat(result).isInstanceOf(CompositionResult.Deferred.class);
    assertThat(((CompositionResult.Deferred) result).arrivedResults())
        .containsExactly(TOOL_CALL_RESULTS.getFirst());
  }

  @Test
  void interruptToolCalls_withNoEvents_stillWaitsForMissingResults() {
    var config =
        new AgentConfiguration(
            null,
            null,
            null,
            null,
            null,
            new EventHandlingConfiguration(EventHandlingBehavior.INTERRUPT_TOOL_CALLS),
            null);
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(TOOL_CALL_RESULTS.getFirst()));
    List<Message> storedMessages =
        List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS));
    var history = TurnReconstructor.reconstruct(storedMessages);

    var result = composer.compose(config, CTX_WITH_CONVERSATION, history, input, NO_DEFERRED_CALLS);

    assertThat(result).isInstanceOf(CompositionResult.Deferred.class);
    assertThat(((CompositionResult.Deferred) result).arrivedResults())
        .containsExactly(TOOL_CALL_RESULTS.getFirst());
  }

  @Test
  void continuingConversation_lastTurnHadNoToolCalls_addsUserPromptTurn() {
    // a non-empty history whose last assistant message has no tool calls: a new user prompt starts
    // the next turn rather than waiting for tool results
    var input = AgentInput.from(new UserPromptConfiguration("And now?", List.of()), List.of());
    var history =
        TurnReconstructor.reconstruct(
            List.of(
                userMessage("hi"),
                assistantMessage("a plain reply without tool calls", List.of())));

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input, NO_DEFERRED_CALLS);

    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
    var messages = ((CompositionResult.NextTurn) result).messages();
    assertThat(messages).hasSize(1);
    assertThat(messages.getFirst()).isInstanceOf(UserMessage.class);
  }

  @Test
  void transformsToolCallResultsViaGatewayToolHandlerRegistry() {
    // the registry is also responsible for annotating elementId; mock returns results with it set
    var transformedResults =
        TOOL_CALL_RESULTS.stream()
            .map(
                r ->
                    r.name().equals("getWeather")
                        ? r.withContent("TRANSFORMED: " + r.content()).withElementId(r.name())
                        : r.withElementId(r.name()))
            .toList();
    when(gatewayToolHandlers.transformToolCallResults(CTX_WITH_CONVERSATION, TOOL_CALL_RESULTS))
        .thenReturn(transformedResults);

    var input =
        AgentInput.from(new UserPromptConfiguration("user input", List.of()), TOOL_CALL_RESULTS);
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input, NO_DEFERRED_CALLS);

    var message =
        (ToolCallResultMessage) ((CompositionResult.NextTurn) result).messages().getFirst();
    assertThat(message.results())
        .containsExactlyElementsOf(
            transformedResults.stream().map(ToolCallResultContent::from).toList());
  }

  @Test
  void toolResultTurn_resultsContainDocuments_emitsNoDocumentMessage() {
    var weatherDoc = createDocument("weather data", "text/plain", "weather.txt");
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(
                ToolCallResult.builder()
                    .id("abcdef")
                    .name("getWeather")
                    .content(Map.of("result", "Sunny", "attachment", weatherDoc))
                    .build(),
                ToolCallResult.builder()
                    .id("fedcba")
                    .name("getDateTime")
                    .content("15:00")
                    .build()));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input, NO_DEFERRED_CALLS);

    // the document is shown as its tag inside the tool result; the message holds it untouched
    var nextTurn = (CompositionResult.NextTurn) result;
    assertThat(nextTurn.messages()).hasSize(1);
    var toolCallResultMessage = (ToolCallResultMessage) nextTurn.messages().getFirst();
    assertThat(toolCallResultMessage.results())
        .filteredOn(r -> "abcdef".equals(r.id()))
        .first()
        .satisfies(
            r ->
                assertThat(r.content())
                    .containsExactly(
                        ObjectContent.objectContent(
                            Map.of("result", "Sunny", "attachment", weatherDoc))));
    assertThat(nextTurn.documents())
        .containsExactly(
            new IngestedDocument(
                weatherDoc,
                DocumentSource.toolCall(
                    "getWeather", "abcdef", toolCallResultMessage.id().toString())));
  }

  @Test
  void toolResultTurn_bareDocumentResult_isPersistedAsItsReferenceWithoutADocumentMessage() {
    // a tool call result whose whole content is a Document (not nested in an object) is persisted
    // as the reference that renders the tag the model sees, and is still ingested into the registry
    var weatherDoc = createDocument("weather data", "text/plain", "weather.txt");
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(
                ToolCallResult.builder()
                    .id("abcdef")
                    .name("getWeather")
                    .content(weatherDoc)
                    .build(),
                ToolCallResult.builder()
                    .id("fedcba")
                    .name("getDateTime")
                    .content("15:00")
                    .build()));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input, NO_DEFERRED_CALLS);

    var nextTurn = (CompositionResult.NextTurn) result;
    assertThat(nextTurn.messages()).hasSize(1);
    var toolCallResultMessage = (ToolCallResultMessage) nextTurn.messages().getFirst();
    assertThat(toolCallResultMessage.results())
        .filteredOn(r -> "abcdef".equals(r.id()))
        .first()
        .satisfies(
            r ->
                assertThat(r.content()).containsExactly(DocumentReferenceContent.from(weatherDoc)));
    assertThat(nextTurn.documents())
        .containsExactly(
            new IngestedDocument(
                weatherDoc,
                DocumentSource.toolCall(
                    "getWeather", "abcdef", toolCallResultMessage.id().toString())));
  }

  @Test
  void toolResultTurn_bareDocumentResult_withoutTheDocumentsFeature_staysTheBareDocument() {
    var weatherDoc = createDocument("weather data", "text/plain", "weather.txt");
    var legacyContext =
        AgentContext.builder()
            .state(AgentState.READY)
            .conversation(InProcessConversationContext.builder("conv").build())
            .build();
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(
                ToolCallResult.builder()
                    .id("abcdef")
                    .name("getWeather")
                    .content(weatherDoc)
                    .build(),
                ToolCallResult.builder()
                    .id("fedcba")
                    .name("getDateTime")
                    .content("15:00")
                    .build()));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(CONFIG, legacyContext, history, input, NO_DEFERRED_CALLS);

    var nextTurn = (CompositionResult.NextTurn) result;
    var toolCallResultMessage = (ToolCallResultMessage) nextTurn.messages().getFirst();
    assertThat(toolCallResultMessage.results())
        .filteredOn(r -> "abcdef".equals(r.id()))
        .first()
        .satisfies(
            r ->
                assertThat(r.content())
                    .containsExactly(DocumentContent.documentContent(weatherDoc)));
    assertThat(nextTurn.documents()).isEmpty();
  }

  @Test
  void waitForToolResults_toolAndEventDocuments_emitsTheToolResultsThenTheEventWithTags() {
    var config =
        new AgentConfiguration(
            null,
            null,
            null,
            null,
            null,
            new EventHandlingConfiguration(EventHandlingBehavior.WAIT_FOR_TOOL_CALL_RESULTS),
            null);
    var toolDoc = createDocument("weather data", "text/plain", "weather.txt");
    var eventDoc = createDocument("event data", "application/pdf", "event.pdf");
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(
                ToolCallResult.builder()
                    .id("abcdef")
                    .name("getWeather")
                    .content(Map.of("file", toolDoc))
                    .build(),
                ToolCallResult.builder().id("fedcba").name("getDateTime").content("15:00").build(),
                ToolCallResult.builder()
                    .content(Map.of("text", "event", "file", eventDoc))
                    .build()));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(config, CTX_WITH_CONVERSATION, history, input, NO_DEFERRED_CALLS);

    // order: tool call results -> event (with its document tags)
    var messages = ((CompositionResult.NextTurn) result).messages();
    assertThat(messages).hasSize(2);
    assertThat(messages.get(0)).isInstanceOf(ToolCallResultMessage.class);
    assertThat(messages.get(1))
        .isInstanceOfSatisfying(
            UserMessage.class,
            eventMessage -> {
              assertThat(eventMessage.content())
                  .contains(
                      textContent(
                          AgentConversationTurnInputComposerImpl.EVENT_DOCUMENT_TAGS_PREAMBLE))
                  .contains(
                      documentReference(
                          eventDoc,
                          "event.pdf",
                          "application/pdf",
                          "event",
                          DocumentDelivery.REFERENCE))
                  .noneMatch(DocumentContent.class::isInstance);
              assertThat(eventMessage.metadata())
                  .doesNotContainKey(UserMessage.METADATA_TOOL_CALL_DOCUMENTS);
            });
  }

  @Test
  void firstTurn_userPromptDocuments_areIngestedWithTheUserMessageSource() {
    var promptDoc = createDocument("prompt data", "text/plain", "prompt.txt");
    var input =
        AgentInput.from(new UserPromptConfiguration("Hello?", List.of(promptDoc)), List.of());

    var result =
        composer.compose(
            CONFIG, CTX, TurnReconstructor.reconstruct(List.of()), input, NO_DEFERRED_CALLS);

    final var nextTurn = (CompositionResult.NextTurn) result;
    assertThat(nextTurn.documents())
        .containsExactly(
            new IngestedDocument(
                promptDoc,
                DocumentSource.userMessage(nextTurn.messages().getFirst().id().toString())));
  }

  @Test
  void toolResultTurn_documentsAreIngestedWithTheirSourceFollowedByEventDocuments() {
    var config =
        new AgentConfiguration(
            null,
            null,
            null,
            null,
            null,
            new EventHandlingConfiguration(EventHandlingBehavior.WAIT_FOR_TOOL_CALL_RESULTS),
            null);
    var toolDoc = createDocument("weather data", "text/plain", "weather.txt");
    var nestedToolDoc = createDocument("nested data", "text/plain", "nested.txt");
    var eventDoc = createDocument("event data", "application/pdf", "event.pdf");
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(
                ToolCallResult.builder()
                    .id("abcdef")
                    .name("getWeather")
                    .content(Map.of("file", toolDoc, "more", List.of(Map.of("d", nestedToolDoc))))
                    .build(),
                ToolCallResult.builder().id("fedcba").name("getDateTime").content("15:00").build(),
                ToolCallResult.builder().content(Map.of("file", eventDoc)).build()));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(config, CTX_WITH_CONVERSATION, history, input, NO_DEFERRED_CALLS);

    final var nextTurn = (CompositionResult.NextTurn) result;
    final var toolCallResultMessageId = nextTurn.messages().get(0).id().toString();
    final var eventMessageId = nextTurn.messages().get(1).id().toString();
    assertThat(nextTurn.messages().get(0)).isInstanceOf(ToolCallResultMessage.class);
    assertThat(nextTurn.documents())
        .containsExactlyInAnyOrder(
            new IngestedDocument(
                toolDoc, DocumentSource.toolCall("getWeather", "abcdef", toolCallResultMessageId)),
            new IngestedDocument(
                nestedToolDoc,
                DocumentSource.toolCall("getWeather", "abcdef", toolCallResultMessageId)),
            new IngestedDocument(eventDoc, DocumentSource.event(eventMessageId)))
        .last()
        .isEqualTo(new IngestedDocument(eventDoc, DocumentSource.event(eventMessageId)));
  }

  @Test
  void toolResultTurn_withoutDocuments_ingestsNone() {
    var input =
        AgentInput.from(new UserPromptConfiguration("user input", List.of()), TOOL_CALL_RESULTS);
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input, NO_DEFERRED_CALLS);

    assertThat(((CompositionResult.NextTurn) result).documents()).isEmpty();
  }

  private static final ToolCallResultContent RUNTIME_RESULT =
      ToolCallResultContent.builder()
          .id("abcdef")
          .name("getWeather")
          .content(List.of(textContent("answered by the agent")))
          .completedAt(java.time.OffsetDateTime.parse("2026-10-05T10:00:00Z"))
          .build();

  /** The split of the tool calls of the turn that answers the calls of the given answers. */
  private static TestRuntimeToolCallSplit deferred(ToolCallResultContent... answers) {
    final var answeredIds =
        java.util.Arrays.stream(answers).map(ToolCallResultContent::id).collect(Collectors.toSet());
    return TestRuntimeToolCallSplit.answering(
        TOOL_CALLS.stream().filter(call -> answeredIds.contains(call.id())).toList(),
        List.of(answers));
  }

  private static List<ToolCallResultContent> resultsOf(CompositionResult result) {
    return ((ToolCallResultMessage) ((CompositionResult.NextTurn) result).messages().getFirst())
        .results();
  }

  @Test
  void deferredRuntimeCalls_mergeWithTheArrivedProcessResultsInToolCallOrder() {
    // the second call is the real one: its result arrives, the first was answered by the agent
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(TOOL_CALL_RESULTS.get(1)));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result =
        composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input, deferred(RUNTIME_RESULT));

    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
    assertThat(resultsOf(result))
        .extracting(ToolCallResultContent::id, ToolCallResultContent::name)
        .containsExactly(tuple("abcdef", "getWeather"), tuple("fedcba", "getDateTime"));
    assertThat(resultsOf(result).getFirst().content())
        .containsExactly(textContent("answered by the agent"));
    // a message with results of both kinds keeps the runtime completion time
    assertThat(resultsOf(result).getFirst().completedAt()).isEqualTo(RUNTIME_RESULT.completedAt());
  }

  @Test
  void deferredRuntimeCallAnswer_thatIsADocumentIsPersistedAsItsReferenceLikeAProcessResult() {
    var document = createDocument("first", "text/plain", "first.txt");
    var pending = RUNTIME_RESULT.withContent(List.of(DocumentContent.documentContent(document)));
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(TOOL_CALL_RESULTS.get(1)));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input, deferred(pending));

    assertThat(resultsOf(result).getFirst().content())
        .containsExactly(DocumentReferenceContent.from(document));
    assertThat(((CompositionResult.NextTurn) result).documents())
        .extracting(ingested -> DocumentHandle.idFor(ingested.document()))
        .containsExactly(DocumentHandle.idFor(document));
  }

  private static ToolCallResultContent contentResult(String id, String documentId) {
    return ToolCallResultContent.builder()
        .id(id)
        .name(GetDocumentTool.NAME)
        .content(List.of(textContent("Loaded document " + documentId + ".")))
        .completedAt(java.time.OffsetDateTime.parse("2026-10-05T10:00:00Z"))
        .properties(Map.of(ToolCallResult.PROPERTY_DOCUMENT_ID_WITH_CONTENT, documentId))
        .build();
  }

  private DocumentRegistry registryOf(Document... documents) {
    return DocumentRegistry.empty()
        .withDocuments(
            java.util.Arrays.stream(documents)
                .map(document -> new IngestedDocument(document, DocumentSource.userMessage(null)))
                .toList());
  }

  @Test
  void documentContentMessage_showsTheMarkedTagOfEveryDocumentWithContentAfterThePreamble() {
    var first = createDocument("first", "text/plain", "first.txt");
    var second = createDocument("second", "text/plain", "second.txt");
    var registry = registryOf(first, second);

    var message =
        composer.composeDocumentContentMessage(
            registry,
            List.of(
                contentResult("c1", DocumentHandle.idFor(second)),
                RUNTIME_RESULT,
                contentResult("c2", DocumentHandle.idFor(first))));

    assertThat(message).isNotNull();
    assertThat(message.metadata()).containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
    assertThat(message.content())
        .containsExactly(
            textContent(AgentConversationTurnInputComposerImpl.DOCUMENT_CONTENT_PREAMBLE),
            tagWithContent(second, "second.txt"),
            tagWithContent(first, "first.txt"));
  }

  @Test
  void documentContentMessage_listsADocumentFetchedByTwoCallsOnce() {
    var document = createDocument("first", "text/plain", "first.txt");
    var id = DocumentHandle.idFor(document);

    var message =
        composer.composeDocumentContentMessage(
            registryOf(document), List.of(contentResult("c1", id), contentResult("c2", id)));

    assertThat(message).isNotNull();
    assertThat(message.content()).hasSize(2);
  }

  @Test
  void documentContentMessage_isNullWithoutALoadingResult() {
    var document = createDocument("first", "text/plain", "first.txt");
    var registry = registryOf(document);
    var id = DocumentHandle.idFor(document);

    // an error or an already-loaded answer carries no id, a result of another tool is not trusted
    var otherTool = contentResult("c1", id).withName("getWeather");
    var unknownDocument = contentResult("c2", "not-registered");
    var answered =
        ToolCallResultContent.builder()
            .id("c3")
            .name(GetDocumentTool.NAME)
            .content(List.of(textContent("Document " + id + " is already in the conversation.")))
            .build();

    assertThat(
            composer.composeDocumentContentMessage(
                registry, List.of(otherTool, unknownDocument, answered, RUNTIME_RESULT)))
        .isNull();
    assertThat(composer.composeDocumentContentMessage(registry, List.of())).isNull();
  }

  @Test
  void deferredGetDocumentCall_isFollowedByTheDocumentContentMessageNextToTheProcessResults() {
    var document = createDocument("first", "text/plain", "first.txt");
    var context = CTX_WITH_CONVERSATION.withDocuments(registryOf(document).entries());
    var answer = contentResult("abcdef", DocumentHandle.idFor(document));
    // the second call is the real one: its result arrives, the first was answered by the agent
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(TOOL_CALL_RESULTS.get(1)));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(CONFIG, context, history, input, deferred(answer));

    var messages = ((CompositionResult.NextTurn) result).messages();
    assertThat(messages).hasSize(2);
    assertThat(messages.get(0)).isInstanceOf(ToolCallResultMessage.class);
    assertThat(messages.get(1))
        .isInstanceOfSatisfying(
            UserMessage.class,
            loaded -> {
              assertThat(loaded.metadata())
                  .containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
              assertThat(loaded.content())
                  .containsExactly(
                      textContent(AgentConversationTurnInputComposerImpl.DOCUMENT_CONTENT_PREAMBLE),
                      tagWithContent(document, "first.txt"));
            });
  }

  @Test
  void
      deferredGetDocumentCallWithContentBlocks_keepsTheBlocksInTheToolResultNextToTheProcessResults() {
    var document = createDocument("first", "text/plain", "first.txt");
    var blocks =
        List.<Content>of(
            textContent("Loaded the document."),
            documentReference(
                document, "first.txt", "text/plain", "user_message", DocumentDelivery.REFERENCE),
            textContent("Its content follows in the next message."));
    var pending =
        ToolCallResultContent.builder()
            .id("abcdef")
            .name(GetDocumentTool.NAME)
            .content(blocks)
            .completedAt(java.time.OffsetDateTime.parse("2026-10-05T10:00:00Z"))
            .properties(
                Map.of(
                    ToolCallResult.PROPERTY_DOCUMENT_ID_WITH_CONTENT,
                    DocumentHandle.idFor(document)))
            .build();
    var context = CTX_WITH_CONVERSATION.withDocuments(registryOf(document).entries());
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(TOOL_CALL_RESULTS.get(1)));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(CONFIG, context, history, input, deferred(pending));

    assertThat(resultsOf(result).getFirst().content()).isEqualTo(blocks);
    assertThat(((CompositionResult.NextTurn) result).messages()).hasSize(2);
    assertThat(((CompositionResult.NextTurn) result).documents()).isEmpty();
  }

  @Test
  void toolResultsWithoutAGetDocumentResult_addNoDocumentContentMessage() {
    var input =
        AgentInput.from(new UserPromptConfiguration("user input", List.of()), TOOL_CALL_RESULTS);
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input, NO_DEFERRED_CALLS);

    assertThat(((CompositionResult.NextTurn) result).messages()).hasSize(1);
  }

  private static DocumentReferenceContent tagWithContent(Document document, String fileName) {
    return documentReference(
        document, fileName, "text/plain", "user_message", DocumentDelivery.CONTENT);
  }

  @Test
  void deferredRuntimeCalls_keepToolCallOrderWhenTheRuntimeCallComesLast() {
    var runtimeSecond = RUNTIME_RESULT.withId("fedcba").withName("getDateTime");
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(TOOL_CALL_RESULTS.getFirst()));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result =
        composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input, deferred(runtimeSecond));

    assertThat(resultsOf(result))
        .extracting(ToolCallResultContent::id)
        .containsExactly("abcdef", "fedcba");
  }

  @Test
  void deferredRuntimeCalls_doNotGoThroughTheGatewayTransformation() {
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(TOOL_CALL_RESULTS.get(1)));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input, deferred(RUNTIME_RESULT));

    org.mockito.Mockito.verify(gatewayToolHandlers)
        .transformToolCallResults(
            any(), org.mockito.ArgumentMatchers.eq(List.of(TOOL_CALL_RESULTS.get(1))));
  }

  @Test
  void deferredRuntimeCalls_partialArrivalDefersAndCarriesOnlyTheProcessResults() {
    var threeCalls =
        List.of(
            TOOL_CALLS.get(0),
            TOOL_CALLS.get(1),
            ToolCall.builder().id("third").name("getTime").build());
    var thirdResult = ToolCallResult.builder().id("third").name("getTime").content("noon").build();
    var input =
        AgentInput.from(new UserPromptConfiguration("user input", List.of()), List.of(thirdResult));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", threeCalls)));

    var calls = deferred(RUNTIME_RESULT);

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input, calls);

    // "fedcba" is still running: wait for it, the answer of the agent is not reported with the
    // arrivals and not computed yet
    assertThat(result).isInstanceOf(CompositionResult.Deferred.class);
    assertThat(((CompositionResult.Deferred) result).arrivedResults()).containsExactly(thirdResult);
    assertThat(calls.answerCount()).isZero();
  }

  @Test
  void deferredRuntimeCalls_withoutAnyProcessResultDeferWithNothingArrived() {
    var input = AgentInput.from(new UserPromptConfiguration("user input", List.of()), List.of());
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var calls = deferred(RUNTIME_RESULT);

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input, calls);

    assertThat(result).isInstanceOf(CompositionResult.Deferred.class);
    assertThat(((CompositionResult.Deferred) result).arrivedResults()).isEmpty();
    assertThat(calls.answerCount()).isZero();
  }

  @Test
  void deferredRuntimeCalls_interruptCancelsOnlyTheMissingProcessCalls() {
    var config =
        new AgentConfiguration(
            null,
            null,
            null,
            null,
            null,
            new EventHandlingConfiguration(EventHandlingBehavior.INTERRUPT_TOOL_CALLS),
            null);
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(ToolCallResult.builder().content("An event occurred").build()));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var calls = deferred(RUNTIME_RESULT);

    var result = composer.compose(config, CTX_WITH_CONVERSATION, history, input, calls);

    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
    // the call of the agent is answered although the turn is interrupted
    assertThat(calls.answerCount()).isEqualTo(1);
    var results = resultsOf(result);
    assertThat(results).extracting(ToolCallResultContent::id).containsExactly("abcdef", "fedcba");
    assertThat(results.get(0).content()).containsExactly(textContent("answered by the agent"));
    assertThat(results.get(0).properties()).doesNotContainKey(ToolCallResult.PROPERTY_INTERRUPTED);
    assertThat(results.get(1).content())
        .containsExactly(textContent(ToolCallResult.CONTENT_CANCELLED));
    assertThat(results.get(1).properties())
        .containsEntry(ToolCallResult.PROPERTY_INTERRUPTED, true);
  }

  @Test
  void deferredRuntimeCalls_areNotAnsweredForAPromptTurn() {
    var input = AgentInput.from(new UserPromptConfiguration("Hello?", List.of()), List.of());
    var history =
        TurnReconstructor.reconstruct(List.of(userMessage("hi"), assistantMessage("hello")));

    var calls = deferred(RUNTIME_RESULT);

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input, calls);

    assertThat(((CompositionResult.NextTurn) result).messages())
        .singleElement()
        .isInstanceOf(UserMessage.class);
    assertThat(calls.answerCount()).isZero();
  }

  @Test
  void deferredRuntimeCalls_areAnsweredOnceAndTheirContextUpdatesReachTheNextTurn() {
    var update = (java.util.function.UnaryOperator<AgentContext>) ctx -> ctx.withProperty("k", "v");
    var calls =
        TestRuntimeToolCallSplit.answering(
            List.of(TOOL_CALLS.getFirst()), List.of(RUNTIME_RESULT), List.of(update));
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(TOOL_CALL_RESULTS.get(1)));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input, calls);

    assertThat(calls.answerCount()).isEqualTo(1);
    assertThat(((CompositionResult.NextTurn) result).contextUpdates()).containsExactly(update);
  }
}
