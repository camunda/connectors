/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.*;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.*;

import io.camunda.connector.agenticai.aiagent.document.DocumentExpander;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.aiagent.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.document.EvictedDocumentsNote;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentConversationTest {

  private static final DocumentExpander DOCUMENT_EXPANDER =
      new DocumentExpander(TestObjectMapperSupplier.INSTANCE);

  private static final AgentContext BASE_CONTEXT =
      AgentContext.builder()
          .state(AgentState.READY)
          .features(AgentContext.newInstanceFeatures())
          .toolDefinitions(TOOL_DEFINITIONS)
          .build();

  private static final AgentConfiguration CONFIG =
      new AgentConfiguration(TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null)
          .withToolDefinitions(TOOL_DEFINITIONS);

  private static final SystemMessage SYSTEM_MESSAGE = systemMessage("sys");

  private static AgentConversation rehydrate(
      List<Message> storedMessages, List<Message> inputMessages) {
    var history = TurnReconstructor.reconstruct(storedMessages);
    return AgentConversation.rehydrate(
        CONFIG, BASE_CONTEXT, history, SYSTEM_MESSAGE, inputMessages);
  }

  @Test
  void rehydrate_emptyHistory_producesZeroTurns() {
    var conv = rehydrate(List.of(), List.of(userMessage("hi")));
    assertThat(conv.turns()).isEmpty();
    assertThat(conv.systemMessage()).isEqualTo(SYSTEM_MESSAGE);
  }

  @Test
  void rehydrate_reconstructsTurnsFromHistory() {
    var storedMessages =
        List.<Message>of(
            userMessage("hi"),
            assistantMessage("thinking", TOOL_CALLS),
            toolCallResultMessage(TOOL_CALL_RESULTS),
            assistantMessage("done"));
    var conv = rehydrate(storedMessages, List.of(userMessage("next")));
    assertThat(conv.turns()).hasSize(2);
    assertThat(conv.turns().get(0).iterationKey()).isEqualTo(1);
    assertThat(conv.turns().get(1).iterationKey()).isEqualTo(2);
  }

  @Test
  void rehydrate_usesStoredLastIterationKey_whenPresent() {
    // stored key (5) disagrees with the reconstructed count (1 turn) on purpose: the stored value
    // must win regardless
    var contextWithStoredKey =
        AgentContext.builder()
            .state(AgentState.READY)
            .metadata(new AgentMetadata(1L, 1L, null, 5))
            .build();
    var storedMessages = List.<Message>of(userMessage("hi"), assistantMessage("hello"));
    var history = TurnReconstructor.reconstruct(storedMessages);
    var conv =
        AgentConversation.rehydrate(
            CONFIG,
            contextWithStoredKey,
            history,
            systemMessage("sys"),
            List.of(userMessage("next")));

    assertThat(conv.currentTurn().iterationKey()).isEqualTo(6);
  }

  @Test
  void rehydrate_fallsBackToReconstructedCount_whenStoredKeyAbsent() {
    var contextWithMetadataNoKey =
        AgentContext.builder()
            .state(AgentState.READY)
            .metadata(new AgentMetadata(1L, 1L, null, null))
            .build();
    var storedMessages = List.<Message>of(userMessage("hi"), assistantMessage("hello"));
    var history = TurnReconstructor.reconstruct(storedMessages);
    var conv =
        AgentConversation.rehydrate(
            CONFIG,
            contextWithMetadataNoKey,
            history,
            systemMessage("sys"),
            List.of(userMessage("next")));

    assertThat(conv.currentTurn().iterationKey()).isEqualTo(2);
  }

  @Test
  void rehydrate_createsPendingTurn_withInputMessages() {
    var inputMessages = List.<Message>of(userMessage("hello"));
    var conv = rehydrate(List.of(), inputMessages);
    assertThat(conv.currentTurn().inputMessages()).containsExactlyElementsOf(inputMessages);
    assertThat(conv.currentTurn().assistantMessage()).isNull();
  }

  @Test
  void ingest_completesCurrentTurn() {
    var conv = rehydrate(List.of(), List.of(userMessage("hi")));
    var tokenUsage = new TokenUsage(10, 5);
    var response = assistantMessage("hello");
    var ingested = conv.ingest(response, new AgentMetrics(1, tokenUsage, 0));
    assertThat(ingested.turns()).hasSize(1);
    assertThat(ingested.turns().getFirst().iterationKey()).isEqualTo(1);
    assertThat(ingested.turns().getFirst().assistantMessage()).isEqualTo(response);
    assertThat(ingested.currentTurn().assistantMessage()).isEqualTo(response);
  }

  @Test
  void ingest_throwsWhenAlreadyIngested() {
    var conv =
        rehydrate(List.of(), List.of(userMessage("hi")))
            .ingest(assistantMessage("hello"), AgentMetrics.empty());
    assertThatThrownBy(() -> conv.ingest(assistantMessage("again"), AgentMetrics.empty()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void allMessages_includesSystemMessageAndTurnMessages() {
    var u = userMessage("hi");
    var a = assistantMessage("hello");
    var storedMessages = List.<Message>of(u, a);
    var conv = rehydrate(storedMessages, List.of(userMessage("next")));
    // SYSTEM_MESSAGE + stored turn messages + pending input
    assertThat(conv.allMessages()).contains(SYSTEM_MESSAGE, u, a);
  }

  @Test
  void allMessages_omitsSystemMessage_whenNull() {
    var history = TurnReconstructor.reconstruct(List.of());
    var input = userMessage("hi");
    var conv = AgentConversation.rehydrate(CONFIG, BASE_CONTEXT, history, null, List.of(input));
    assertThat(conv.systemMessage()).isNull();
    assertThat(conv.allMessages()).noneMatch(SystemMessage.class::isInstance);
    assertThat(conv.allMessages()).containsExactly(input);
  }

  @Test
  void window_returnsConversationSnapshot_withToolDefinitions() {
    var storedMessages = List.<Message>of(userMessage("hi"), assistantMessage("hello"));
    var conv = rehydrate(storedMessages, List.of(userMessage("next")));
    var snapshot = conv.window(20, DOCUMENT_EXPANDER.windowDecorator(conv));
    assertThat(snapshot.toolDefinitions()).isEqualTo(TOOL_DEFINITIONS);
    assertThat(snapshot.messages()).isNotEmpty();
  }

  @Test
  void window_offersDocumentParametersAsStringSchemasToTheProviderAndKeepsTheAuthoredDefinitions() {
    var authored =
        ToolDefinition.builder()
            .name("upload")
            .description("Uploads a file")
            .inputSchema(
                Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of(
                        "doc",
                        Map.of(
                            "type",
                            "string",
                            "format",
                            "camunda-document-id",
                            "description",
                            "The file"))))
            .build();
    var configuration = CONFIG.withToolDefinitions(List.of(authored));
    var conv =
        AgentConversation.rehydrate(
            configuration,
            BASE_CONTEXT.withToolDefinitions(List.of(authored)),
            TurnReconstructor.reconstruct(List.of()),
            SYSTEM_MESSAGE,
            List.of(userMessage("hi")));

    var snapshot = conv.window(20, DOCUMENT_EXPANDER.windowDecorator(conv));

    assertThat(snapshot.toolDefinitions()).hasSize(1);
    assertThat(snapshot.toolDefinitions().getFirst().inputSchema())
        .isEqualTo(
            Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                    "doc",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "The file. " + DocumentParameterSchema.HINT))));
    // nothing persisted or configured is rewritten
    assertThat(conv.configuration().toolDefinitions()).containsExactly(authored);
    assertThat(conv.toAgentContext().toolDefinitions()).containsExactly(authored);
  }

  @Test
  void window_offersUnionParametersAsPlainStringsToTheProviderAndKeepsTheAuthoredDefinitions() {
    var authored =
        ToolDefinition.builder()
            .name("send")
            .description("Sends a message")
            .inputSchema(
                Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of(
                        "body",
                        Map.of(
                            "anyOf",
                            List.of(
                                Map.of("type", "string"),
                                Map.of("type", "string", "format", "camunda-document-id")),
                            "description",
                            "The body"))))
            .build();
    var configuration = CONFIG.withToolDefinitions(List.of(authored));
    var conv =
        AgentConversation.rehydrate(
            configuration,
            BASE_CONTEXT.withToolDefinitions(List.of(authored)),
            TurnReconstructor.reconstruct(List.of()),
            SYSTEM_MESSAGE,
            List.of(userMessage("hi")));

    var snapshot = conv.window(20, DOCUMENT_EXPANDER.windowDecorator(conv));

    assertThat(snapshot.toolDefinitions().getFirst().inputSchema())
        .isEqualTo(
            Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                    "body",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "The body. " + DocumentParameterSchema.UNION_HINT))));
    // nothing persisted or configured is rewritten
    assertThat(conv.configuration().toolDefinitions()).containsExactly(authored);
    assertThat(conv.toAgentContext().toolDefinitions()).containsExactly(authored);
    assertThat(authored.inputSchema().toString()).contains("camunda-document-id", "anyOf");
  }

  @Test
  void withContextUpdate_appliesTheUpdateToThePersistedContextOnly() {
    var conv = rehydrate(List.of(), List.of(userMessage("hi")));

    var updated =
        conv.withContextUpdate(
                context ->
                    context.withResponseDocuments(
                        new LinkedHashMap<>(
                            Map.of("b", new ResponseDocumentSelection("doc-b", null)))))
            .withContextUpdate(context -> context.withProperty("note", "x"));

    assertThat(conv.responseDocuments()).isEmpty();
    assertThat(updated.responseDocuments()).containsOnlyKeys("b");
    assertThat(updated.toAgentContext().responseDocuments()).containsOnlyKeys("b");
    assertThat(updated.toAgentContext().properties()).containsEntry("note", "x");
    // the metrics of the persisted context are not touched by an update
    assertThat(updated.toAgentContext().metrics()).isEqualTo(conv.toAgentContext().metrics());
  }

  @Test
  void withContextUpdate_returnsTheSameInstanceForAnUnchangedContext() {
    var conv = rehydrate(List.of(), List.of(userMessage("hi")));

    assertThat(conv.withContextUpdate(context -> context)).isSameAs(conv);
  }

  @Test
  void window_listsRegistryDocumentsOfEvictedMessagesInATransientTrailingNote() {
    var document =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
            .create(
                DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                    .contentType("application/pdf")
                    .fileName("report.pdf")
                    .build());
    var documentMessage =
        UserMessage.builder()
            .content(List.of(textContent("see"), DocumentContent.documentContent(document)))
            .build();
    var entry =
        DocumentRegistryEntry.from(
            new IngestedDocument(document, DocumentSource.userMessage(null)));
    var context = BASE_CONTEXT.withDocuments(List.of(entry));
    var conv =
        AgentConversation.rehydrate(
            CONFIG,
            context,
            TurnReconstructor.reconstruct(
                List.of(
                    documentMessage,
                    assistantMessage("a1"),
                    userMessage("q2"),
                    assistantMessage("a2"))),
            SYSTEM_MESSAGE,
            List.of(userMessage("next")));

    var windowed = conv.window(2, DOCUMENT_EXPANDER.windowDecorator(conv)).messages();
    var allBefore = conv.allMessages();

    // the document message is outside of the window, so the note names the document
    assertThat(windowed).doesNotContain(documentMessage);
    assertThat(windowed.getLast())
        .isInstanceOfSatisfying(
            UserMessage.class,
            note ->
                assertThat(note.content())
                    .containsExactly(
                        textContent(EvictedDocumentsNote.PREAMBLE),
                        textContent(
                            "<doc id=\"%s\" fileName=\"report.pdf\" contentType=\"application/pdf\" source=\"user_message\" />"
                                .formatted(DocumentHandle.idFor(document)))));
    // the note is never part of the conversation itself
    assertThat(conv.allMessages()).isEqualTo(allBefore).contains(documentMessage);
    assertThat(allBefore).noneMatch(m -> m == windowed.getLast());

    // a window that keeps the document message needs no note
    assertThat(conv.window(20, DOCUMENT_EXPANDER.windowDecorator(conv)).messages())
        .hasSameSizeAs(conv.allMessages())
        .noneMatch(
            m ->
                m instanceof UserMessage user
                    && user.content().contains(textContent(EvictedDocumentsNote.PREAMBLE)));
  }

  @Test
  void window_expandsDocumentReferencesFromTheRegistryAndLeavesTheStoredMessagesAlone() {
    var document =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
            .create(
                DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                    .contentType("application/pdf")
                    .fileName("report.pdf")
                    .build());
    var entry =
        DocumentRegistryEntry.from(
            new IngestedDocument(document, DocumentSource.userMessage(null)));
    var reference = DocumentReferenceContent.from(entry, DocumentDelivery.CONTENT);
    var storedMessage =
        UserMessage.builder().content(List.of(textContent("see"), reference)).build();
    var conv =
        AgentConversation.rehydrate(
            CONFIG,
            BASE_CONTEXT.withDocuments(List.of(entry)),
            TurnReconstructor.reconstruct(List.of()),
            SYSTEM_MESSAGE,
            List.of(storedMessage));

    var windowed = conv.window(20, DOCUMENT_EXPANDER.windowDecorator(conv)).messages();

    assertThat(windowed).hasSize(2);
    assertThat(windowed.getLast())
        .isInstanceOfSatisfying(
            UserMessage.class,
            expanded -> {
              assertThat(expanded.id()).isEqualTo(storedMessage.id());
              assertThat(expanded.content()).hasSize(3);
              assertThat(expanded.content().subList(0, 2))
                  .containsExactly(textContent("see"), textContent(reference.toTag()));
              assertThat(expanded.content().get(2))
                  .isInstanceOfSatisfying(
                      DocumentContent.class,
                      documentContent ->
                          assertThat(
                                  TestObjectMapperSupplier.INSTANCE.convertValue(
                                      documentContent.document(), Map.class))
                              .isEqualTo(entry.reference()));
            });
    // the stored conversation keeps the document reference only
    assertThat(conv.allMessages().getLast()).isSameAs(storedMessage);
    assertThat(storedMessage.content()).containsExactly(textContent("see"), reference);
  }

  @Test
  void window_expandsAReferenceWithReferenceDeliveryToItsTagWithoutContent() {
    var document =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
            .create(
                DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                    .contentType("application/pdf")
                    .fileName("report.pdf")
                    .build());
    var entry =
        DocumentRegistryEntry.from(
            new IngestedDocument(document, DocumentSource.userMessage(null)));
    var reference = DocumentReferenceContent.from(entry);
    var storedMessage =
        UserMessage.builder().content(List.of(textContent("see"), reference)).build();
    var conv =
        AgentConversation.rehydrate(
            CONFIG,
            BASE_CONTEXT.withDocuments(List.of(entry)),
            TurnReconstructor.reconstruct(List.of()),
            SYSTEM_MESSAGE,
            List.of(storedMessage));

    var windowed = conv.window(20, DOCUMENT_EXPANDER.windowDecorator(conv)).messages();

    assertThat(windowed).hasSize(2);
    assertThat(windowed.getLast())
        .isInstanceOfSatisfying(
            UserMessage.class,
            expanded -> {
              assertThat(expanded.id()).isEqualTo(storedMessage.id());
              assertThat(expanded.content())
                  .containsExactly(textContent("see"), textContent(reference.toTag()));
            });
    assertThat(storedMessage.content()).containsExactly(textContent("see"), reference);
  }

  @Test
  void window_listsADocumentWhoseReferenceWithContentLeftTheWindowInsteadOfExpandingIt() {
    var document =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
            .create(
                DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                    .contentType("application/pdf")
                    .fileName("report.pdf")
                    .build());
    var withContent = UserMessage.builder().content(List.of(textContent("see"))).build();
    var entry =
        DocumentRegistryEntry.from(
            new IngestedDocument(
                document, DocumentSource.userMessage(withContent.id().toString())));
    var evictedMessage =
        withContent.withContent(
            List.of(
                textContent("see"),
                DocumentReferenceContent.from(entry, DocumentDelivery.CONTENT)));
    var conv =
        AgentConversation.rehydrate(
            CONFIG,
            BASE_CONTEXT.withDocuments(List.of(entry)),
            TurnReconstructor.reconstruct(
                List.of(
                    evictedMessage,
                    assistantMessage("one"),
                    userMessage("two"),
                    assistantMessage("three"))),
            SYSTEM_MESSAGE,
            List.of(userMessage("next")));

    var windowed = conv.window(3, DOCUMENT_EXPANDER.windowDecorator(conv)).messages();

    assertThat(windowed).noneMatch(message -> message.id().equals(evictedMessage.id()));
    assertThat(windowed)
        .filteredOn(UserMessage.class::isInstance)
        .map(message -> ((UserMessage) message).content())
        .allSatisfy(content -> assertThat(content).noneMatch(DocumentContent.class::isInstance));
    assertThat(windowed.getLast())
        .isInstanceOfSatisfying(
            UserMessage.class,
            note ->
                assertThat(note.content())
                    .containsExactly(
                        textContent(EvictedDocumentsNote.PREAMBLE),
                        textContent(DocumentReferenceContent.from(entry).toTag())));
  }

  @Test
  void documentRegistryOfTheRehydratedContextSurvivesEveryCopyOnWrite() {
    var entry =
        new DocumentRegistryEntry(
            "doc-1", DocumentSource.userMessage(null), Map.of("camunda.document.type", "camunda"));
    var context = BASE_CONTEXT.withDocuments(List.of(entry));
    var conv =
        AgentConversation.rehydrate(
            CONFIG,
            context,
            TurnReconstructor.reconstruct(List.of()),
            SYSTEM_MESSAGE,
            List.of(userMessage("hi")));

    var ingested = conv.ingest(assistantMessage("hello"), AgentMetrics.empty());
    var stored =
        ingested.withStoredConversation(InProcessConversationContext.builder("conv").build());

    assertThat(ingested.toAgentContext().documents()).containsExactly(entry);
    assertThat(stored.toAgentContext().documents()).containsExactly(entry);
    assertThat(ingested.nextContinuationRound().toAgentContext().documents())
        .containsExactly(entry);
  }

  @Test
  void toAgentContext_updatesCurrentTurnMetricsFromIngestedTurns() {
    var conv =
        rehydrate(List.of(), List.of(userMessage("hi")))
            .ingest(assistantMessage("hello"), new AgentMetrics(1, new TokenUsage(10, 5), 0));
    var ctx = conv.toAgentContext();
    assertThat(ctx.metrics().modelCalls()).isEqualTo(1);
  }

  @Test
  void toAgentContext_stampsLastIterationKeyOnMetadata() {
    var contextWithMetadata =
        AgentContext.builder()
            .state(AgentState.READY)
            .metadata(new AgentMetadata(1L, 1L, null, null))
            .build();
    var history = TurnReconstructor.reconstruct(List.of());
    var conv =
        AgentConversation.rehydrate(
                CONFIG,
                contextWithMetadata,
                history,
                systemMessage("sys"),
                List.of(userMessage("hi")))
            .ingest(assistantMessage("hello"), AgentMetrics.empty());

    var ctx = conv.toAgentContext();
    assertThat(ctx.metadata().lastIterationKey()).isEqualTo(1);
  }

  @Test
  void toAgentContext_doesNotStampLastIterationKey_whileTurnPending() {
    var contextWithStoredKey =
        AgentContext.builder()
            .state(AgentState.READY)
            .metadata(new AgentMetadata(1L, 1L, null, 4))
            .build();
    var history = TurnReconstructor.reconstruct(List.of());
    var conv =
        AgentConversation.rehydrate(
            CONFIG,
            contextWithStoredKey,
            history,
            systemMessage("sys"),
            List.of(userMessage("hi")));

    assertThat(conv.currentTurn().assistantMessage()).isNull();
    var ctx = conv.toAgentContext();
    assertThat(ctx.metadata().lastIterationKey()).isEqualTo(4);
  }

  @Test
  void toAgentContext_recordsConfigurationChange_evenAcrossContinuationRounds() {
    var priorTurn =
        new AgentConversationTurn(
            1, List.of(), assistantMessage("done"), AgentMetrics.empty(), "old-fp");
    var previousConversation = new PreviousConversation(Optional.empty(), List.of(priorTurn));
    var contextWithMetadata =
        AgentContext.builder()
            .state(AgentState.READY)
            .metadata(new AgentMetadata(1L, 1L, null, 1, Map.of(1, "old-fp")))
            .build();

    var conv =
        AgentConversation.rehydrate(
                CONFIG, contextWithMetadata, previousConversation, null, List.of(userMessage("hi")))
            .ingest(assistantMessage("round one"), AgentMetrics.empty())
            .nextContinuationRound()
            .ingest(assistantMessage("round two"), AgentMetrics.empty());

    var ctx = conv.toAgentContext();
    assertThat(ctx.metadata().configurationFingerprintHistory())
        .containsEntry(2, CONFIG.fingerprint());
  }

  @Test
  void toAgentContext_leavesMetadataNull_whenAbsent() {
    var conv =
        rehydrate(List.of(), List.of(userMessage("hi")))
            .ingest(assistantMessage("hello"), AgentMetrics.empty());

    var ctx = conv.toAgentContext();
    assertThat(ctx.metadata()).isNull();
  }

  @Test
  void totalMetrics_readsDurableContextMetrics_notReconstructedTurns() {
    // reconstructed history turns always carry AgentMetrics.empty(); the cumulative model-call
    // count lives on the durable AgentContext and must be what totalMetrics() reports so the
    // model-call limit is enforced across rehydrations.
    var contextWithHistory =
        AgentContext.builder()
            .state(AgentState.READY)
            .metrics(new AgentMetrics(9, new TokenUsage(100, 200), 4))
            .build();
    var storedMessages =
        List.<Message>of(
            userMessage("hi"),
            assistantMessage("first"),
            userMessage("again"),
            assistantMessage("second"));
    var history = TurnReconstructor.reconstruct(storedMessages);
    var conv =
        AgentConversation.rehydrate(
            CONFIG,
            contextWithHistory,
            history,
            systemMessage("sys"),
            List.of(userMessage("next")));

    assertThat(conv.turns()).hasSize(2);
    assertThat(conv.turns()).allSatisfy(t -> assertThat(t.metrics().modelCalls()).isZero());
    assertThat(conv.totalMetrics().modelCalls()).isEqualTo(9);
  }

  @Test
  void jobMetrics_noContinuation_equalsCurrentTurnMetrics() {
    var conv =
        rehydrate(List.of(), List.of(userMessage("hi")))
            .ingest(assistantMessage("hello"), new AgentMetrics(1, new TokenUsage(10, 5), 0));

    assertThat(conv.jobMetrics()).isEqualTo(conv.currentTurnMetrics());
  }

  @Test
  void jobMetrics_afterContinuationRound_sumsAllRoundsAndExcludesBaseContextMetrics() {
    // a non-zero base-context cumulative counter must not leak into jobMetrics(): it is the
    // per-job delta pushed additively to the agent instance, not the running total.
    var contextWithHistory =
        AgentContext.builder()
            .state(AgentState.READY)
            .metrics(new AgentMetrics(9, new TokenUsage(100, 200), 4))
            .build();
    var history = TurnReconstructor.reconstruct(List.of());
    var firstRoundMetrics = new AgentMetrics(1, new TokenUsage(10, 20), 0);
    var secondRoundMetrics = new AgentMetrics(1, new TokenUsage(5, 8), 0);

    var conv =
        AgentConversation.rehydrate(
                CONFIG,
                contextWithHistory,
                history,
                systemMessage("sys"),
                List.of(userMessage("hi")))
            .ingest(assistantMessage("partial"), firstRoundMetrics)
            .nextContinuationRound()
            .ingest(assistantMessage("done"), secondRoundMetrics);

    assertThat(conv.jobMetrics()).isEqualTo(firstRoundMetrics.add(secondRoundMetrics));
    assertThat(conv.totalMetrics()).isEqualTo(contextWithHistory.metrics().add(conv.jobMetrics()));
  }

  @Test
  void lastTurn_whileCurrentPending_isTurnPrecedingCurrent() {
    // while the current turn is still pending, lastTurn() resolves to the turn preceding it — the
    // one whose assistant message requested the tools answered by the current turn's results
    var conv =
        rehydrate(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)),
            List.of(toolCallResultMessage(TOOL_CALL_RESULTS)));

    assertThat(conv.lastTurn()).isPresent();
    assertThat(conv.lastTurn().get().toolCalls()).isEqualTo(TOOL_CALLS);
  }

  @Test
  void nextRuntimeToolRound_closesTheTurnAndOpensAPendingTurnCarryingTheToolResults() {
    var results = toolCallResultMessage(TOOL_CALL_RESULTS);
    var firstRound = new AgentMetrics(1, new TokenUsage(10, 20), 2);
    var conv =
        rehydrate(List.of(), List.of(userMessage("hi")))
            .ingest(assistantMessage("calling", TOOL_CALLS), firstRound)
            .nextRuntimeToolRound(results);

    assertThat(conv.currentTurn().iterationKey()).isEqualTo(2);
    assertThat(conv.currentTurn().inputMessages()).containsExactly(results);
    assertThat(conv.currentTurn().assistantMessage()).isNull();
    assertThat(conv.lastTurn()).get().extracting(AgentConversationTurn::iterationKey).isEqualTo(1);
    assertThat(conv.jobMetrics()).isEqualTo(firstRound);
  }

  @Test
  void nextRuntimeToolRound_carriesTheFollowUpMessagesAfterTheToolResults() {
    var results = toolCallResultMessage(TOOL_CALL_RESULTS);
    var followUp = userMessage("loaded");
    var conv =
        rehydrate(List.of(), List.of(userMessage("hi")))
            .ingest(assistantMessage("calling", TOOL_CALLS), AgentMetrics.empty())
            .nextRuntimeToolRound(results, List.of(followUp));

    assertThat(conv.currentTurn().inputMessages()).containsExactly(results, followUp);
  }

  @Test
  void windowedMessages_leavesOutAMessageThatWasEvicted() {
    var userMessage = userMessage("hi");

    for (var windowSize : List.of(3, 2)) {
      var conv =
          AgentConversation.rehydrate(
                  new AgentConfiguration(
                      TEST_CHAT_MODEL,
                      TEST_SYSTEM_PROMPT,
                      null,
                      new MemoryConfiguration(null, windowSize),
                      null,
                      null,
                      null),
                  BASE_CONTEXT,
                  TurnReconstructor.reconstruct(List.of()),
                  SYSTEM_MESSAGE,
                  List.of(userMessage))
              .ingest(assistantMessage("one"), AgentMetrics.empty());

      // the window holds the system message, the user message and the assistant message
      assertThat(conv.windowedMessages().contains(userMessage)).isEqualTo(windowSize == 3);
    }
  }

  @Test
  void nextRuntimeToolRound_throwsBeforeTheCurrentTurnIsIngested() {
    var conv = rehydrate(List.of(), List.of(userMessage("hi")));

    assertThatThrownBy(() -> conv.nextRuntimeToolRound(toolCallResultMessage(TOOL_CALL_RESULTS)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void nextRuntimeToolRound_throwsWhenTheResultsDoNotMatchTheToolCalls() {
    var conv =
        rehydrate(List.of(), List.of(userMessage("hi")))
            .ingest(assistantMessage("calling", TOOL_CALLS), AgentMetrics.empty());

    assertThatThrownBy(
            () ->
                conv.nextRuntimeToolRound(
                    toolCallResultMessage(List.of(TOOL_CALL_RESULTS.getFirst()))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nextRuntimeToolRound_keepsTheAssistantAndItsResultsPairableInTheWindow() {
    var results = toolCallResultMessage(TOOL_CALL_RESULTS);
    var calling = assistantMessage("calling", TOOL_CALLS);
    var conv =
        rehydrate(List.of(), List.of(userMessage("hi")))
            .ingest(calling, AgentMetrics.empty())
            .nextRuntimeToolRound(results);

    assertThat(conv.allMessages()).containsSubsequence(calling, results);
    // system message + user + assistant + results; evicting the assistant evicts its results
    assertThat(conv.window(3, DOCUMENT_EXPANDER.windowDecorator(conv)).messages())
        .containsExactly(SYSTEM_MESSAGE, calling, results);
    assertThat(conv.window(2, DOCUMENT_EXPANDER.windowDecorator(conv)).messages())
        .containsExactly(SYSTEM_MESSAGE);
  }

  @Test
  void hasInterruptedToolCallResultsInInvocation_coversTheRoundsBeforeARuntimeToolRound() {
    var interrupted =
        toolCallResultMessage(
            List.of(
                ToolCallResult.forCancelledToolCall(
                    "abcdef", "getWeather", java.time.OffsetDateTime.now()),
                ToolCallResult.forCancelledToolCall(
                    "fedcba", "getDateTime", java.time.OffsetDateTime.now())));
    var conv =
        rehydrate(
                List.of(userMessage("hi"), assistantMessage("calling", TOOL_CALLS)),
                List.of(interrupted))
            .ingest(assistantMessage("calling again", TOOL_CALLS), AgentMetrics.empty())
            .nextRuntimeToolRound(toolCallResultMessage(TOOL_CALL_RESULTS));

    assertThat(conv.currentTurn().hasInterruptedToolCallResults()).isFalse();
    assertThat(conv.hasInterruptedToolCallResultsInInvocation()).isTrue();
  }

  @Test
  void hasInterruptedToolCallResultsInInvocation_ignoresTurnsOfEarlierInvocations() {
    var interrupted =
        toolCallResultMessage(
            List.of(
                ToolCallResult.forCancelledToolCall(
                    "abcdef", "getWeather", java.time.OffsetDateTime.now()),
                ToolCallResult.forCancelledToolCall(
                    "fedcba", "getDateTime", java.time.OffsetDateTime.now())));
    var conv =
        rehydrate(
            List.of(
                userMessage("hi"),
                assistantMessage("calling", TOOL_CALLS),
                interrupted,
                assistantMessage("done")),
            List.of(userMessage("again")));

    assertThat(conv.hasInterruptedToolCallResultsInInvocation()).isFalse();
  }
}
