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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.conversation.AgentInput;
import io.camunda.connector.agenticai.aiagent.conversation.PreviousConversation;
import io.camunda.connector.agenticai.aiagent.conversation.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments.NewDocument;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration.EventHandlingBehavior;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentConversationTurnInputComposerImplTest {

  private AgentConversationTurnInputComposerImpl composer;
  private GatewayToolHandlerRegistry gatewayToolHandlers;

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  // the contexts of an agent instance with the documents feature
  private static final AgentContext CTX =
      AgentContext.builder()
          .state(AgentState.READY)
          .features(List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS))
          .build();
  // a context with a previous conversation cursor — the realistic state when tool results arrive
  private static final AgentContext CTX_WITH_CONVERSATION =
      AgentContext.builder()
          .state(AgentState.READY)
          .features(List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS))
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
    composer =
        new AgentConversationTurnInputComposerImpl(
            gatewayToolHandlers, new ToolCallResultsInputBuilder());
  }

  private CompositionResult resolve(
      AgentConfiguration configuration,
      AgentContext agentContext,
      PreviousConversation history,
      AgentInput input) {
    return composer.resolve(configuration, agentContext, history, input);
  }

  /** The input of a result that is ready and has no local tool calls to answer. */
  private TurnInput inputOf(CompositionResult result) {
    assertThat(result).isInstanceOf(CompositionResult.Ready.class);
    final var ready = (CompositionResult.Ready) result;
    assertThat(ready.unansweredToolCalls()).isEmpty();
    return composer.assemble(ready);
  }

  private List<Message> messagesOf(CompositionResult result) {
    return inputOf(result).messages();
  }

  private Document createDocument(String content, String contentType, String fileName) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(fileName)
            .build());
  }

  @Test
  void firstTurn_withUserPrompt_returnsReady() {
    var input = AgentInput.from(new UserPromptConfiguration("Hello?", List.of()), List.of());
    var history = TurnReconstructor.reconstruct(List.of());
    var result = resolve(CONFIG, CTX, history, input);
    assertThat(result).isInstanceOf(CompositionResult.Ready.class);
    var ready = (CompositionResult.Ready) result;
    assertThat(ready.toolCallBatch()).isEqualTo(ToolCallBatch.of(List.of()));
    assertThat(ready.events()).isEqualTo(TurnInput.empty());
    assertThat(ready.userPrompt().messages()).singleElement().isInstanceOf(UserMessage.class);
    assertThat(((UserMessage) ready.userPrompt().messages().getFirst()).content())
        .containsExactly(textContent("Hello?"));
    assertThat(ready.userPrompt().documents()).isEmpty();
    assertThat(messagesOf(result)).containsExactlyElementsOf(ready.userPrompt().messages());
  }

  @Test
  void firstTurn_emptyPrompt_returnsNoInput() {
    var input = AgentInput.from(new UserPromptConfiguration("", List.of()), List.of());
    var history = TurnReconstructor.reconstruct(List.of());
    var result = resolve(CONFIG, CTX, history, input);
    assertThat(result).isInstanceOf(CompositionResult.NoInput.class);
  }

  @Test
  void toolResultsOnEmptyContext_throwsConnectorException() {
    // tool call results arriving with no previous conversation is a modeling error, not a no-op
    var input =
        AgentInput.from(new UserPromptConfiguration("user input", List.of()), TOOL_CALL_RESULTS);
    var history = TurnReconstructor.reconstruct(List.of());

    assertThatThrownBy(() -> resolve(CONFIG, CTX, history, input))
        .isInstanceOfSatisfying(
            io.camunda.connector.api.error.ConnectorException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(AgentErrorCodes.ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT));
  }

  @Test
  void toolResultTurn_allResultsPresent_returnsTheResultsAsOneMessageAndIngestsNoDocument() {
    var input =
        AgentInput.from(new UserPromptConfiguration("user input", List.of()), TOOL_CALL_RESULTS);
    List<Message> storedMessages =
        List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS));
    var history = TurnReconstructor.reconstruct(storedMessages);
    var result = resolve(CONFIG, CTX_WITH_CONVERSATION, history, input);
    assertThat(result).isInstanceOf(CompositionResult.Ready.class);
    var nextTurn = inputOf(result);
    assertThat(nextTurn.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
    assertThat(nextTurn.documents()).isEmpty();
  }

  @Test
  void toolResultTurn_missingResults_returnsNone() {
    List<ToolCallResult> partialResults = List.of(TOOL_CALL_RESULTS.getFirst());
    var input =
        AgentInput.from(new UserPromptConfiguration("user input", List.of()), partialResults);
    List<Message> storedMessages =
        List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS));
    var history = TurnReconstructor.reconstruct(storedMessages);
    var result = resolve(CONFIG, CTX_WITH_CONVERSATION, history, input);
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
    var result = resolve(CONFIG, CTX_WITH_CONVERSATION, history, input);
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

    var result = resolve(config, CTX_WITH_CONVERSATION, history, input);

    assertThat(result).isInstanceOf(CompositionResult.Ready.class);
    var messages = messagesOf(result);
    assertThat(messages).hasSizeGreaterThanOrEqualTo(2);
    assertThat(messages.getFirst()).isInstanceOf(ToolCallResultMessage.class);
    var toolResults = ((ToolCallResultMessage) messages.getFirst()).results();
    assertThat(toolResults).hasSize(2);
    assertThat(toolResults.get(1).content())
        .containsExactly(TextContent.textContent(ToolCallResult.CONTENT_CANCELLED));
    assertThat(messages.getLast()).isInstanceOf(UserMessage.class);
  }

  @Test
  void firstTurn_withUserPromptDocuments_addsTheReferencesWithTheDeliveryContent() {
    var document = createDocument("story notes", "text/plain", "notes.txt");
    var input =
        AgentInput.from(
            new UserPromptConfiguration("Tell me a story", List.of(document)), List.of());
    var history = TurnReconstructor.reconstruct(List.of());

    var result = resolve(CONFIG, CTX, history, input);

    assertThat(result).isInstanceOf(CompositionResult.Ready.class);
    var userMessage = (UserMessage) messagesOf(result).getFirst();
    assertThat(userMessage.content())
        .containsExactly(
            textContent("Tell me a story"), documentReference(document, DocumentDelivery.CONTENT));
    final var ingested = inputOf(result).documents();
    assertThat(ingested).containsExactly(new NewDocument(document, DocumentOrigin.userMessage()));
    assertThat(
            TestDocuments.handleText(
                AgentDocuments.empty().register(ingested), AgentDocumentId.of(document)))
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"%s\",\"fileName\":\"notes.txt\",\"contentType\":\"text/plain\"}"
                .formatted(AgentDocumentId.of(document)));
  }

  private static AgentDocumentContent documentReference(
      Document document, DocumentDelivery delivery) {
    return new AgentDocumentContent(AgentDocumentId.of(document), delivery, null);
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

    var result = resolve(CONFIG, CTX_WITH_CONVERSATION, history, input);

    var message = (ToolCallResultMessage) messagesOf(result).getFirst();
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

    var result = resolve(config, CTX_WITH_CONVERSATION, history, input);

    assertThat(result).isInstanceOf(CompositionResult.Ready.class);
    var messages = messagesOf(result);
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

    var result = resolve(config, CTX_WITH_CONVERSATION, history, input);

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

    var result = resolve(config, CTX_WITH_CONVERSATION, history, input);

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

    var result = resolve(CONFIG, CTX_WITH_CONVERSATION, history, input);

    assertThat(result).isInstanceOf(CompositionResult.Ready.class);
    var messages = messagesOf(result);
    assertThat(messages).hasSize(1);
    assertThat(messages.getFirst()).isInstanceOf(UserMessage.class);
  }

  @Test
  void transformsToolCallResultsViaGatewayToolHandlerRegistry() {
    // the registry is also responsible for annotating elementId; mock returns results with it set
    var transformedResults =
        TOOL_CALL_RESULTS.stream()
            .map(r ->
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

    var result = resolve(CONFIG, CTX_WITH_CONVERSATION, history, input);

    var message = (ToolCallResultMessage) messagesOf(result).getFirst();
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

    var result = resolve(CONFIG, CTX_WITH_CONVERSATION, history, input);

    // the document is shown as its handle inside the tool result; the message holds it untouched
    var nextTurn = inputOf(result);
    assertThat(nextTurn.messages()).hasSize(1);
    var toolCallResultMessage = (ToolCallResultMessage) nextTurn.messages().getFirst();
    assertThat(toolCallResultMessage.results())
        .filteredOn(r -> "abcdef".equals(r.id()))
        .first()
        .satisfies(r ->
            assertThat(r.content())
                .containsExactly(
                    ObjectContent.objectContent(
                        Map.of("result", "Sunny", "attachment", weatherDoc))));
    assertThat(nextTurn.documents())
        .containsExactly(
            new NewDocument(weatherDoc, DocumentOrigin.toolCall("getWeather", "abcdef")));
  }

  @Test
  void toolResultTurn_bareDocumentResult_isPersistedAsItsReferenceWithoutADocumentMessage() {
    // a tool call result whose whole content is a Document (not nested in an object) is persisted
    // as the reference that renders the handle the model sees, and is still ingested into the
    // agent documents
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

    var result = resolve(CONFIG, CTX_WITH_CONVERSATION, history, input);

    var nextTurn = inputOf(result);
    assertThat(nextTurn.messages()).hasSize(1);
    var toolCallResultMessage = (ToolCallResultMessage) nextTurn.messages().getFirst();
    assertThat(toolCallResultMessage.results())
        .filteredOn(r -> "abcdef".equals(r.id()))
        .first()
        .satisfies(r ->
            assertThat(r.content())
                .containsExactly(AgentDocumentContent.of(AgentDocumentId.of(weatherDoc))));
    assertThat(nextTurn.documents())
        .containsExactly(
            new NewDocument(weatherDoc, DocumentOrigin.toolCall("getWeather", "abcdef")));
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

    var result = resolve(CONFIG, legacyContext, history, input);

    var nextTurn = inputOf(result);
    var toolCallResultMessage = (ToolCallResultMessage) nextTurn.messages().getFirst();
    assertThat(toolCallResultMessage.results())
        .filteredOn(r -> "abcdef".equals(r.id()))
        .first()
        .satisfies(r ->
            assertThat(r.content()).containsExactly(DocumentContent.documentContent(weatherDoc)));
    assertThat(nextTurn.documents()).isEmpty();
  }

  @Test
  void waitForToolResults_toolAndEventDocuments_emitsTheToolResultsThenTheEventWithHandles() {
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

    var result = resolve(config, CTX_WITH_CONVERSATION, history, input);

    // order: tool call results -> event (with its document handles)
    var messages = messagesOf(result);
    assertThat(messages).hasSize(2);
    assertThat(messages.get(0)).isInstanceOf(ToolCallResultMessage.class);
    assertThat(messages.get(1))
        .isInstanceOfSatisfying(
            UserMessage.class,
            eventMessage -> {
              assertThat(eventMessage.content())
                  .contains(
                      textContent(
                          AgentConversationTurnInputComposerImpl.EVENT_DOCUMENT_HANDLES_PREAMBLE))
                  .contains(documentReference(eventDoc, DocumentDelivery.REFERENCE))
                  .noneMatch(DocumentContent.class::isInstance);
              assertThat(eventMessage.metadata())
                  .doesNotContainKey(UserMessage.METADATA_TOOL_CALL_DOCUMENTS);
            });
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

    var result = resolve(config, CTX_WITH_CONVERSATION, history, input);

    final var nextTurn = inputOf(result);
    assertThat(nextTurn.messages().get(0)).isInstanceOf(ToolCallResultMessage.class);
    assertThat(nextTurn.documents())
        .containsExactlyInAnyOrder(
            new NewDocument(toolDoc, DocumentOrigin.toolCall("getWeather", "abcdef")),
            new NewDocument(nestedToolDoc, DocumentOrigin.toolCall("getWeather", "abcdef")),
            new NewDocument(eventDoc, DocumentOrigin.event()))
        .last()
        .isEqualTo(new NewDocument(eventDoc, DocumentOrigin.event()));
  }

  private static final ToolCallResultContent RUNTIME_RESULT =
      ToolCallResultContent.builder()
          .id("abcdef")
          .name("getWeather")
          .content(List.of(textContent("result of the runtime tool")))
          .completedAt(OffsetDateTime.parse("2026-10-05T10:00:00Z"))
          .build();

  private static final List<ToolCall> MIXED_CALLS =
      List.of(TOOL_CALLS.get(0).withKind(ToolKind.RUNTIME), TOOL_CALLS.get(1));

  private static final List<ToolCall> RUNTIME_CALLS =
      TOOL_CALLS.stream().map(call -> call.withKind(ToolKind.RUNTIME)).toList();

  private static final ToolCallResultContent SECOND_RUNTIME_RESULT =
      RUNTIME_RESULT.withId("fedcba").withName("getDateTime");

  private static final AgentConfiguration INTERRUPTING_CONFIG =
      new AgentConfiguration(
          null,
          null,
          null,
          null,
          null,
          new EventHandlingConfiguration(EventHandlingBehavior.INTERRUPT_TOOL_CALLS),
          null);

  private static PreviousConversation historyWith(List<ToolCall> toolCalls) {
    return TurnReconstructor.reconstruct(
        List.of(userMessage("hi"), assistantMessage("thinking", toolCalls)));
  }

  private static AgentInput inputWith(ToolCallResult... processResults) {
    return AgentInput.from(
        new UserPromptConfiguration("user input", List.of()), List.of(processResults));
  }

  private static ToolCallBatch.Entry entryOf(ToolCallResultContent result) {
    return ToolCallBatch.Entry.from(result);
  }

  private CompositionResult.Ready resolveReady(
      AgentConfiguration configuration, PreviousConversation history, AgentInput input) {
    final var result = composer.resolve(configuration, CTX_WITH_CONVERSATION, history, input);
    assertThat(result).isInstanceOf(CompositionResult.Ready.class);
    return (CompositionResult.Ready) result;
  }

  /**
   * Composes like the request handler: the unanswered local tool calls of the resolved turn are
   * answered with the given results before the turn is assembled.
   */
  private TurnInput assembleWithLocalResults(
      AgentConfiguration configuration,
      PreviousConversation history,
      AgentInput input,
      ToolCallResultContent... localResults) {
    final var ready = resolveReady(configuration, history, input);
    return composer.assemble(
        ready.withResults(Arrays.stream(localResults).map(r -> entryOf(r)).toList()));
  }

  private static List<ToolCallResultContent> resultsOf(TurnInput input) {
    return ((ToolCallResultMessage) input.messages().getFirst()).results();
  }

  @Test
  void localToolCalls_mergeWithTheArrivedProcessResultsInToolCallOrder() {
    // the first call is the runtime tool call, the result of the second one arrives
    var input = inputWith(TOOL_CALL_RESULTS.get(1));

    var turnInput =
        assembleWithLocalResults(CONFIG, historyWith(MIXED_CALLS), input, RUNTIME_RESULT);

    assertThat(turnInput.messages()).hasSize(1);
    assertThat(resultsOf(turnInput))
        .containsExactly(RUNTIME_RESULT, ToolCallResultContent.from(TOOL_CALL_RESULTS.get(1)));
  }

  @Test
  void localToolCalls_keepToolCallOrderWhenTheRuntimeCallComesLast() {
    var calls = List.of(TOOL_CALLS.get(0), TOOL_CALLS.get(1).withKind(ToolKind.RUNTIME));
    var input = inputWith(TOOL_CALL_RESULTS.getFirst());

    var turnInput =
        assembleWithLocalResults(CONFIG, historyWith(calls), input, SECOND_RUNTIME_RESULT);

    assertThat(resultsOf(turnInput))
        .containsExactly(
            ToolCallResultContent.from(TOOL_CALL_RESULTS.getFirst()), SECOND_RUNTIME_RESULT);
  }

  @Test
  void runtimeToolCallResult_thatIsADocumentIsPersistedAsItsReferenceAndIngested() {
    var document = createDocument("first", "text/plain", "first.txt");
    var runtimeResult =
        RUNTIME_RESULT.withContent(List.of(DocumentContent.documentContent(document)));
    var input = inputWith(TOOL_CALL_RESULTS.get(1));

    var turnInput =
        assembleWithLocalResults(CONFIG, historyWith(MIXED_CALLS), input, runtimeResult);

    assertThat(resultsOf(turnInput).getFirst().content())
        .containsExactly(AgentDocumentContent.of(AgentDocumentId.of(document)));
    // the document of the result of a runtime tool is registered with the call that returned it
    assertThat(turnInput.documents())
        .containsExactly(
            new NewDocument(document, DocumentOrigin.toolCall("getWeather", "abcdef")));
  }

  @Test
  void runtimeToolDocuments_areIngestedWithTheProcessDocumentsInToolCallOrder() {
    var runtimeDocument = createDocument("created", "text/plain", "created.txt");
    var nestedRuntimeDocument = createDocument("nested", "text/plain", "nested.txt");
    var processDocument = createDocument("weather", "text/plain", "weather.txt");
    var runtimeResult =
        RUNTIME_RESULT.withContent(
            List.of(
                textContent("Created."),
                DocumentContent.documentContent(runtimeDocument),
                ObjectContent.objectContent(Map.of("more", nestedRuntimeDocument))));
    var input =
        inputWith(
            ToolCallResult.builder()
                .id("fedcba")
                .name("getDateTime")
                .content(Map.of("file", processDocument))
                .build());

    var turnInput =
        assembleWithLocalResults(CONFIG, historyWith(MIXED_CALLS), input, runtimeResult);

    // the first call is the runtime tool call, the second one belongs to the process
    assertThat(turnInput.documents())
        .containsExactly(
            new NewDocument(runtimeDocument, DocumentOrigin.toolCall("getWeather", "abcdef")),
            new NewDocument(nestedRuntimeDocument, DocumentOrigin.toolCall("getWeather", "abcdef")),
            new NewDocument(processDocument, DocumentOrigin.toolCall("getDateTime", "fedcba")));
  }

  @Test
  void runtimeToolErrorResult_ingestsNoDocument() {
    var errorResult = RUNTIME_RESULT.withContent(List.of(textContent("The tool failed.")));
    var input = inputWith(TOOL_CALL_RESULTS.get(1));

    var turnInput = assembleWithLocalResults(CONFIG, historyWith(MIXED_CALLS), input, errorResult);

    assertThat(turnInput.documents()).isEmpty();
  }

  @Test
  void aTurnOfRuntimeToolCallsOnly_isReadyWithAllCallsUnansweredAndNoProcessResult() {
    var history = historyWith(RUNTIME_CALLS);

    var ready = resolveReady(CONFIG, history, inputWith());

    assertThat(ready.toolCallBatch()).isEqualTo(ToolCallBatch.of(RUNTIME_CALLS));
    assertThat(ready.unansweredToolCalls()).isEqualTo(RUNTIME_CALLS);
    assertThat(ready.userPrompt()).isEqualTo(TurnInput.empty());
    assertThat(ready.events()).isEqualTo(TurnInput.empty());

    var turnInput =
        composer.assemble(
            ready.withResults(List.of(entryOf(SECOND_RUNTIME_RESULT), entryOf(RUNTIME_RESULT))));

    assertThat(resultsOf(turnInput)).containsExactly(RUNTIME_RESULT, SECOND_RUNTIME_RESULT);
  }

  @Test
  void localToolCalls_areUnansweredOnceTheProcessResultsHaveArrived() {
    var ready = resolveReady(CONFIG, historyWith(MIXED_CALLS), inputWith(TOOL_CALL_RESULTS.get(1)));

    assertThat(ready.unansweredToolCalls()).containsExactly(MIXED_CALLS.getFirst());
    assertThat(ready.toolCallBatch())
        .isEqualTo(
            ToolCallBatch.of(MIXED_CALLS)
                .withEntries(
                    List.of(entryOf(ToolCallResultContent.from(TOOL_CALL_RESULTS.get(1))))));
  }

  @Test
  void localToolCalls_defersWhileTheProcessResultsAreMissing() {
    var result =
        composer.resolve(CONFIG, CTX_WITH_CONVERSATION, historyWith(MIXED_CALLS), inputWith());

    assertThat(result).isInstanceOf(CompositionResult.Deferred.class);
    assertThat(((CompositionResult.Deferred) result).arrivedResults()).isEmpty();
  }

  @Test
  void localToolCalls_doNotGoThroughTheGatewayTransformation() {
    var ready = resolveReady(CONFIG, historyWith(MIXED_CALLS), inputWith(TOOL_CALL_RESULTS.get(1)));
    composer.assemble(ready.withResults(List.of(entryOf(RUNTIME_RESULT))));

    verify(gatewayToolHandlers, times(1))
        .transformToolCallResults(any(), eq(List.of(TOOL_CALL_RESULTS.get(1))));
    verify(gatewayToolHandlers, times(1)).transformToolCallResults(any(), any());
  }

  @Test
  void localToolCalls_partialArrivalDefersAndCarriesOnlyTheProcessResults() {
    var threeCalls =
        List.of(
            MIXED_CALLS.get(0),
            MIXED_CALLS.get(1),
            ToolCall.builder().id("third").name("getTime").build());
    var thirdResult = ToolCallResult.builder().id("third").name("getTime").content("noon").build();

    var result =
        composer.resolve(
            CONFIG, CTX_WITH_CONVERSATION, historyWith(threeCalls), inputWith(thirdResult));

    // "fedcba" is still running: wait for it, the runtime tool call is not reported with the
    // arrivals
    assertThat(result).isInstanceOf(CompositionResult.Deferred.class);
    assertThat(((CompositionResult.Deferred) result).arrivedResults()).containsExactly(thirdResult);
  }

  @Test
  void localToolCalls_interruptCancelsTheRuntimeCallsTooAsTheyWereNotExecutedYet() {
    var input = inputWith(ToolCallResult.builder().content("An event occurred").build());

    var ready = resolveReady(INTERRUPTING_CONFIG, historyWith(MIXED_CALLS), input);

    // nothing is left to execute: the runtime call is cancelled like the missing process call
    assertThat(ready.unansweredToolCalls()).isEmpty();
    var turnInput = composer.assemble(ready);
    var results = resultsOf(turnInput);
    assertThat(results).extracting(ToolCallResultContent::id).containsExactly("abcdef", "fedcba");
    assertThat(results)
        .allSatisfy(result -> {
          assertThat(result.content())
              .containsExactly(textContent(ToolCallResult.CONTENT_CANCELLED));
          assertThat(result.properties()).containsEntry(ToolCallResult.PROPERTY_INTERRUPTED, true);
        });
    assertThat(turnInput.messages().getLast())
        .isInstanceOfSatisfying(
            UserMessage.class,
            event -> assertThat(event.content()).containsExactly(textContent("An event occurred")));
  }

  @Test
  void localToolCalls_interruptKeepsTheArrivedProcessResultsAndTheRejections() {
    var rejectedCall =
        ToolCall.builder()
            .id("rejected")
            .name("unknownTool")
            .build()
            .withRejection("The tool 'unknownTool' does not exist.");
    var calls = List.of(MIXED_CALLS.get(0), MIXED_CALLS.get(1), rejectedCall);
    var input =
        inputWith(
            TOOL_CALL_RESULTS.get(1),
            ToolCallResult.builder().content("An event occurred").build());

    var ready = resolveReady(INTERRUPTING_CONFIG, historyWith(calls), input);

    assertThat(ready.unansweredToolCalls()).isEmpty();
    var results = resultsOf(composer.assemble(ready));
    assertThat(results)
        .extracting(ToolCallResultContent::id)
        .containsExactly("abcdef", "fedcba", "rejected");
    assertThat(results.get(0).content())
        .containsExactly(textContent(ToolCallResult.CONTENT_CANCELLED));
    assertThat(results.get(1)).isEqualTo(ToolCallResultContent.from(TOOL_CALL_RESULTS.get(1)));
    assertThat(results.get(2).content())
        .containsExactly(textContent("The tool 'unknownTool' does not exist."));
  }

  @Test
  void assemblingWithoutTheResultOfALocalToolCall_fails() {
    var ready = resolveReady(CONFIG, historyWith(MIXED_CALLS), inputWith(TOOL_CALL_RESULTS.get(1)));

    assertThatThrownBy(() -> composer.assemble(ready))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("abcdef")
        .hasMessageNotContaining("fedcba");
  }

  @Test
  void answeringAProcessToolCallAgain_fails() {
    var ready = resolveReady(CONFIG, historyWith(MIXED_CALLS), inputWith(TOOL_CALL_RESULTS.get(1)));
    var entries = List.of(entryOf(RUNTIME_RESULT), entryOf(SECOND_RUNTIME_RESULT));

    assertThatThrownBy(() -> ready.withResults(entries))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fedcba");
  }

  @Test
  void aTurnWithoutLocalToolCalls_isAssembledWithoutAnyLocalResult() {
    var ready =
        resolveReady(
            CONFIG,
            historyWith(TOOL_CALLS),
            inputWith(TOOL_CALL_RESULTS.toArray(ToolCallResult[]::new)));

    assertThat(ready.unansweredToolCalls()).isEmpty();
    assertThat(resultsOf(composer.assemble(ready)))
        .containsExactly(
            ToolCallResultContent.from(TOOL_CALL_RESULTS.get(0)),
            ToolCallResultContent.from(TOOL_CALL_RESULTS.get(1)));
  }

  @Test
  void localToolCalls_areNotUnansweredForAPromptTurn() {
    var input = AgentInput.from(new UserPromptConfiguration("Hello?", List.of()), List.of());
    var history =
        TurnReconstructor.reconstruct(List.of(userMessage("hi"), assistantMessage("hello")));

    var result = composer.resolve(CONFIG, CTX_WITH_CONVERSATION, history, input);

    assertThat(messagesOf(result)).singleElement().isInstanceOf(UserMessage.class);
  }
}
