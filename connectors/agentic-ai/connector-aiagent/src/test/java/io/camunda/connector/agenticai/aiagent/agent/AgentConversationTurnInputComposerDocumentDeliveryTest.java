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
import static io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent.objectContent;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.document.UnsupportedDocumentReferenceException;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.request.MemoryTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolExecution;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        TestDocuments.documentContentDelivery(),
        TestDocuments.objectHandleRenderer());
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

  private static boolean isBlockWithContent(Content content) {
    return content instanceof AgentDocumentContent block
        && block.delivery() == DocumentDelivery.CONTENT;
  }

  private static AgentDocumentContent block(Document document, boolean loaded) {
    return AgentDocumentContent.of(AgentDocumentId.of(document))
        .withDelivery(loaded ? DocumentDelivery.CONTENT : DocumentDelivery.REFERENCE);
  }

  /** The file name and the id of a document as the sentences about its content name it. */
  private static String refOf(Document document) {
    final var fileName =
        AgentDocument.Descriptors.of((CamundaDocumentReference) document.reference()).fileName();
    final var id = AgentDocumentId.of(document).value();
    return fileName == null ? id : "%s (%s)".formatted(fileName, id);
  }

  /** What the tool result says about a document whose content is added. */
  private static Content followsSentence(Document document) {
    return textContent("The content of " + refOf(document) + " follows in the next message.");
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
  void userPrompt_withTheDeliveryContent_addsTheContentOfADocument() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), doc));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), block(doc, true));
    assertThat(turn.documents())
        .containsExactly(
            AgentDocument.of(
                doc,
                AgentDocumentOrigin.userMessage(((UserMessage) turn.messages().getFirst()).id())));
  }

  @Test
  void userPrompt_withoutADelivery_addsTheContentOfALargePdf() {
    final var pdf = document("p".repeat(3_700_000), "application/pdf", "large.pdf");

    final var turn = nextTurn(composePrompt(CTX, null, List.of(), pdf));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), block(pdf, true));
  }

  @Test
  void userPrompt_withoutADelivery_addsTheContentOfADocumentOfAnUnsupportedType() {
    final var archive = document("zip", "application/zip", "archive.zip");

    final var turn = nextTurn(composePrompt(CTX, null, List.of(), archive));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), block(archive, true));
  }

  @Test
  void userPrompt_withReferenceDelivery_keepsTheDocumentAsAReference() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.REFERENCE, List.of(), doc));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), block(doc, false));
  }

  @Test
  void userPrompt_withTheDeliveryContent_addsTheContentOfADocumentOnlyOnce() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), doc, doc));

    // the second occurrence of the document is its block without content
    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), block(doc, true), block(doc, false));
  }

  @Test
  void
      userPrompt_withTheDeliveryContent_doesNotAddTheContentOfADocumentThatAlreadyHasContentInTheConversation() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");
    final var earlier = UserMessage.builder().content(List.of(block(doc, true))).build();

    final var turn =
        nextTurn(
            composePrompt(
                CTX_WITH_CONVERSATION,
                DocumentDelivery.CONTENT,
                List.of(earlier, assistantMessage("done")),
                doc));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), block(doc, false));
  }

  private static AgentConfiguration configWithContextWindow(int contextWindowSize) {
    return new AgentConfiguration(
        null,
        null,
        null,
        new MemoryTemplateConfiguration(null, contextWindowSize),
        null,
        null,
        null);
  }

  private CompositionResult composePromptWithin(
      AgentConfiguration configuration,
      List<Message> previousMessages,
      List<ToolCallResult> eventResults,
      Document doc) {
    final var input =
        AgentInput.from(
            new UserPromptConfiguration("Hello?", List.of(doc), DocumentDelivery.CONTENT),
            eventResults);
    return composer.resolve(
        configuration,
        CTX_WITH_CONVERSATION,
        TurnReconstructor.reconstruct(previousMessages),
        input);
  }

  @Test
  void
      userPrompt_withTheDeliveryContent_doesNotAddTheContentOfADocumentInTheOldestMessageOfTheWindow() {
    // 2 previous messages, the user prompt message and the place of the system message fill the
    // window of 4
    final var doc = document("prompt data", "text/plain", "prompt.txt");
    final var earlier = UserMessage.builder().content(List.of(block(doc, true))).build();

    final var turn =
        nextTurn(
            composePromptWithin(
                configWithContextWindow(4),
                List.of(earlier, assistantMessage("done")),
                List.of(),
                doc));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), block(doc, false));
  }

  @Test
  void
      userPrompt_withTheDeliveryContent_addsTheContentOfADocumentThatTheUserPromptMessageSqueezesOutOfTheWindow() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");
    final var earlier = UserMessage.builder().content(List.of(block(doc, true))).build();

    final var turn =
        nextTurn(
            composePromptWithin(
                configWithContextWindow(3),
                List.of(earlier, assistantMessage("done")),
                List.of(),
                doc));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), block(doc, true));
  }

  @Test
  void
      userPrompt_withTheDeliveryContent_addsTheContentOfADocumentThatAnEventMessageSqueezesOutOfTheWindow() {
    // 2 previous messages, the user prompt message, the event message and the place of the system
    // message exceed the window of 4
    final var doc = document("prompt data", "text/plain", "prompt.txt");
    final var earlier = UserMessage.builder().content(List.of(block(doc, true))).build();

    final var turn =
        nextTurn(
            composePromptWithin(
                configWithContextWindow(4),
                List.of(earlier, assistantMessage("done")),
                List.of(ToolCallResult.builder().content("event").build()),
                doc));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), block(doc, true));
  }

  @Test
  void
      userPrompt_withoutADelivery_addsTheContentOfADocumentThatAlreadyHasContentInTheConversation() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");
    final var earlier = UserMessage.builder().content(List.of(block(doc, true))).build();

    final var turn =
        nextTurn(
            composePrompt(
                CTX_WITH_CONVERSATION, null, List.of(earlier, assistantMessage("done")), doc, doc));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), block(doc, true), block(doc, true));
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
  void
      toolResults_ofAToolWithTheDeliveryContent_arePutIntoAFollowUpMessageAsDocumentsWithContent() {
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
                  .contains(block(doc, true), block(nested, true))
                  .hasSize(3);
            });
    assertThat(turn.documents())
        .extracting(AgentDocument::id)
        .contains(AgentDocumentId.of(doc), AgentDocumentId.of(nested));
    // the result of the tool says that the content of both documents follows, the other result
    // is as it was
    final var results = ((ToolCallResultMessage) turn.messages().getFirst()).results();
    assertThat(results.get(0).content()).hasSize(3);
    assertThat(results.get(0).content().getFirst())
        .isEqualTo(objectContent(Map.of("file", doc, "more", List.of(nested))));
    assertThat(results.get(0).content().subList(1, 3))
        .containsExactlyInAnyOrder(followsSentence(doc), followsSentence(nested));
    assertThat(results.get(1).content()).containsExactly(textContent("15:00"));
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
    assertThat(((UserMessage) turn.messages().get(1)).content())
        .containsExactly(
            textContent(ToolCallResultsInputBuilder.DOCUMENT_CONTENT_PREAMBLE), block(doc, true));
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
    assertThat(turn.documents())
        .extracting(AgentDocument::id)
        .containsExactly(AgentDocumentId.of(doc));
  }

  @Test
  void toolResults_withoutAName_takeTheDeliveryOfTheToolThatTheModelCalled() {
    final var doc = document("weather data", "text/plain", "weather.txt");

    final var turn =
        nextTurn(
            composeToolResults(
                contextWithTools(CONTENT_TOOL, REFERENCE_TOOL),
                ToolCallResult.builder().id("abcdef").content(Map.of("file", doc)).build(),
                dateTimeResult()));

    assertThat(turn.messages()).hasSize(2);
    assertThat(((UserMessage) turn.messages().get(1)).content())
        .containsExactly(
            textContent(ToolCallResultsInputBuilder.DOCUMENT_CONTENT_PREAMBLE), block(doc, true));
  }

  @Test
  void toolResults_ofAToolWithTheDeliveryContent_failTheJobForAnExternalDocument() {
    final var external = externalDocument();

    assertThatThrownBy(() ->
            composeToolResults(
                contextWithTools(CONTENT_TOOL),
                result("abcdef", "getWeather", external),
                dateTimeResult()))
        .isExactlyInstanceOf(UnsupportedDocumentReferenceException.class)
        .hasMessage(
            "Only Camunda documents can be passed to the AI Agent, but the result of the tool 'getWeather' contains an external document.");
  }

  @Test
  void
      toolResults_ofAToolWithTheDeliveryContent_doNotAddADocumentWhoseContentIsInTheConversation() {
    final var doc = document("weather data", "text/plain", "weather.txt");
    final var earlier = UserMessage.builder().content(List.of(block(doc, true))).build();
    final var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(result("abcdef", "getWeather", Map.of("file", doc)), dateTimeResult()));
    final var history =
        TurnReconstructor.reconstruct(List.of(earlier, assistantMessage("thinking", TOOL_CALLS)));

    final var turn =
        nextTurn(composer.resolve(CONFIG, contextWithTools(CONTENT_TOOL), history, input));

    assertThat(turn.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
    assertThat(((ToolCallResultMessage) turn.messages().getFirst()).results().getFirst().content())
        .endsWith(textContent("The content of " + refOf(doc) + " is already in the conversation."));
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
            .withDocuments(
                AgentDocuments.empty()
                    .register(doc, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID)));
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
            textContent(ToolCallResultsInputBuilder.DOCUMENT_CONTENT_PREAMBLE), block(doc, true));
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
        .noneMatch(AgentConversationTurnInputComposerDocumentDeliveryTest::isBlockWithContent);
  }
}
