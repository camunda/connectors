/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.MIXED_KIND_TOOL_CALLS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.PROCESS_CALL_ID;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALLS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.api.document.ContentTreeDocumentWalker;
import io.camunda.connector.agenticai.aiagent.api.tool.GatewayToolHandler;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
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
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolExecution;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The document delivery: the option of the user prompt and the delivery of a tool activity. */
class AgentConversationTurnInputComposerDocumentDeliveryTest {

  private static final AgentConfiguration CONFIG =
      new AgentConfiguration(null, null, null, null, null, null, null);
  private static final AgentContext CTX =
      AgentContext.builder()
          .state(AgentState.READY)
          .features(List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS))
          .build();
  private static final AgentContext CTX_WITH_CONVERSATION =
      CTX.withConversation(InProcessConversationContext.builder("conv").build());
  private static final ToolDefinition CONTENT_TOOL =
      ToolDefinition.builder()
          .name("getWeather")
          .inputSchema(Map.of("type", "object"))
          .documentDelivery(DocumentDelivery.CONTENT)
          .build();
  private static final ToolDefinition REFERENCE_TOOL =
      ToolDefinition.builder().name("getDateTime").inputSchema(Map.of("type", "object")).build();

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
  private GatewayToolHandlerRegistry gatewayToolHandlers;
  private AgentConversationTurnInputComposerImpl composer;

  @BeforeEach
  void setUp() {
    gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    when(gatewayToolHandlers.transformToolCallResults(any(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(gatewayToolHandlers.handlerForToolDefinition(any())).thenReturn(Optional.empty());
    composer = composerWith(gatewayToolHandlers);
  }

  private static AgentConversationTurnInputComposerImpl composerWith(
      GatewayToolHandlerRegistry gatewayToolHandlers) {
    return new AgentConversationTurnInputComposerImpl(
        gatewayToolHandlers,
        new ToolCallResultsInputBuilder(TestDocuments.documentContentDelivery()),
        TestDocuments.documentContentDelivery());
  }

  private Document document(String content, String contentType, String fileName) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(fileName)
            .build());
  }

  private static ExternalDocument externalDocument() {
    return new ExternalDocument("https://files.example.com/report.pdf", "report.pdf", url -> null);
  }

  private static TurnInput nextTurn(
      AgentConversationTurnInputComposerImpl composer, CompositionResult result) {
    assertThat(result).isInstanceOf(CompositionResult.Ready.class);
    return composer.assemble((CompositionResult.Ready) result);
  }

  private TurnInput nextTurn(CompositionResult result) {
    return nextTurn(composer, result);
  }

  private static boolean isTagWithContent(Content content) {
    return content instanceof AgentDocumentContent tag
        && tag.delivery() == DocumentDelivery.CONTENT;
  }

  private static AgentDocumentContent tag(Document document, boolean loaded) {
    return AgentDocumentContent.of(AgentDocumentId.of(document))
        .withDelivery(loaded ? DocumentDelivery.CONTENT : DocumentDelivery.REFERENCE);
  }

  // --- user prompt

  private CompositionResult composePrompt(
      AgentContext context,
      DocumentDelivery delivery,
      List<Message> previousMessages,
      Document... docs) {
    final var input =
        AgentInput.from(new UserPromptConfiguration("Hello?", List.of(docs), delivery), List.of());
    return composer.resolve(
        CONFIG, context, TurnReconstructor.reconstruct(previousMessages), input);
  }

  @Test
  void userPrompt_withTheDeliveryContent_addsTheContentOfADocumentThatPassesTheGuard() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), doc));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), tag(doc, true));
    assertThat(turn.documents())
        .containsExactly(new NewDocument(doc, DocumentOrigin.userMessage()));
  }

  @Test
  void userPrompt_withoutADelivery_addsTheContentOfAPdfOverTheLimitWithoutAGuard() {
    final var pdf = document("p".repeat(3_700_000), "application/pdf", "large.pdf");

    final var turn = nextTurn(composePrompt(CTX, null, List.of(), pdf));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), tag(pdf, true));
  }

  @Test
  void userPrompt_withoutADelivery_addsTheContentOfADocumentOfAnUnsupportedType() {
    final var archive = document("zip", "application/zip", "archive.zip");

    final var turn = nextTurn(composePrompt(CTX, null, List.of(), archive));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), tag(archive, true));
  }

  @Test
  void userPrompt_withoutADelivery_addsTheContentOfAnExternalDocument() {
    final var external = externalDocument();

    final var turn = nextTurn(composePrompt(CTX, null, List.of(), external));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), tag(external, true));
  }

  @Test
  void userPrompt_withReferenceDelivery_addsTheContentOfAnExternalDocument() {
    final var external = externalDocument();

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.REFERENCE, List.of(), external));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), tag(external, true));
  }

  @Test
  void userPrompt_withTheDeliveryContent_keepsTheGuardForAPdfOverTheLimit() {
    final var pdf = document("p".repeat(3_700_000), "application/pdf", "large.pdf");

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), pdf));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .hasSize(3)
        .contains(tag(pdf, false));
  }

  @Test
  void userPrompt_withReferenceDelivery_keepsTheDocumentAsAReference() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.REFERENCE, List.of(), doc));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), tag(doc, false));
  }

  @Test
  void
      userPrompt_withTheDeliveryContent_fallsBackToTheReferenceAndANoteForADocumentThatFailsTheGuard() {
    final var loadable = document("prompt data", "text/plain", "prompt.txt");
    final var archive = document("zip", "application/zip", "archive.zip");

    final var turn =
        nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), loadable, archive));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(
            textContent("Hello?"),
            tag(loadable, true),
            tag(archive, false),
            textContent(
                "The document %s is of type application/zip which cannot be loaded into the conversation; pass its tag to a tool that processes it."
                    .formatted(AgentDocumentId.of(archive).value())));
    // both documents are registered, loaded or not
    assertThat(turn.documents())
        .extracting(NewDocument::document)
        .containsExactly(loadable, archive);
  }

  @Test
  void userPrompt_withTheDeliveryContent_addsTheContentOfAnExternalDocumentWithoutGuardingIt() {
    final var downloads = new AtomicInteger();
    final var external =
        new ExternalDocument(
            "https://files.example.com/report.pdf",
            "report.pdf",
            url -> {
              downloads.incrementAndGet();
              return null;
            });

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), external));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), tag(external, true));
    assertThat(downloads).hasValue(0);
  }

  @Test
  void userPrompt_withTheDeliveryContent_addsTheContentOfADocumentOnlyOnce() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), doc, doc));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .filteredOn(AgentConversationTurnInputComposerDocumentDeliveryTest::isTagWithContent)
        .hasSize(1);
  }

  @Test
  void
      userPrompt_withTheDeliveryContent_doesNotAddTheContentOfADocumentThatAlreadyHasContentInTheConversation() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");
    final var earlier = UserMessage.builder().content(List.of(tag(doc, true))).build();

    final var turn =
        nextTurn(
            composePrompt(
                CTX_WITH_CONVERSATION,
                DocumentDelivery.CONTENT,
                List.of(earlier, assistantMessage("done")),
                doc));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), tag(doc, false));
  }

  @Test
  void
      userPrompt_withoutADelivery_addsTheContentOfADocumentThatAlreadyHasContentInTheConversation() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");
    final var earlier = UserMessage.builder().content(List.of(tag(doc, true))).build();

    final var turn =
        nextTurn(
            composePrompt(
                CTX_WITH_CONVERSATION, null, List.of(earlier, assistantMessage("done")), doc, doc));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), tag(doc, true), tag(doc, true));
  }

  @Test
  void userPrompt_ofAnAgentInstanceWithoutTheDocumentsFeature_ignoresTheDelivery() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");
    final var untracked = AgentContext.builder().state(AgentState.READY).build();

    final var turn = nextTurn(composePrompt(untracked, DocumentDelivery.CONTENT, List.of(), doc));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), DocumentContent.documentContent(doc));
    assertThat(turn.documents()).isEmpty();
  }

  // --- tool results

  private CompositionResult composeToolResults(AgentContext context, ToolCallResult... results) {
    return composeToolResults(composer, context, results);
  }

  private static CompositionResult composeToolResults(
      AgentConversationTurnInputComposerImpl composer,
      AgentContext context,
      ToolCallResult... results) {
    final var input =
        AgentInput.from(new UserPromptConfiguration("user input", List.of()), List.of(results));
    return composer.resolve(CONFIG, context, processToolCallsHistory(), input);
  }

  private static PreviousConversation processToolCallsHistory() {
    return TurnReconstructor.reconstruct(
        List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));
  }

  private static ToolCallResult result(String id, String name, Object content) {
    return ToolCallResult.builder().id(id).name(name).content(content).build();
  }

  private static ToolCallResult dateTimeResult() {
    return result("fedcba", "getDateTime", "15:00");
  }

  private static AgentContext contextWithTools(ToolDefinition... tools) {
    return CTX_WITH_CONVERSATION.withToolDefinitions(List.of(tools));
  }

  @Test
  void toolResults_ofAToolWithTheDeliveryContent_arePutIntoAFollowUpMessageAsTagsWithContent() {
    final var doc = document("weather data", "text/plain", "weather.txt");
    final var nested = document("nested data", "application/json", "nested.json");

    final var turn =
        nextTurn(
            composeToolResults(
                contextWithTools(CONTENT_TOOL, REFERENCE_TOOL),
                result("abcdef", "getWeather", Map.of("file", doc, "more", List.of(nested))),
                dateTimeResult()));

    assertThat(turn.messages()).hasSize(2);
    assertThat(turn.messages().getFirst()).isInstanceOf(ToolCallResultMessage.class);
    assertThat(turn.messages().get(1))
        .isInstanceOfSatisfying(
            UserMessage.class,
            followUp -> {
              assertThat(followUp.metadata())
                  .containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
              assertThat(followUp.content())
                  .startsWith(textContent(ToolCallResultsInputBuilder.DOCUMENT_CONTENT_PREAMBLE))
                  .contains(tag(doc, true), tag(nested, true))
                  .hasSize(3);
            });
    assertThat(turn.documents()).extracting(NewDocument::document).contains(doc, nested);
  }

  @Test
  void toolResults_ofAGatewayToolWhoseDefinitionHasTheDeliveryContent_arePutIntoAFollowUpMessage() {
    // a gateway handler sets the delivery on the definitions it creates; the extension property of
    // the element is not read for gateway elements
    final var doc = document("weather data", "text/plain", "weather.txt");
    final var handler = mock(GatewayToolHandler.class);
    when(handler.extractDocuments(any()))
        .thenAnswer(inv ->
            ContentTreeDocumentWalker.extractDocumentsFromContent(
                ((ToolCallResult) inv.getArgument(0)).content()));
    final var registry = mock(GatewayToolHandlerRegistry.class);
    when(registry.transformToolCallResults(any(), any())).thenAnswer(inv -> inv.getArgument(1));
    when(registry.handlerForToolDefinition("getWeather")).thenReturn(Optional.of(handler));
    when(registry.handlerForToolDefinition("getDateTime")).thenReturn(Optional.empty());

    final var gatewayComposer = composerWith(registry);
    final var turn =
        nextTurn(
            gatewayComposer,
            composeToolResults(
                gatewayComposer,
                contextWithTools(CONTENT_TOOL, REFERENCE_TOOL),
                result("abcdef", "getWeather", Map.of("file", doc)),
                dateTimeResult()));

    assertThat(turn.messages()).hasSize(2);
    assertThat(((UserMessage) turn.messages().get(1)).content()).contains(tag(doc, true));
  }

  @Test
  void toolResults_ofAToolWithoutTheDeliveryContent_stayReferences() {
    final var doc = document("weather data", "text/plain", "weather.txt");

    final var turn =
        nextTurn(
            composeToolResults(
                contextWithTools(CONTENT_TOOL, REFERENCE_TOOL),
                result("abcdef", "getWeather", "Sunny"),
                result("fedcba", "getDateTime", Map.of("file", doc))));

    assertThat(turn.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
    assertThat(turn.documents()).extracting(NewDocument::document).containsExactly(doc);
  }

  @Test
  void toolResults_ofAToolWithTheDeliveryContent_fallBackToANoteForADocumentThatFailsTheGuard() {
    final var loadable = document("weather data", "text/plain", "weather.txt");
    final var archive = document("zip", "application/zip", "archive.zip");

    final var turn =
        nextTurn(
            composeToolResults(
                contextWithTools(CONTENT_TOOL),
                result("abcdef", "getWeather", List.of(loadable, archive)),
                dateTimeResult()));

    assertThat(((UserMessage) turn.messages().get(1)).content())
        .containsExactly(
            textContent(ToolCallResultsInputBuilder.DOCUMENT_CONTENT_PREAMBLE),
            tag(loadable, true),
            textContent(
                "The document %s is of type application/zip which cannot be loaded into the conversation; pass its tag to a tool that processes it."
                    .formatted(AgentDocumentId.of(archive).value())));
  }

  @Test
  void toolResults_ofAToolWithTheDeliveryContent_keepAnExternalDocumentAsAReferenceWithANote() {
    final var external = externalDocument();

    final var turn =
        nextTurn(
            composeToolResults(
                contextWithTools(CONTENT_TOOL),
                result("abcdef", "getWeather", external),
                dateTimeResult()));

    assertThat(((UserMessage) turn.messages().get(1)).content())
        .containsExactly(
            textContent(ToolCallResultsInputBuilder.DOCUMENT_CONTENT_PREAMBLE),
            textContent(
                "The document %s is an external document, which cannot be loaded into the conversation; pass its tag to a tool that processes it."
                    .formatted(AgentDocumentId.of(external).value())));
    // the document is known to the agent and the model reads its tag in the tool result
    assertThat(turn.documents()).extracting(NewDocument::document).containsExactly(external);
  }

  @Test
  void
      toolResults_ofAToolWithTheDeliveryContent_doNotAddADocumentWhoseContentIsInTheConversation() {
    final var doc = document("weather data", "text/plain", "weather.txt");
    final var earlier = UserMessage.builder().content(List.of(tag(doc, true))).build();
    final var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(result("abcdef", "getWeather", Map.of("file", doc)), dateTimeResult()));
    final var history =
        TurnReconstructor.reconstruct(List.of(earlier, assistantMessage("thinking", TOOL_CALLS)));

    final var turn =
        nextTurn(composer.resolve(CONFIG, contextWithTools(CONTENT_TOOL), history, input));

    assertThat(turn.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
  }

  @Test
  void
      toolResults_ofAToolWithTheDeliveryContent_shareTheContentWithTheRuntimeToolResultsOfTheTurn() {
    // a mixed batch: the runtime tool asks for the content of the document that the process tool
    // returns again; the document is added once
    final var doc = document("weather data", "text/plain", "weather.txt");
    final var id = AgentDocumentId.of(doc);
    final var context =
        contextWithTools(CONTENT_TOOL)
            .withDocuments(AgentDocuments.empty().register(doc, DocumentOrigin.userMessage()));
    final var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", MIXED_KIND_TOOL_CALLS)));
    final var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(result(PROCESS_CALL_ID, "getWeather", Map.of("file", doc))));
    final var now = OffsetDateTime.parse("2026-10-05T10:00:00Z");
    final var execution =
        new RuntimeToolExecution(
            MIXED_KIND_TOOL_CALLS.getFirst(),
            RuntimeToolResult.success("Loaded the document.").withDocumentContent(id),
            now,
            now);
    final var ready = (CompositionResult.Ready) composer.resolve(CONFIG, context, history, input);

    final var turn =
        composer.assemble(
            ready.withResults(
                List.of(
                    ToolCallBatch.Entry.from(
                        execution.toolCallResult(),
                        ((RuntimeToolResult.Success) execution.result()).effects()))));

    assertThat(((UserMessage) turn.messages().get(1)).content())
        .containsExactly(
            textContent(ToolCallResultsInputBuilder.DOCUMENT_CONTENT_PREAMBLE), tag(doc, true));
  }

  @Test
  void toolResults_ofAnAgentInstanceWithoutTheDocumentsFeature_ignoreTheDelivery() {
    final var doc = document("weather data", "text/plain", "weather.txt");
    final var untracked =
        AgentContext.builder()
            .state(AgentState.READY)
            .conversation(InProcessConversationContext.builder("conv").build())
            .toolDefinitions(List.of(CONTENT_TOOL))
            .build();

    final var turn =
        nextTurn(
            composeToolResults(
                untracked, result("abcdef", "getWeather", Map.of("file", doc)), dateTimeResult()));

    assertThat(turn.messages()).hasSize(2);
    assertThat(((UserMessage) turn.messages().get(1)).content())
        .contains(DocumentContent.documentContent(doc))
        .noneMatch(AgentConversationTurnInputComposerDocumentDeliveryTest::isTagWithContent);
  }
}
