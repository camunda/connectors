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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.conversation.AgentInput;
import io.camunda.connector.agenticai.aiagent.conversation.PreviousConversation;
import io.camunda.connector.agenticai.aiagent.conversation.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentGuard;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentLimits;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentReferences;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistries;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
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
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
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
            gatewayToolHandlers,
            new ToolCallResultsInputBuilderImpl(
                new DocumentContentGuard(DocumentContentLimits.defaults()),
                TestObjectMapperSupplier.INSTANCE),
            new DocumentContentGuard(DocumentContentLimits.defaults()));
  }

  private CompositionResult compose(
      AgentConfiguration configuration,
      AgentContext agentContext,
      PreviousConversation history,
      AgentInput input) {
    return composer.compose(configuration, agentContext, history, input);
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
    var result = compose(CONFIG, CTX, history, input);
    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
    var nextTurn = (CompositionResult.NextTurn) result;
    assertThat(nextTurn.messages()).hasSize(1);
    assertThat(nextTurn.messages().getFirst()).isInstanceOf(UserMessage.class);
  }

  @Test
  void firstTurn_emptyPrompt_returnsNoInput() {
    var input = AgentInput.from(new UserPromptConfiguration("", List.of()), List.of());
    var history = TurnReconstructor.reconstruct(List.of());
    var result = compose(CONFIG, CTX, history, input);
    assertThat(result).isInstanceOf(CompositionResult.NoInput.class);
  }

  @Test
  void toolResultsOnEmptyContext_throwsConnectorException() {
    // tool call results arriving with no previous conversation is a modeling error, not a no-op
    var input =
        AgentInput.from(new UserPromptConfiguration("user input", List.of()), TOOL_CALL_RESULTS);
    var history = TurnReconstructor.reconstruct(List.of());

    assertThatThrownBy(() -> compose(CONFIG, CTX, history, input))
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
    var result = compose(CONFIG, CTX_WITH_CONVERSATION, history, input);
    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
    var nextTurn = (CompositionResult.NextTurn) result;
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
    var result = compose(CONFIG, CTX_WITH_CONVERSATION, history, input);
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
    var result = compose(CONFIG, CTX_WITH_CONVERSATION, history, input);
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

    var result = compose(config, CTX_WITH_CONVERSATION, history, input);

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
  void firstTurn_withUserPromptDocumentsOfTheDeliveryReference_addsTheTagWithoutTheContent() {
    var document = createDocument("story notes", "text/plain", "notes.txt");
    var input =
        AgentInput.from(
            new UserPromptConfiguration(
                "Tell me a story", List.of(document), DocumentDelivery.REFERENCE),
            List.of());
    var history = TurnReconstructor.reconstruct(List.of());

    var result = compose(CONFIG, CTX, history, input);

    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
    var userMessage = (UserMessage) ((CompositionResult.NextTurn) result).messages().getFirst();
    assertThat(userMessage.content())
        .containsExactly(
            textContent("Tell me a story"),
            documentReference(
                document,
                "notes.txt",
                "text/plain",
                DocumentSource.userMessage(),
                DocumentDelivery.REFERENCE));
    assertThat(((DocumentReferenceContent) userMessage.content().get(1)).toTag())
        .isEqualTo(
            "<doc id=\"%s\" fileName=\"notes.txt\" contentType=\"text/plain\" source=\"user_message\" />"
                .formatted(DocumentHandle.idFor(document)));
    assertThat(((CompositionResult.NextTurn) result).documents())
        .containsExactly(new IngestedDocument(document, DocumentSource.userMessage()));
  }

  private static DocumentReferenceContent documentReference(
      Document document,
      String fileName,
      String contentType,
      DocumentSource source,
      DocumentDelivery delivery) {
    return new DocumentReferenceContent(
        DocumentHandle.idFor(document), fileName, contentType, null, source, delivery, null);
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

    var result = compose(CONFIG, CTX_WITH_CONVERSATION, history, input);

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

    var result = compose(config, CTX_WITH_CONVERSATION, history, input);

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

    var result = compose(config, CTX_WITH_CONVERSATION, history, input);

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

    var result = compose(config, CTX_WITH_CONVERSATION, history, input);

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

    var result = compose(CONFIG, CTX_WITH_CONVERSATION, history, input);

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

    var result = compose(CONFIG, CTX_WITH_CONVERSATION, history, input);

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

    var result = compose(CONFIG, CTX_WITH_CONVERSATION, history, input);

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
            new IngestedDocument(weatherDoc, DocumentSource.toolCall("getWeather", "abcdef")));
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

    var result = compose(CONFIG, CTX_WITH_CONVERSATION, history, input);

    var nextTurn = (CompositionResult.NextTurn) result;
    assertThat(nextTurn.messages()).hasSize(1);
    var toolCallResultMessage = (ToolCallResultMessage) nextTurn.messages().getFirst();
    assertThat(toolCallResultMessage.results())
        .filteredOn(r -> "abcdef".equals(r.id()))
        .first()
        .satisfies(
            r -> assertThat(r.content()).containsExactly(DocumentReferences.from(weatherDoc)));
    assertThat(nextTurn.documents())
        .containsExactly(
            new IngestedDocument(weatherDoc, DocumentSource.toolCall("getWeather", "abcdef")));
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

    var result = compose(CONFIG, legacyContext, history, input);

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

    var result = compose(config, CTX_WITH_CONVERSATION, history, input);

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
                          DocumentSource.event(),
                          DocumentDelivery.REFERENCE))
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

    var result = compose(config, CTX_WITH_CONVERSATION, history, input);

    final var nextTurn = (CompositionResult.NextTurn) result;
    assertThat(nextTurn.messages().get(0)).isInstanceOf(ToolCallResultMessage.class);
    assertThat(nextTurn.documents())
        .containsExactlyInAnyOrder(
            new IngestedDocument(toolDoc, DocumentSource.toolCall("getWeather", "abcdef")),
            new IngestedDocument(nestedToolDoc, DocumentSource.toolCall("getWeather", "abcdef")),
            new IngestedDocument(eventDoc, DocumentSource.event()))
        .last()
        .isEqualTo(new IngestedDocument(eventDoc, DocumentSource.event()));
  }

  private static final ToolCallResultContent RUNTIME_RESULT =
      ToolCallResultContent.builder()
          .id("abcdef")
          .name("getWeather")
          .content(List.of(textContent("result of the runtime tool")))
          .completedAt(OffsetDateTime.parse("2026-10-05T10:00:00Z"))
          .build();

  /** The first call is a runtime tool call, the second one goes to the process. */
  private static final List<ToolCall> MIXED_CALLS =
      List.of(TOOL_CALLS.get(0).withKind(ToolKind.RUNTIME), TOOL_CALLS.get(1));

  /** Every call is a runtime tool call. */
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

  private static List<RuntimeToolExecution> executionsOf(
      List<ToolCall> calls, ToolCallResultContent... results) {
    return Arrays.stream(results)
        .map(
            result ->
                new RuntimeToolExecution(
                    calls.stream()
                        .filter(call -> call.id().equals(result.id()))
                        .findFirst()
                        .orElseThrow(),
                    RuntimeToolResult.success(result.content()),
                    result.completedAt(),
                    result.completedAt()))
        .toList();
  }

  /**
   * Composes like the request handler: when the turn needs the results of its runtime tool calls,
   * they are given to the second step.
   */
  private CompositionResult composeWithRuntimeResults(
      AgentConfiguration configuration,
      PreviousConversation history,
      AgentInput input,
      ToolCallResultContent... runtimeResults) {
    final var result = composer.compose(configuration, CTX_WITH_CONVERSATION, history, input);
    if (result instanceof CompositionResult.RuntimeToolResultsRequired required) {
      return composer.composeWithRuntimeToolResults(
          configuration,
          CTX_WITH_CONVERSATION,
          history,
          input,
          executionsOf(required.runtimeToolCalls(), runtimeResults));
    }
    return result;
  }

  private static List<ToolCallResultContent> resultsOf(CompositionResult result) {
    return ((ToolCallResultMessage) ((CompositionResult.NextTurn) result).messages().getFirst())
        .results();
  }

  @Test
  void runtimeToolCalls_mergeWithTheArrivedProcessResultsInToolCallOrder() {
    // the first call is the runtime tool call, the result of the second one arrives
    var input = inputWith(TOOL_CALL_RESULTS.get(1));

    var result = composeWithRuntimeResults(CONFIG, historyWith(MIXED_CALLS), input, RUNTIME_RESULT);

    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
    assertThat(resultsOf(result))
        .extracting(ToolCallResultContent::id, ToolCallResultContent::name)
        .containsExactly(tuple("abcdef", "getWeather"), tuple("fedcba", "getDateTime"));
    assertThat(resultsOf(result).getFirst().content())
        .containsExactly(textContent("result of the runtime tool"));
    assertThat(resultsOf(result).getFirst().completedAt()).isEqualTo(RUNTIME_RESULT.completedAt());
  }

  @Test
  void runtimeToolCalls_keepToolCallOrderWhenTheRuntimeCallComesLast() {
    var calls = List.of(TOOL_CALLS.get(0), TOOL_CALLS.get(1).withKind(ToolKind.RUNTIME));
    var input = inputWith(TOOL_CALL_RESULTS.getFirst());

    var result =
        composeWithRuntimeResults(CONFIG, historyWith(calls), input, SECOND_RUNTIME_RESULT);

    assertThat(resultsOf(result))
        .extracting(ToolCallResultContent::id)
        .containsExactly("abcdef", "fedcba");
  }

  @Test
  void runtimeToolCallResult_thatIsADocumentIsPersistedAsItsReferenceAndIngested() {
    var document = createDocument("first", "text/plain", "first.txt");
    var runtimeResult =
        RUNTIME_RESULT.withContent(List.of(DocumentContent.documentContent(document)));
    var input = inputWith(TOOL_CALL_RESULTS.get(1));

    var result = composeWithRuntimeResults(CONFIG, historyWith(MIXED_CALLS), input, runtimeResult);

    assertThat(resultsOf(result).getFirst().content())
        .containsExactly(DocumentReferences.from(document));
    // the document of the result of a runtime tool is registered with the call that returned it
    assertThat(((CompositionResult.NextTurn) result).documents())
        .containsExactly(
            new IngestedDocument(document, DocumentSource.toolCall("getWeather", "abcdef")));
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

    var result = composeWithRuntimeResults(CONFIG, historyWith(MIXED_CALLS), input, runtimeResult);

    // the first call is the runtime tool call, the second one belongs to the process
    assertThat(((CompositionResult.NextTurn) result).documents())
        .containsExactly(
            new IngestedDocument(runtimeDocument, DocumentSource.toolCall("getWeather", "abcdef")),
            new IngestedDocument(
                nestedRuntimeDocument, DocumentSource.toolCall("getWeather", "abcdef")),
            new IngestedDocument(
                processDocument, DocumentSource.toolCall("getDateTime", "fedcba")));
  }

  @Test
  void runtimeToolErrorResult_ingestsNoDocument() {
    var errorResult = RUNTIME_RESULT.withContent(List.of(textContent("The tool failed.")));
    var input = inputWith(TOOL_CALL_RESULTS.get(1));

    var result = composeWithRuntimeResults(CONFIG, historyWith(MIXED_CALLS), input, errorResult);

    assertThat(((CompositionResult.NextTurn) result).documents()).isEmpty();
  }

  @Test
  void aTurnOfRuntimeToolCallsOnly_needsTheirResultsWithoutAnyProcessResult() {
    var input = inputWith();
    var history = historyWith(RUNTIME_CALLS);

    var required = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input);

    assertThat(required).isEqualTo(new CompositionResult.RuntimeToolResultsRequired(RUNTIME_CALLS));

    var result =
        composer.composeWithRuntimeToolResults(
            CONFIG,
            CTX_WITH_CONVERSATION,
            history,
            input,
            executionsOf(RUNTIME_CALLS, SECOND_RUNTIME_RESULT, RUNTIME_RESULT));

    assertThat(resultsOf(result))
        .extracting(ToolCallResultContent::id)
        .containsExactly("abcdef", "fedcba");
  }

  @Test
  void runtimeToolCalls_needTheirResultsOnceTheProcessResultsHaveArrived() {
    var result =
        composer.compose(
            CONFIG,
            CTX_WITH_CONVERSATION,
            historyWith(MIXED_CALLS),
            inputWith(TOOL_CALL_RESULTS.get(1)));

    assertThat(result)
        .isEqualTo(
            new CompositionResult.RuntimeToolResultsRequired(List.of(MIXED_CALLS.getFirst())));
  }

  @Test
  void runtimeToolCalls_areNotRequiredWhileTheProcessResultsAreMissing() {
    var result =
        composer.compose(CONFIG, CTX_WITH_CONVERSATION, historyWith(MIXED_CALLS), inputWith());

    assertThat(result).isInstanceOf(CompositionResult.Deferred.class);
    assertThat(((CompositionResult.Deferred) result).arrivedResults()).isEmpty();
  }

  @Test
  void runtimeToolCalls_doNotGoThroughTheGatewayTransformation() {
    composeWithRuntimeResults(
        CONFIG, historyWith(MIXED_CALLS), inputWith(TOOL_CALL_RESULTS.get(1)), RUNTIME_RESULT);

    verify(gatewayToolHandlers, atLeastOnce())
        .transformToolCallResults(any(), eq(List.of(TOOL_CALL_RESULTS.get(1))));
  }

  @Test
  void runtimeToolCalls_partialArrivalDefersAndCarriesOnlyTheProcessResults() {
    var threeCalls =
        List.of(
            MIXED_CALLS.get(0),
            MIXED_CALLS.get(1),
            ToolCall.builder().id("third").name("getTime").build());
    var thirdResult = ToolCallResult.builder().id("third").name("getTime").content("noon").build();

    var result =
        composer.compose(
            CONFIG, CTX_WITH_CONVERSATION, historyWith(threeCalls), inputWith(thirdResult));

    // "fedcba" is still running: wait for it, the runtime tool call is not reported with the
    // arrivals and not executed yet
    assertThat(result).isInstanceOf(CompositionResult.Deferred.class);
    assertThat(((CompositionResult.Deferred) result).arrivedResults()).containsExactly(thirdResult);
  }

  @Test
  void runtimeToolCalls_interruptCancelsOnlyTheMissingProcessCalls() {
    var input = inputWith(ToolCallResult.builder().content("An event occurred").build());

    var result =
        composeWithRuntimeResults(
            INTERRUPTING_CONFIG, historyWith(MIXED_CALLS), input, RUNTIME_RESULT);

    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
    // the call of the runtime tool is executed although the turn is interrupted
    var results = resultsOf(result);
    assertThat(results).extracting(ToolCallResultContent::id).containsExactly("abcdef", "fedcba");
    assertThat(results.get(0).content()).containsExactly(textContent("result of the runtime tool"));
    assertThat(results.get(0).properties()).doesNotContainKey(ToolCallResult.PROPERTY_INTERRUPTED);
    assertThat(results.get(1).content())
        .containsExactly(textContent(ToolCallResult.CONTENT_CANCELLED));
    assertThat(results.get(1).properties())
        .containsEntry(ToolCallResult.PROPERTY_INTERRUPTED, true);
  }

  @Test
  void runtimeToolCalls_interruptWithoutAnyProcessCallStillNeedsTheirResults() {
    var input = inputWith(ToolCallResult.builder().content("An event occurred").build());

    var result =
        composer.compose(
            INTERRUPTING_CONFIG, CTX_WITH_CONVERSATION, historyWith(RUNTIME_CALLS), input);

    assertThat(result).isEqualTo(new CompositionResult.RuntimeToolResultsRequired(RUNTIME_CALLS));
  }

  @Test
  void composingWithRuntimeToolResults_neverRequiresThemAgain() {
    var result =
        composer.composeWithRuntimeToolResults(
            CONFIG,
            CTX_WITH_CONVERSATION,
            historyWith(MIXED_CALLS),
            inputWith(TOOL_CALL_RESULTS.get(1)),
            executionsOf(MIXED_CALLS, RUNTIME_RESULT));

    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
  }

  @Test
  void composingWithRuntimeToolResults_defersWhileTheProcessResultsAreMissing() {
    var result =
        composer.composeWithRuntimeToolResults(
            CONFIG,
            CTX_WITH_CONVERSATION,
            historyWith(MIXED_CALLS),
            inputWith(),
            executionsOf(MIXED_CALLS, RUNTIME_RESULT));

    assertThat(result).isInstanceOf(CompositionResult.Deferred.class);
  }

  @Test
  void composingWithoutTheResultOfARuntimeToolCall_fails() {
    var history = historyWith(MIXED_CALLS);
    var input = inputWith(TOOL_CALL_RESULTS.get(1));

    assertThatThrownBy(
            () ->
                composer.composeWithRuntimeToolResults(
                    CONFIG, CTX_WITH_CONVERSATION, history, input, List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("abcdef");
  }

  @Test
  void composingWithAnExecutionOfAnotherToolCall_fails() {
    var history = historyWith(MIXED_CALLS);
    var input = inputWith(TOOL_CALL_RESULTS.get(1));
    // "fedcba" is a process tool call: it has no execution
    var executions = executionsOf(TOOL_CALLS, RUNTIME_RESULT, SECOND_RUNTIME_RESULT);

    assertThatThrownBy(
            () ->
                composer.composeWithRuntimeToolResults(
                    CONFIG, CTX_WITH_CONVERSATION, history, input, executions))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("abcdef")
        .hasMessageContaining("fedcba");
  }

  @Test
  void composingWithTheSameExecutionTwice_fails() {
    var history = historyWith(MIXED_CALLS);
    var input = inputWith(TOOL_CALL_RESULTS.get(1));
    var executions = executionsOf(MIXED_CALLS, RUNTIME_RESULT, RUNTIME_RESULT);

    assertThatThrownBy(
            () ->
                composer.composeWithRuntimeToolResults(
                    CONFIG, CTX_WITH_CONVERSATION, history, input, executions))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void composingWithExecutionsForATurnWithoutRuntimeToolCalls_fails() {
    var history = historyWith(TOOL_CALLS);
    var input = inputWith(TOOL_CALL_RESULTS.toArray(ToolCallResult[]::new));
    var executions = executionsOf(MIXED_CALLS, RUNTIME_RESULT);

    assertThatThrownBy(
            () ->
                composer.composeWithRuntimeToolResults(
                    CONFIG, CTX_WITH_CONVERSATION, history, input, executions))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("abcdef");
  }

  @Test
  void composingWithoutExecutionsForATurnWithoutRuntimeToolCalls_composesTheTurn() {
    var result =
        composer.composeWithRuntimeToolResults(
            CONFIG,
            CTX_WITH_CONVERSATION,
            historyWith(TOOL_CALLS),
            inputWith(TOOL_CALL_RESULTS.toArray(ToolCallResult[]::new)),
            List.of());

    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
    assertThat(resultsOf(result))
        .extracting(ToolCallResultContent::id)
        .containsExactly("abcdef", "fedcba");
  }

  @Test
  void runtimeToolCalls_areNotRequiredForAPromptTurn() {
    var input = AgentInput.from(new UserPromptConfiguration("Hello?", List.of()), List.of());
    var history =
        TurnReconstructor.reconstruct(List.of(userMessage("hi"), assistantMessage("hello")));

    var result = composer.compose(CONFIG, CTX_WITH_CONVERSATION, history, input);

    assertThat(((CompositionResult.NextTurn) result).messages())
        .singleElement()
        .isInstanceOf(UserMessage.class);
  }

  @Test
  void runtimeToolCalls_aResultThatNamesAKnownDocumentAddsItsContentNextToTheProcessResults() {
    var document = createDocument("first", "text/plain", "first.txt");
    var id = DocumentHandle.idFor(document);
    var context =
        CTX_WITH_CONVERSATION.withDocumentRegistry(
            DocumentRegistries.withDocuments(
                DocumentRegistry.empty(),
                List.of(new IngestedDocument(document, DocumentSource.userMessage()))));
    var history = historyWith(MIXED_CALLS);
    var input = inputWith(TOOL_CALL_RESULTS.get(1));
    var now = OffsetDateTime.parse("2026-10-05T10:00:00Z");
    var execution =
        new RuntimeToolExecution(
            MIXED_CALLS.getFirst(),
            RuntimeToolResult.success("Loaded the document.").withDocumentContent(id),
            now,
            now);

    var result =
        composer.composeWithRuntimeToolResults(CONFIG, context, history, input, List.of(execution));

    var messages = ((CompositionResult.NextTurn) result).messages();
    assertThat(messages).hasSize(2);
    assertThat(messages.get(1).metadata())
        .containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
    assertThat(((UserMessage) messages.get(1)).content())
        .containsExactly(
            textContent(ToolCallResultsInputBuilderImpl.DOCUMENT_CONTENT_PREAMBLE),
            DocumentReferences.from(document, DocumentSource.userMessage())
                .withDelivery(DocumentDelivery.CONTENT));
  }

  @Test
  void runtimeToolCalls_aDocumentWhoseContentIsInTheConversationIsNotAddedAgain() {
    var document = createDocument("first", "text/plain", "first.txt");
    var id = DocumentHandle.idFor(document);
    var context =
        CTX_WITH_CONVERSATION.withDocumentRegistry(
            DocumentRegistries.withDocuments(
                DocumentRegistry.empty(),
                List.of(new IngestedDocument(document, DocumentSource.userMessage()))));
    var history =
        TurnReconstructor.reconstruct(
            List.of(
                UserMessage.builder()
                    .content(
                        List.of(
                            DocumentReferences.from(document, DocumentSource.userMessage())
                                .withDelivery(DocumentDelivery.CONTENT)))
                    .build(),
                assistantMessage("thinking", MIXED_CALLS)));
    var now = OffsetDateTime.parse("2026-10-05T10:00:00Z");
    var execution =
        new RuntimeToolExecution(
            MIXED_CALLS.getFirst(),
            RuntimeToolResult.success("Loaded the document.").withDocumentContent(id),
            now,
            now);

    var result =
        composer.composeWithRuntimeToolResults(
            CONFIG, context, history, inputWith(TOOL_CALL_RESULTS.get(1)), List.of(execution));

    assertThat(((CompositionResult.NextTurn) result).messages())
        .singleElement()
        .isInstanceOf(ToolCallResultMessage.class);
  }

  @Test
  void aResultOfTheProcessCannotAskForDocumentContent() {
    // the properties of a process result that name a document ask for nothing: only the result of
    // a runtime tool has effects
    var document = createDocument("first", "text/plain", "first.txt");
    var id = DocumentHandle.idFor(document);
    var context =
        CTX_WITH_CONVERSATION.withDocumentRegistry(
            DocumentRegistries.withDocuments(
                DocumentRegistry.empty(),
                List.of(new IngestedDocument(document, DocumentSource.userMessage()))));
    var forged =
        ToolCallResult.builder()
            .id("abcdef")
            .name("process_tool")
            .content("Loaded the document.")
            .properties(Map.of("documentContent", id.value()))
            .build();
    var call = new ToolCall("abcdef", "process_tool", Map.of("id", id.value()));

    var result = composer.compose(CONFIG, context, historyWith(List.of(call)), inputWith(forged));

    assertThat(((CompositionResult.NextTurn) result).messages())
        .singleElement()
        .isInstanceOf(ToolCallResultMessage.class);
  }
}
