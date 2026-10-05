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

import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentInput;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration.EventHandlingBehavior;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentConversationTurnInputComposerImplTest {

  private AgentConversationTurnInputComposerImpl composer;
  private GatewayToolHandlerRegistry gatewayToolHandlers;

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  private static final AgentContext CTX = AgentContext.builder().state(AgentState.READY).build();
  // a context with a previous conversation cursor — the realistic state when tool results arrive
  private static final AgentContext CTX_WITH_CONVERSATION =
      AgentContext.builder()
          .state(AgentState.READY)
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
    var result = composer.compose(CONFIG, CTX, history, input);
    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
    var nextTurn = (CompositionResult.NextTurn) result;
    assertThat(nextTurn.messages()).hasSize(1);
    assertThat(nextTurn.messages().getFirst()).isInstanceOf(UserMessage.class);
  }

  @Test
  void firstTurn_emptyPrompt_returnsNoInput() {
    var input = AgentInput.from(new UserPromptConfiguration("", List.of()), List.of());
    var history = TurnReconstructor.reconstruct(List.of());
    var result = composer.compose(CONFIG, CTX, history, input);
    assertThat(result).isInstanceOf(CompositionResult.NoInput.class);
  }

  @Test
  void toolResultsOnEmptyContext_throwsConnectorException() {
    // tool call results arriving with no previous conversation is a modeling error, not a no-op
    var input =
        AgentInput.from(new UserPromptConfiguration("user input", List.of()), TOOL_CALL_RESULTS);
    var history = TurnReconstructor.reconstruct(List.of());

    assertThatThrownBy(() -> composer.compose(CONFIG, CTX, history, input))
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
    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input);
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
    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input);
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
    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input);
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

    var result = composer.compose(config, CTX_WITH_CONVERSATION, history, input);

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
  void firstTurn_withUserPromptDocuments_addsDocumentContent() {
    var document = createDocument("story notes", "text/plain", "notes.txt");
    var input =
        AgentInput.from(
            new UserPromptConfiguration("Tell me a story", List.of(document)), List.of());
    var history = TurnReconstructor.reconstruct(List.of());

    var result = composer.compose(CONFIG, CTX, history, input);

    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
    var userMessage = (UserMessage) ((CompositionResult.NextTurn) result).messages().getFirst();
    assertThat(userMessage.content())
        .containsExactly(
            textContent("Tell me a story"),
            textContent(
                "<doc id=\"%s\" fileName=\"notes.txt\" contentType=\"text/plain\" />"
                    .formatted(DocumentHandle.idFor(document))),
            DocumentContent.documentContent(document));
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

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input);

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

    var result = composer.compose(config, CTX_WITH_CONVERSATION, history, input);

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

    var result = composer.compose(config, CTX_WITH_CONVERSATION, history, input);

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

    var result = composer.compose(config, CTX_WITH_CONVERSATION, history, input);

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

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input);

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

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input);

    var message =
        (ToolCallResultMessage) ((CompositionResult.NextTurn) result).messages().getFirst();
    assertThat(message.results())
        .containsExactlyElementsOf(
            transformedResults.stream().map(ToolCallResultContent::from).toList());
  }

  @Test
  void toolResultTurn_resultsContainDocuments_emitsToolCallDocumentMessage() {
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

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input);

    var messages = ((CompositionResult.NextTurn) result).messages();
    assertThat(messages).hasSize(2);
    assertThat(messages.getFirst()).isInstanceOf(ToolCallResultMessage.class);
    assertThat(messages.get(1))
        .isInstanceOfSatisfying(
            UserMessage.class,
            documentMessage -> {
              assertThat(documentMessage.metadata())
                  .containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
              assertThat(documentMessage.content())
                  .first()
                  .isEqualTo(
                      textContent(
                          AgentConversationTurnInputComposerImpl.TOOL_CALL_DOCUMENTS_PREAMBLE));
              assertThat(documentMessage.content())
                  .contains(
                      textContent(
                          "<doc id=\"%s\" fileName=\"weather.txt\" contentType=\"text/plain\" toolName=\"getWeather\" toolCallId=\"abcdef\" />"
                              .formatted(DocumentHandle.idFor(weatherDoc))),
                      DocumentContent.documentContent(weatherDoc));
            });
  }

  @Test
  void toolResultTurn_bareDocumentResult_liftsToDocumentContentAndAlsoDeliversFallbackMessage() {
    // a tool call result whose whole content is a Document (not nested in an object): the
    // ToolCallResultContent lift turns this into a first-class DocumentContent, same as every
    // other content-lift path; the fallback <doc/> message is still composed alongside it
    // regardless — provider tool-result converters are responsible for not embedding the same
    // bytes a second time, not this provider-agnostic composition step
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

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input);

    var messages = ((CompositionResult.NextTurn) result).messages();
    assertThat(messages).hasSize(2);
    var toolCallResultMessage = (ToolCallResultMessage) messages.getFirst();
    assertThat(toolCallResultMessage.results())
        .filteredOn(r -> "abcdef".equals(r.id()))
        .first()
        .satisfies(
            r ->
                assertThat(r.content())
                    .containsExactly(DocumentContent.documentContent(weatherDoc)));
    assertThat(messages.get(1))
        .isInstanceOfSatisfying(
            UserMessage.class,
            documentMessage ->
                assertThat(documentMessage.content())
                    .contains(DocumentContent.documentContent(weatherDoc)));
  }

  @Test
  void waitForToolResults_toolAndEventDocuments_emitsMessagesInOrder() {
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

    var result = composer.compose(config, CTX_WITH_CONVERSATION, history, input);

    // order: tool call results -> tool-call documents -> event (with its documents)
    var messages = ((CompositionResult.NextTurn) result).messages();
    assertThat(messages).hasSize(3);
    assertThat(messages.get(0)).isInstanceOf(ToolCallResultMessage.class);
    assertThat(messages.get(1))
        .isInstanceOfSatisfying(
            UserMessage.class,
            toolCallDocuments -> {
              assertThat(toolCallDocuments.metadata())
                  .containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
              assertThat(toolCallDocuments.content())
                  .first()
                  .isEqualTo(
                      textContent(
                          AgentConversationTurnInputComposerImpl.TOOL_CALL_DOCUMENTS_PREAMBLE));
              assertThat(toolCallDocuments.content())
                  .contains(DocumentContent.documentContent(toolDoc));
            });
    assertThat(messages.get(2))
        .isInstanceOfSatisfying(
            UserMessage.class,
            eventMessage -> {
              assertThat(eventMessage.content())
                  .contains(
                      textContent(AgentConversationTurnInputComposerImpl.EVENT_DOCUMENTS_PREAMBLE))
                  .contains(
                      textContent(
                          "<doc id=\"%s\" fileName=\"event.pdf\" contentType=\"application/pdf\" />"
                              .formatted(DocumentHandle.idFor(eventDoc))),
                      DocumentContent.documentContent(eventDoc));
              assertThat(eventMessage.metadata())
                  .doesNotContainKey(UserMessage.METADATA_TOOL_CALL_DOCUMENTS);
            });
  }

  @Test
  void firstTurn_userPromptDocuments_areIngestedWithoutOrigin() {
    var promptDoc = createDocument("prompt data", "text/plain", "prompt.txt");
    var input =
        AgentInput.from(new UserPromptConfiguration("Hello?", List.of(promptDoc)), List.of());

    var result = composer.compose(CONFIG, CTX, TurnReconstructor.reconstruct(List.of()), input);

    assertThat(((CompositionResult.NextTurn) result).documents())
        .containsExactly(new IngestedDocument(promptDoc, null, null));
  }

  @Test
  void toolResultTurn_documentsAreIngestedWithToolOriginFollowedByEventDocuments() {
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

    var result = composer.compose(config, CTX_WITH_CONVERSATION, history, input);

    assertThat(((CompositionResult.NextTurn) result).documents())
        .containsExactlyInAnyOrder(
            new IngestedDocument(toolDoc, "abcdef", "getWeather"),
            new IngestedDocument(nestedToolDoc, "abcdef", "getWeather"),
            new IngestedDocument(eventDoc, null, null))
        .last()
        .isEqualTo(new IngestedDocument(eventDoc, null, null));
  }

  @Test
  void toolResultTurn_withoutDocuments_ingestsNone() {
    var input =
        AgentInput.from(new UserPromptConfiguration("user input", List.of()), TOOL_CALL_RESULTS);
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input);

    assertThat(((CompositionResult.NextTurn) result).documents()).isEmpty();
  }

  private static final ToolCallResult LOCAL_RESULT =
      ToolCallResult.builder()
          .id("abcdef")
          .name("getWeather")
          .content("answered by the agent")
          .completedAt(java.time.OffsetDateTime.parse("2026-10-05T10:00:00Z"))
          .build();

  private static AgentContext contextWithPending(ToolCallResult... pending) {
    return CTX_WITH_CONVERSATION.withPendingLocalToolCallResults(List.of(pending));
  }

  private static List<ToolCallResultContent> resultsOf(CompositionResult result) {
    return ((ToolCallResultMessage) ((CompositionResult.NextTurn) result).messages().getFirst())
        .results();
  }

  @Test
  void pendingLocalResults_mergeWithTheArrivedProcessResultsInToolCallOrder() {
    // the second call is the real one: its result arrives, the first was answered by the agent
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(TOOL_CALL_RESULTS.get(1)));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(CONFIG, contextWithPending(LOCAL_RESULT), history, input);

    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
    assertThat(resultsOf(result))
        .extracting(ToolCallResultContent::id, ToolCallResultContent::name)
        .containsExactly(tuple("abcdef", "getWeather"), tuple("fedcba", "getDateTime"));
    assertThat(resultsOf(result).getFirst().content())
        .containsExactly(textContent("answered by the agent"));
    // a message with results of both kinds keeps the local completion time
    assertThat(resultsOf(result).getFirst().completedAt()).isEqualTo(LOCAL_RESULT.completedAt());
  }

  @Test
  void pendingLocalResults_keepToolCallOrderWhenTheLocalCallComesLast() {
    var localSecond = LOCAL_RESULT.withId("fedcba").withName("getDateTime");
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(TOOL_CALL_RESULTS.getFirst()));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(CONFIG, contextWithPending(localSecond), history, input);

    assertThat(resultsOf(result))
        .extracting(ToolCallResultContent::id)
        .containsExactly("abcdef", "fedcba");
  }

  @Test
  void pendingLocalResults_doNotGoThroughTheGatewayTransformation() {
    var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(TOOL_CALL_RESULTS.get(1)));
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    composer.compose(CONFIG, contextWithPending(LOCAL_RESULT), history, input);

    org.mockito.Mockito.verify(gatewayToolHandlers)
        .transformToolCallResults(
            any(), org.mockito.ArgumentMatchers.eq(List.of(TOOL_CALL_RESULTS.get(1))));
  }

  @Test
  void pendingLocalResults_partialArrivalDefersAndCarriesOnlyTheProcessResults() {
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

    var result = composer.compose(CONFIG, contextWithPending(LOCAL_RESULT), history, input);

    // "fedcba" is still running: wait for it, the local result is not reported with the arrivals
    assertThat(result).isInstanceOf(CompositionResult.Deferred.class);
    assertThat(((CompositionResult.Deferred) result).arrivedResults()).containsExactly(thirdResult);
  }

  @Test
  void pendingLocalResults_withoutAnyProcessResultDeferWithNothingArrived() {
    var input = AgentInput.from(new UserPromptConfiguration("user input", List.of()), List.of());
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(CONFIG, contextWithPending(LOCAL_RESULT), history, input);

    assertThat(result).isInstanceOf(CompositionResult.Deferred.class);
    assertThat(((CompositionResult.Deferred) result).arrivedResults()).isEmpty();
  }

  @Test
  void pendingLocalResults_interruptCancelsOnlyTheMissingProcessCalls() {
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

    var result = composer.compose(config, contextWithPending(LOCAL_RESULT), history, input);

    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
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
  void pendingLocalResults_ofOtherToolCallsAreIgnored() {
    var stale = LOCAL_RESULT.withId("from-an-earlier-turn");
    var input =
        AgentInput.from(new UserPromptConfiguration("user input", List.of()), TOOL_CALL_RESULTS);
    var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    var result = composer.compose(CONFIG, contextWithPending(stale), history, input);

    assertThat(resultsOf(result))
        .extracting(ToolCallResultContent::id)
        .containsExactly("abcdef", "fedcba");
    assertThat(resultsOf(result).getFirst().content()).containsExactly(textContent("Sunny, 22°C"));
  }

  @Test
  void pendingLocalResults_areNotUsedForAPromptTurn() {
    var input = AgentInput.from(new UserPromptConfiguration("Hello?", List.of()), List.of());
    var history =
        TurnReconstructor.reconstruct(List.of(userMessage("hi"), assistantMessage("hello")));

    var result = composer.compose(CONFIG, contextWithPending(LOCAL_RESULT), history, input);

    assertThat(((CompositionResult.NextTurn) result).messages())
        .singleElement()
        .isInstanceOf(UserMessage.class);
  }
}
