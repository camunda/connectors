/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALLS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.connector.agenticai.aiagent.TestMessagesFixture;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceHistoryMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConverseContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.document.DocumentExpander;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentInput;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistryImpl;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The model gets the same tool result bytes for a document whether the result is stored as the raw
 * document or as its reference: the composed messages are windowed through the registry and every
 * provider converter renders the result of the window like the result of the raw form.
 */
class ToolResultDocumentRenderingTest {

  private static final AgentContext CONTEXT =
      AgentContext.builder()
          .state(AgentState.READY)
          .features(AgentContext.newInstanceFeatures())
          .conversation(InProcessConversationContext.builder("conv").build())
          .build();
  private static final AgentConfiguration CONFIGURATION =
      new AgentConfiguration(
          TestMessagesFixture.TEST_CHAT_MODEL,
          TestMessagesFixture.TEST_SYSTEM_PROMPT,
          null,
          null,
          null,
          null,
          null);

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
  private final DocumentExpander expander = new DocumentExpander(TestObjectMapperSupplier.INSTANCE);

  private AgentConversationTurnInputComposerImpl composer;

  @BeforeEach
  void setUp() {
    final var gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    when(gatewayToolHandlers.transformToolCallResults(any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(gatewayToolHandlers.handlerForToolDefinition(any())).thenReturn(Optional.empty());
    composer = new AgentConversationTurnInputComposerImpl(gatewayToolHandlers);
  }

  private Document document(String content, String fileName, String contentType) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(fileName)
            .build());
  }

  @Test
  void everyProviderRendersTheWindowedToolResultsLikeTheRawDocuments() {
    final var bare = document("bare", "bare.pdf", "application/pdf");
    final var nested = document("nested", "nested.txt", "text/plain");
    final var results =
        List.of(
            ToolCallResult.builder().id("abcdef").name("getWeather").content(bare).build(),
            ToolCallResult.builder()
                .id("fedcba")
                .name("getDateTime")
                .content(Map.of("status", 200, "file", nested))
                .build());
    final var previous =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    final var nextTurn =
        (CompositionResult.NextTurn)
            composer.compose(
                CONFIGURATION,
                CONTEXT,
                previous,
                AgentInput.from(new UserPromptConfiguration("user input", List.of()), results));

    // the stored message holds the bare document as a reference and the nested one as it is
    final var stored = (ToolCallResultMessage) nextTurn.messages().getFirst();
    assertThat(stored.results().get(0).content())
        .containsExactly(DocumentReferenceContent.from(bare));
    assertThat(stored.results().get(1).content()).singleElement().isInstanceOf(ObjectContent.class);

    final var registry = DocumentRegistry.empty().withDocuments(nextTurn.documents());
    final var conversation =
        AgentConversation.rehydrate(
            CONFIGURATION,
            CONTEXT.withDocuments(registry.entries()),
            previous,
            null,
            nextTurn.messages());
    final var windowed =
        conversation.window(100, expander).messages().stream()
            .filter(ToolCallResultMessage.class::isInstance)
            .map(ToolCallResultMessage.class::cast)
            .findFirst()
            .orElseThrow();

    // the providers never see a reference: the windowed result holds the document again
    assertThat(windowed.results().get(0).content())
        .singleElement()
        .isInstanceOf(DocumentContent.class);

    final var raw = results.stream().map(ToolCallResultContent::from).toList();
    for (int i = 0; i < raw.size(); i++) {
      final var fileName = i == 0 ? "bare.pdf" : "nested.txt";
      final var rendered = renderedByEveryProvider(windowed.results().get(i).content());
      assertThat(rendered).isEqualTo(renderedByEveryProvider(raw.get(i).content()));
      // the document is in the request as a tag, not as its reference
      assertThat(rendered)
          .allSatisfy(text -> assertThat(text).contains(fileName).doesNotContain("storeId"));
    }
  }

  @Test
  void theHistoryOfTheLiveResultsAndOfTheComposedMessageIsTheSame() {
    final var bare = document("bare", "bare.pdf", "application/pdf");
    final var nested = document("nested", "nested.txt", "text/plain");
    final var completedAt = OffsetDateTime.parse("2026-07-02T09:59:55+02:00");
    final var results =
        List.of(
            ToolCallResult.builder()
                .id("abcdef")
                .name("getWeather")
                .content(bare)
                .completedAt(completedAt)
                .build(),
            ToolCallResult.builder()
                .id("fedcba")
                .name("getDateTime")
                .content(Map.of("status", 200, "file", nested))
                .completedAt(completedAt)
                .build());
    final var previous =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));
    final var nextTurn =
        (CompositionResult.NextTurn)
            composer.compose(
                CONFIGURATION,
                CONTEXT,
                previous,
                AgentInput.from(new UserPromptConfiguration("user input", List.of()), results));
    final var toolCallsById = previous.turns().getLast().toolCallsById();
    final var mapper =
        new AgentInstanceHistoryMapper(
            mock(GatewayToolHandlerRegistry.class),
            new RuntimeToolRegistryImpl(List.of()),
            expander);

    // the report of the results that arrived before the turn was composed knows no document of it
    final var live =
        mapper.inputHistoryItems(
            ToolCallResultMessage.builder()
                .results(results.stream().map(ToolCallResultContent::from).toList())
                .build(),
            toolCallsById,
            completedAt,
            AgentContext.empty().withFeatures(AgentContext.newInstanceFeatures()));
    final var composed =
        mapper.inputHistoryItems(
            nextTurn.messages().getFirst(),
            toolCallsById,
            completedAt,
            AgentContext.empty()
                .withFeatures(AgentContext.newInstanceFeatures())
                .withDocumentRegistry(
                    DocumentRegistry.empty().withDocuments(nextTurn.documents())));

    assertThat(live).hasSize(2);
    assertThat(composed.stream().map(this::describe).toList())
        .isEqualTo(live.stream().map(this::describe).toList());
    assertThat(live.get(0).content()).hasSize(2);
    assertThat(live.get(1).content()).hasSize(2);
  }

  @Test
  void theHistoryOfAnAnswerMadeOfBlocksIsTheSameWhenItIsWrittenEarlyAndWhenItIsComposed()
      throws Exception {
    final var loaded = document("loaded", "loaded.txt", "text/plain");
    final var registry =
        DocumentRegistry.empty()
            .withDocuments(
                List.of(new IngestedDocument(loaded, DocumentSource.userMessage("m-1"))));
    final var completedAt = OffsetDateTime.parse("2026-07-02T09:59:55+02:00");
    final var answer =
        ToolCallResultContent.builder()
            .id("abcdef")
            .name("c8__get_document")
            .content(
                List.of(
                    TextContent.textContent("Loaded the document."),
                    DocumentReferenceContent.from(
                        registry.entries().getFirst(), DocumentDelivery.REFERENCE),
                    TextContent.textContent("Its content follows in the next message.")))
            .completedAt(completedAt)
            .properties(
                Map.of(
                    ToolCallResult.PROPERTY_DOCUMENT_ID_WITH_CONTENT,
                    registry.entries().getFirst().id()))
            .build();
    final var processResult =
        ToolCallResult.builder()
            .id("fedcba")
            .name("getDateTime")
            .content("today")
            .completedAt(completedAt)
            .build();
    final var previous =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));
    // the pending answer reaches the next job as the agent context holds it in the process variable
    final var context =
        TestObjectMapperSupplier.INSTANCE.readValue(
            TestObjectMapperSupplier.INSTANCE.writeValueAsString(
                CONTEXT
                    .withDocuments(registry.entries())
                    .withPendingRuntimeToolCallResults(List.of(answer))),
            AgentContext.class);
    final var nextTurn =
        (CompositionResult.NextTurn)
            composer.compose(
                CONFIGURATION,
                context,
                previous,
                AgentInput.from(
                    new UserPromptConfiguration("user input", List.of()), List.of(processResult)));
    final var toolCallsById = previous.turns().getLast().toolCallsById();
    final var mapper =
        new AgentInstanceHistoryMapper(
            mock(GatewayToolHandlerRegistry.class),
            new RuntimeToolRegistryImpl(List.of()),
            expander);
    final var historyContext =
        AgentContext.empty()
            .withFeatures(AgentContext.newInstanceFeatures())
            .withDocumentRegistry(registry);

    final var early =
        mapper.inputHistoryItems(
            ToolCallResultMessage.builder().results(List.of(answer)).build(),
            toolCallsById,
            completedAt,
            historyContext);
    final var composed =
        mapper.inputHistoryItems(
            nextTurn.messages().getFirst(), toolCallsById, completedAt, historyContext);

    assertThat(early).hasSize(1);
    assertThat(early.getFirst().content()).hasSize(4);
    assertThat(composed.getFirst().historyItemId()).isEqualTo(early.getFirst().historyItemId());
    assertThat(describe(composed.getFirst())).isEqualTo(describe(early.getFirst()));
  }

  private String describe(AgentInstanceHistoryMapper.InputHistoryItem item) {
    return item.toolCalls().getFirst().getToolCallId()
        + item.content().stream()
            .map(
                content ->
                    switch (content) {
                      case AgentInstanceHistoryContent.TextContent text -> "TEXT " + text.getText();
                      case AgentInstanceHistoryContent.ObjectContent object ->
                          "OBJECT " + object.getObject();
                      case AgentInstanceHistoryContent.DocumentContent document -> {
                        final var reference = document.getDocumentReference();
                        yield "DOCUMENT %s %s %s"
                            .formatted(
                                reference.getDocumentId(),
                                reference.getContentHash(),
                                reference.getMetadata().getFileName());
                      }
                      default -> content.toString();
                    })
            .toList();
  }

  private static List<String> renderedByEveryProvider(
      List<io.camunda.connector.agenticai.aiagent.model.message.content.Content> content) {
    final var mapper = TestObjectMapperSupplier.INSTANCE;
    return List.of(
        new AnthropicContentConverter(mapper).toToolResultBlocks(content).toString(),
        new BedrockConverseContentConverter(mapper).toToolResultBlocks(content).toString(),
        new GeminiContentConverter(mapper).toFunctionResponseParts(content).toString(),
        new OpenAiContentConverter(mapper).toResponsesToolResultOutputItems(content).toString());
  }
}
