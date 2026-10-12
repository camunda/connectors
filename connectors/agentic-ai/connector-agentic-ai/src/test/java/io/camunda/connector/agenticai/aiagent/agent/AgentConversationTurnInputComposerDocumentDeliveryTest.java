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
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.document.GetDocumentTool;
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
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandler;
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

/** The document delivery: the option of the user prompt and the marker of a tool activity. */
class AgentConversationTurnInputComposerDocumentDeliveryTest {

  private static final AgentConfiguration CONFIG =
      new AgentConfiguration(null, null, null, null, null, null, null);
  private static final AgentContext CTX =
      AgentContext.builder()
          .state(AgentState.READY)
          .features(AgentContext.newInstanceFeatures())
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
  private AgentConversationTurnInputComposerImpl composer;

  @BeforeEach
  void setUp() {
    final var gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    when(gatewayToolHandlers.transformToolCallResults(any(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(gatewayToolHandlers.handlerForToolDefinition(any()))
        .thenReturn(java.util.Optional.empty());
    composer = new AgentConversationTurnInputComposerImpl(gatewayToolHandlers);
  }

  private Document document(String content, String contentType, String fileName) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(fileName)
            .build());
  }

  private static CompositionResult.NextTurn nextTurn(CompositionResult result) {
    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
    return (CompositionResult.NextTurn) result;
  }

  private static boolean isTagWithContent(Content content) {
    return content instanceof DocumentReferenceContent reference
        && reference.delivery() == DocumentDelivery.CONTENT;
  }

  private static DocumentReferenceContent tag(
      Document document, DocumentSource source, boolean loaded) {
    return DocumentReferenceContent.from(
        document, source, loaded ? DocumentDelivery.CONTENT : DocumentDelivery.REFERENCE);
  }

  // --- user prompt

  private CompositionResult composePrompt(
      AgentContext context,
      DocumentDelivery delivery,
      List<Message> previousMessages,
      Document... docs) {
    final var input =
        AgentInput.from(new UserPromptConfiguration("Hello?", List.of(docs), delivery), List.of());
    return composer.compose(
        CONFIG, context, TurnReconstructor.reconstruct(previousMessages), input);
  }

  @Test
  void userPrompt_withContentDelivery_addsTheContentOfADocumentThatPassesTheGuard() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), doc));

    final var message = turn.messages().getFirst();
    final var source = DocumentSource.userMessage(message.id().toString());
    assertThat(((UserMessage) message).content())
        .containsExactly(textContent("Hello?"), tag(doc, source, true));
    assertThat(turn.documents()).containsExactly(new IngestedDocument(doc, source));
  }

  @Test
  void userPrompt_withReferenceDelivery_keepsTheDocumentAsAReference() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.REFERENCE, List.of(), doc));

    final var message = turn.messages().getFirst();
    assertThat(((UserMessage) message).content())
        .containsExactly(
            textContent("Hello?"),
            tag(doc, DocumentSource.userMessage(message.id().toString()), false));
  }

  @Test
  void
      userPrompt_withContentDelivery_fallsBackToTheReferenceAndANoteForADocumentThatFailsTheGuard() {
    final var loadable = document("prompt data", "text/plain", "prompt.txt");
    final var archive = document("zip", "application/zip", "archive.zip");

    final var turn =
        nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), loadable, archive));

    final var message = turn.messages().getFirst();
    final var source = DocumentSource.userMessage(message.id().toString());
    assertThat(((UserMessage) message).content())
        .containsExactly(
            textContent("Hello?"),
            tag(loadable, source, true),
            tag(archive, source, false),
            textContent(
                "Document %s was not loaded automatically: it is of type application/zip which cannot be loaded into the conversation. Pass its id to a tool that processes it."
                    .formatted(DocumentHandle.idFor(archive))));
    // both documents are registered, loaded or not
    assertThat(turn.documents()).extracting(IngestedDocument::document).contains(loadable, archive);
  }

  @Test
  void userPrompt_withContentDelivery_addsTheContentOfADocumentOnlyOnce() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), doc, doc));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .filteredOn(AgentConversationTurnInputComposerDocumentDeliveryTest::isTagWithContent)
        .hasSize(1);
  }

  @Test
  void
      userPrompt_withContentDelivery_doesNotAddTheContentOfADocumentThatAlreadyHasContentInTheConversation() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");
    final var earlier =
        UserMessage.builder()
            .content(List.of(tag(doc, DocumentSource.userMessage(null), true)))
            .build();

    final var turn =
        nextTurn(
            composePrompt(
                CTX_WITH_CONVERSATION,
                DocumentDelivery.CONTENT,
                List.of(earlier, assistantMessage("done")),
                doc));

    final var message = turn.messages().getFirst();
    assertThat(((UserMessage) message).content())
        .containsExactly(
            textContent("Hello?"),
            tag(doc, DocumentSource.userMessage(message.id().toString()), false));
  }

  @Test
  void userPrompt_ofAnAgentInstanceWithoutTheDocumentsFeature_ignoresTheDelivery() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");
    final var legacy = AgentContext.builder().state(AgentState.READY).build();

    final var turn = nextTurn(composePrompt(legacy, DocumentDelivery.CONTENT, List.of(), doc));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), DocumentContent.documentContent(doc));
    assertThat(turn.documents()).isEmpty();
  }

  // --- tool results

  private CompositionResult composeToolResults(AgentContext context, ToolCallResult... results) {
    final var input =
        AgentInput.from(new UserPromptConfiguration("user input", List.of()), List.of(results));
    final var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));
    return composer.compose(CONFIG, context, history, input);
  }

  private static ToolCallResult result(String id, String name, Object content) {
    return ToolCallResult.builder().id(id).name(name).content(content).build();
  }

  private static ToolCallResult dateTimeResult() {
    return result("fedcba", "getDateTime", "15:00");
  }

  private AgentContext contextWithTools(ToolDefinition... tools) {
    return CTX_WITH_CONVERSATION.withToolDefinitions(List.of(tools));
  }

  @Test
  void toolResults_ofAContentDeliveryTool_arePutIntoAFollowUpMessageAsTagsWithContent() {
    final var doc = document("weather data", "text/plain", "weather.txt");
    final var nested = document("nested data", "application/json", "nested.json");

    final var turn =
        nextTurn(
            composeToolResults(
                contextWithTools(CONTENT_TOOL, REFERENCE_TOOL),
                result("abcdef", "getWeather", Map.of("file", doc, "more", List.of(nested))),
                result("fedcba", "getDateTime", "15:00")));

    assertThat(turn.messages()).hasSize(2);
    assertThat(turn.messages().getFirst()).isInstanceOf(ToolCallResultMessage.class);
    final var source =
        DocumentSource.toolCall("getWeather", "abcdef", turn.messages().getFirst().id().toString());
    assertThat(turn.messages().get(1))
        .isInstanceOfSatisfying(
            UserMessage.class,
            followUp -> {
              assertThat(followUp.metadata())
                  .containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
              assertThat(followUp.content())
                  .startsWith(
                      textContent(AgentConversationTurnInputComposerImpl.DOCUMENT_CONTENT_PREAMBLE))
                  .contains(tag(doc, source, true), tag(nested, source, true))
                  .hasSize(3);
            });
    assertThat(turn.documents()).extracting(IngestedDocument::document).contains(doc, nested);
  }

  @Test
  void toolResults_ofAGatewayToolWhoseDefinitionHasContentDelivery_arePutIntoAFollowUpMessage() {
    // a gateway handler sets the delivery on the definitions it creates; the extension property of
    // the element is not read for gateway elements
    final var doc = document("weather data", "text/plain", "weather.txt");
    final var handler = mock(GatewayToolHandler.class);
    when(handler.extractDocuments(any()))
        .thenAnswer(
            inv ->
                ContentTreeDocumentWalker.extractDocumentsFromContent(
                    ((ToolCallResult) inv.getArgument(0)).content()));
    final var gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    when(gatewayToolHandlers.transformToolCallResults(any(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(gatewayToolHandlers.handlerForToolDefinition("getWeather"))
        .thenReturn(java.util.Optional.of(handler));
    when(gatewayToolHandlers.handlerForToolDefinition("getDateTime"))
        .thenReturn(java.util.Optional.empty());
    final var gatewayComposer = new AgentConversationTurnInputComposerImpl(gatewayToolHandlers);

    final var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(result("abcdef", "getWeather", Map.of("file", doc)), dateTimeResult()));
    final var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));
    final var turn =
        nextTurn(
            gatewayComposer.compose(
                CONFIG, contextWithTools(CONTENT_TOOL, REFERENCE_TOOL), history, input));

    assertThat(turn.messages()).hasSize(2);
    final var source =
        DocumentSource.toolCall("getWeather", "abcdef", turn.messages().getFirst().id().toString());
    assertThat(((UserMessage) turn.messages().get(1)).content()).contains(tag(doc, source, true));
  }

  @Test
  void toolResults_ofAToolWithoutTheMarker_stayReferences() {
    final var doc = document("weather data", "text/plain", "weather.txt");

    final var turn =
        nextTurn(
            composeToolResults(
                contextWithTools(CONTENT_TOOL, REFERENCE_TOOL),
                result("abcdef", "getWeather", "Sunny"),
                result("fedcba", "getDateTime", Map.of("file", doc))));

    assertThat(turn.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
    assertThat(turn.documents()).extracting(IngestedDocument::document).containsExactly(doc);
  }

  @Test
  void toolResults_ofAContentDeliveryTool_fallBackToANoteForADocumentThatFailsTheGuard() {
    final var loadable = document("weather data", "text/plain", "weather.txt");
    final var archive = document("zip", "application/zip", "archive.zip");

    final var turn =
        nextTurn(
            composeToolResults(
                contextWithTools(CONTENT_TOOL),
                result("abcdef", "getWeather", Map.of("a", loadable, "b", archive)),
                dateTimeResult()));

    final var source =
        DocumentSource.toolCall("getWeather", "abcdef", turn.messages().getFirst().id().toString());
    assertThat(((UserMessage) turn.messages().get(1)).content())
        .containsExactly(
            textContent(AgentConversationTurnInputComposerImpl.DOCUMENT_CONTENT_PREAMBLE),
            tag(loadable, source, true),
            textContent(
                "Document %s was not loaded automatically: it is of type application/zip which cannot be loaded into the conversation. Pass its id to a tool that processes it."
                    .formatted(DocumentHandle.idFor(archive))));
  }

  @Test
  void toolResults_ofAContentDeliveryTool_withOnlyDocumentsThatFailTheGuard_addOnlyTheNote() {
    final var archive = document("zip", "application/zip", "archive.zip");

    final var turn =
        nextTurn(
            composeToolResults(
                contextWithTools(CONTENT_TOOL),
                result("abcdef", "getWeather", archive),
                dateTimeResult()));

    assertThat(((UserMessage) turn.messages().get(1)).content())
        .singleElement()
        .isInstanceOf(TextContent.class)
        .satisfies(
            note ->
                assertThat(((TextContent) note).text())
                    .startsWith("Document " + DocumentHandle.idFor(archive) + " was not loaded"));
  }

  @Test
  void toolResults_ofAContentDeliveryTool_doNotAddTheContentOfADocumentTheModelGotInTheSameTurn() {
    final var doc = document("weather data", "text/plain", "weather.txt");
    final var id = DocumentHandle.idFor(doc);
    final var registry =
        io.camunda.connector.agenticai.aiagent.document.DocumentRegistry.empty()
            .withDocuments(List.of(new IngestedDocument(doc, DocumentSource.userMessage(null))));
    final var context = contextWithTools(CONTENT_TOOL).withDocuments(registry.entries());

    final var turn =
        nextTurn(
            composeToolResults(
                context,
                result("abcdef", "getWeather", Map.of("file", doc)).withName("getWeather"),
                ToolCallResult.builder()
                    .id("fedcba")
                    .name(GetDocumentTool.NAME)
                    .content("Loaded document " + id + ".")
                    .properties(Map.of(ToolCallResult.PROPERTY_DOCUMENT_ID_WITH_CONTENT, id))
                    .build()));

    assertThat(((UserMessage) turn.messages().get(1)).content())
        .filteredOn(AgentConversationTurnInputComposerDocumentDeliveryTest::isTagWithContent)
        .hasSize(1);
  }

  @Test
  void
      toolResults_ofAContentDeliveryTool_doNotAddTheContentOfADocumentThatAlreadyHasContentInTheConversation() {
    final var doc = document("weather data", "text/plain", "weather.txt");
    final var earlier =
        UserMessage.builder()
            .content(List.of(tag(doc, DocumentSource.userMessage(null), true)))
            .build();
    final var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(
                result("abcdef", "getWeather", Map.of("file", doc)),
                result("fedcba", "getDateTime", "15:00")));
    final var history =
        TurnReconstructor.reconstruct(List.of(earlier, assistantMessage("thinking", TOOL_CALLS)));

    final var turn =
        nextTurn(composer.compose(CONFIG, contextWithTools(CONTENT_TOOL), history, input));

    assertThat(turn.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
  }

  @Test
  void toolResults_ofAnAgentInstanceWithoutTheDocumentsFeature_ignoreTheMarker() {
    final var doc = document("weather data", "text/plain", "weather.txt");
    final var legacy =
        AgentContext.builder()
            .state(AgentState.READY)
            .conversation(InProcessConversationContext.builder("conv").build())
            .toolDefinitions(List.of(CONTENT_TOOL))
            .build();

    final var turn =
        nextTurn(
            composeToolResults(
                legacy,
                result("abcdef", "getWeather", Map.of("file", doc)),
                result("fedcba", "getDateTime", "15:00")));

    assertThat(turn.messages()).hasSize(2);
    assertThat(((UserMessage) turn.messages().get(1)).content())
        .contains(DocumentContent.documentContent(doc))
        .noneMatch(AgentConversationTurnInputComposerDocumentDeliveryTest::isTagWithContent);
  }
}
