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
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.InlineDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
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
  private AgentConversationTurnInputComposerImpl composer;

  @BeforeEach
  void setUp() {
    final var gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
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
    return DocumentReferences.from(document, source)
        .withDelivery(loaded ? DocumentDelivery.CONTENT : DocumentDelivery.REFERENCE);
  }

  // --- user prompt

  private CompositionResult composePrompt(
      AgentContext context,
      @Nullable DocumentDelivery delivery,
      List<Message> previousMessages,
      Document... docs) {
    final var input =
        AgentInput.from(new UserPromptConfiguration("Hello?", List.of(docs), delivery), List.of());
    return composer.compose(
        CONFIG, context, TurnReconstructor.reconstruct(previousMessages), input);
  }

  @Test
  void userPrompt_withTheDeliveryContent_addsTheContentOfADocumentThatPassesTheGuard() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), doc));

    final var message = turn.messages().getFirst();
    final var source = DocumentSource.userMessage();
    assertThat(((UserMessage) message).content())
        .containsExactly(textContent("Hello?"), tag(doc, source, true));
    assertThat(turn.documents()).containsExactly(new IngestedDocument(doc, source));
  }

  @Test
  void userPrompt_withoutADelivery_addsTheContentOfAPdfOverTheLimitWithoutAGuard() {
    final var pdf = document("p".repeat(3_700_000), "application/pdf", "large.pdf");

    final var turn = nextTurn(composePrompt(CTX, null, List.of(), pdf));

    final var message = turn.messages().getFirst();
    assertThat(((UserMessage) message).content())
        .containsExactly(textContent("Hello?"), tag(pdf, DocumentSource.userMessage(), true));
  }

  @Test
  void userPrompt_withoutADelivery_addsTheContentOfADocumentOfAnUnsupportedType() {
    final var archive = document("zip", "application/zip", "archive.zip");

    final var turn = nextTurn(composePrompt(CTX, null, List.of(), archive));

    final var message = turn.messages().getFirst();
    assertThat(((UserMessage) message).content())
        .containsExactly(textContent("Hello?"), tag(archive, DocumentSource.userMessage(), true));
  }

  @Test
  void userPrompt_withoutADelivery_addsTheContentOfAnExternalDocument() {
    final var external =
        new ExternalDocument("https://files.example.com/report.pdf", "report.pdf", url -> null);

    final var turn = nextTurn(composePrompt(CTX, null, List.of(), external));

    final var message = turn.messages().getFirst();
    assertThat(((UserMessage) message).content())
        .containsExactly(textContent("Hello?"), tag(external, DocumentSource.userMessage(), true));
  }

  @Test
  void userPrompt_withReferenceDelivery_addsTheContentOfAnExternalDocument() {
    final var external =
        new ExternalDocument("https://files.example.com/report.pdf", "report.pdf", url -> null);

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.REFERENCE, List.of(), external));

    final var message = turn.messages().getFirst();
    assertThat(((UserMessage) message).content())
        .containsExactly(textContent("Hello?"), tag(external, DocumentSource.userMessage(), true));
  }

  @Test
  void userPrompt_withTheDeliveryContent_keepsTheGuardForAPdfOverTheLimit() {
    final var pdf = document("p".repeat(3_700_000), "application/pdf", "large.pdf");

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), pdf));

    final var message = turn.messages().getFirst();
    assertThat(((UserMessage) message).content())
        .hasSize(3)
        .contains(tag(pdf, DocumentSource.userMessage(), false));
  }

  @Test
  void userPrompt_withReferenceDelivery_keepsTheDocumentAsAReference() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.REFERENCE, List.of(), doc));

    final var message = turn.messages().getFirst();
    assertThat(((UserMessage) message).content())
        .containsExactly(textContent("Hello?"), tag(doc, DocumentSource.userMessage(), false));
  }

  @Test
  void
      userPrompt_withTheDeliveryContent_fallsBackToTheReferenceAndANoteForADocumentThatFailsTheGuard() {
    final var loadable = document("prompt data", "text/plain", "prompt.txt");
    final var archive = document("zip", "application/zip", "archive.zip");

    final var turn =
        nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), loadable, archive));

    final var message = turn.messages().getFirst();
    final var source = DocumentSource.userMessage();
    assertThat(((UserMessage) message).content())
        .containsExactly(
            textContent("Hello?"),
            tag(loadable, source, true),
            tag(archive, source, false),
            textContent(
                "Document %s was not loaded automatically: it is of type application/zip which cannot be loaded into the conversation. Pass its tag to a tool that processes it."
                    .formatted(DocumentHandle.idFor(archive))));
    // both documents are registered, loaded or not
    assertThat(turn.documents()).extracting(IngestedDocument::document).contains(loadable, archive);
  }

  @Test
  void userPrompt_withTheDeliveryContent_theNoteForAnUnreadableDocumentHoldsNoDetailOfTheFailure() {
    final var unreadable =
        new InlineDocument("data", "report.txt", "text/plain") {
          @Override
          public InputStream asInputStream() {
            throw new IllegalStateException(
                "GET https://bucket.example/report?token=secret failed");
          }
        };

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), unreadable));

    final var message = turn.messages().getFirst();
    final var source = DocumentSource.userMessage();
    assertThat(((UserMessage) message).content())
        .containsExactly(
            textContent("Hello?"),
            tag(unreadable, source, false),
            textContent(
                "Document %s was not loaded automatically: it could not be read to check its size. A tool for loading documents can try to load it again."
                    .formatted(DocumentHandle.idFor(unreadable))));
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

    final var message = turn.messages().getFirst();
    final var source = DocumentSource.userMessage();
    assertThat(((UserMessage) message).content())
        .containsExactly(textContent("Hello?"), tag(external, source, true));
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
    final var earlier =
        UserMessage.builder()
            .content(List.of(tag(doc, DocumentSource.userMessage(), true)))
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
        .containsExactly(textContent("Hello?"), tag(doc, DocumentSource.userMessage(), false));
  }

  @Test
  void
      userPrompt_withoutADelivery_addsTheContentOfADocumentThatAlreadyHasContentInTheConversation() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");
    final var earlier =
        UserMessage.builder()
            .content(List.of(tag(doc, DocumentSource.userMessage(), true)))
            .build();

    final var turn =
        nextTurn(
            composePrompt(
                CTX_WITH_CONVERSATION, null, List.of(earlier, assistantMessage("done")), doc, doc));

    final var message = turn.messages().getFirst();
    final var source = DocumentSource.userMessage();
    assertThat(((UserMessage) message).content())
        .containsExactly(textContent("Hello?"), tag(doc, source, true), tag(doc, source, true));
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
    return composer.compose(CONFIG, context, processToolCallsHistory(), input);
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

  private AgentContext contextWithTools(ToolDefinition... tools) {
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
    final var source = DocumentSource.toolCall("getWeather", "abcdef");
    assertThat(turn.messages().get(1))
        .isInstanceOfSatisfying(
            UserMessage.class,
            followUp -> {
              assertThat(followUp.metadata())
                  .containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
              assertThat(followUp.content())
                  .startsWith(
                      textContent(ToolCallResultsInputBuilderImpl.DOCUMENT_CONTENT_PREAMBLE))
                  .contains(tag(doc, source, true), tag(nested, source, true))
                  .hasSize(3);
            });
    assertThat(turn.documents()).extracting(IngestedDocument::document).contains(doc, nested);
  }

  @Test
  void toolResults_ofAGatewayToolWhoseDefinitionHasTheDeliveryContent_arePutIntoAFollowUpMessage() {
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
        .thenReturn(Optional.of(handler));
    when(gatewayToolHandlers.handlerForToolDefinition("getDateTime")).thenReturn(Optional.empty());
    final var gatewayComposer =
        new AgentConversationTurnInputComposerImpl(
            gatewayToolHandlers,
            new ToolCallResultsInputBuilderImpl(
                new DocumentContentGuard(DocumentContentLimits.defaults()),
                TestObjectMapperSupplier.INSTANCE),
            new DocumentContentGuard(DocumentContentLimits.defaults()));

    final var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(result("abcdef", "getWeather", Map.of("file", doc)), dateTimeResult()));
    final var turn =
        nextTurn(
            gatewayComposer.compose(
                CONFIG,
                contextWithTools(CONTENT_TOOL, REFERENCE_TOOL),
                processToolCallsHistory(),
                input));

    assertThat(turn.messages()).hasSize(2);
    final var source = DocumentSource.toolCall("getWeather", "abcdef");
    assertThat(((UserMessage) turn.messages().get(1)).content()).contains(tag(doc, source, true));
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
    assertThat(turn.documents()).extracting(IngestedDocument::document).containsExactly(doc);
  }

  @Test
  void toolResults_ofAToolWithTheDeliveryContent_fallBackToANoteForADocumentThatFailsTheGuard() {
    final var loadable = document("weather data", "text/plain", "weather.txt");
    final var archive = document("zip", "application/zip", "archive.zip");

    final var turn =
        nextTurn(
            composeToolResults(
                contextWithTools(CONTENT_TOOL),
                result("abcdef", "getWeather", Map.of("a", loadable, "b", archive)),
                dateTimeResult()));

    final var source = DocumentSource.toolCall("getWeather", "abcdef");
    assertThat(((UserMessage) turn.messages().get(1)).content())
        .containsExactly(
            textContent(ToolCallResultsInputBuilderImpl.DOCUMENT_CONTENT_PREAMBLE),
            tag(loadable, source, true),
            textContent(
                "Document %s was not loaded automatically: it is of type application/zip which cannot be loaded into the conversation. Pass its tag to a tool that processes it."
                    .formatted(DocumentHandle.idFor(archive))));
  }

  @Test
  void toolResults_ofAToolWithTheDeliveryContent_keepAnExternalDocumentAsAReferenceWithANote() {
    final var external =
        new ExternalDocument("https://files.example.com/report.pdf", "report.pdf", url -> null);

    final var turn =
        nextTurn(
            composeToolResults(
                contextWithTools(CONTENT_TOOL),
                result("abcdef", "getWeather", external),
                dateTimeResult()));

    assertThat(((UserMessage) turn.messages().get(1)).content())
        .containsExactly(
            textContent(
                "Document %s was not loaded automatically: it is an external document, which cannot be loaded into the conversation. Pass its tag to a tool that processes it."
                    .formatted(DocumentHandle.idFor(external))));
    // the document is known to the agent and the model reads its tag in the tool result
    assertThat(turn.documents()).extracting(IngestedDocument::document).containsExactly(external);
  }

  @Test
  void
      toolResults_ofAToolWithTheDeliveryContent_doNotAddADocumentWhoseContentIsInTheConversation() {
    final var doc = document("weather data", "text/plain", "weather.txt");
    final var earlier =
        UserMessage.builder()
            .content(List.of(tag(doc, DocumentSource.userMessage(), true)))
            .build();
    final var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(result("abcdef", "getWeather", Map.of("file", doc)), dateTimeResult()));
    final var history =
        TurnReconstructor.reconstruct(List.of(earlier, assistantMessage("thinking", TOOL_CALLS)));

    final var turn =
        nextTurn(composer.compose(CONFIG, contextWithTools(CONTENT_TOOL), history, input));

    assertThat(turn.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
  }

  @Test
  void
      toolResults_ofAToolWithTheDeliveryContent_shareTheContentWithTheRuntimeToolResultsOfTheTurn() {
    // a mixed batch: the runtime tool asks for the content of the document that the process tool
    // returns again; the document is added once
    final var doc = document("weather data", "text/plain", "weather.txt");
    final var id = DocumentHandle.idFor(doc);
    final var context =
        contextWithTools(CONTENT_TOOL)
            .withDocumentRegistry(
                DocumentRegistries.withDocuments(
                    DocumentRegistry.empty(),
                    List.of(new IngestedDocument(doc, DocumentSource.userMessage()))));
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

    final var turn =
        nextTurn(
            composer.composeWithRuntimeToolResults(
                CONFIG, context, history, input, List.of(execution)));

    assertThat(((UserMessage) turn.messages().get(1)).content())
        .containsExactly(
            textContent(ToolCallResultsInputBuilderImpl.DOCUMENT_CONTENT_PREAMBLE),
            tag(doc, DocumentSource.userMessage(), true));
  }

  @Test
  void toolResults_ofARuntimeTool_ignoreTheDeliveryOfAToolDefinitionWithItsName() {
    final var doc = document("lookup data", "text/plain", "lookup.txt");
    final var runtimeDefinition =
        ToolDefinition.builder()
            .name("acme__lookup")
            .inputSchema(Map.of("type", "object"))
            .documentDelivery(DocumentDelivery.CONTENT)
            .build();
    final var history =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", MIXED_KIND_TOOL_CALLS)));
    final var input =
        AgentInput.from(
            new UserPromptConfiguration("user input", List.of()),
            List.of(result(PROCESS_CALL_ID, "getWeather", "Sunny")));
    final var now = OffsetDateTime.parse("2026-10-05T10:00:00Z");
    final var execution =
        new RuntimeToolExecution(
            MIXED_KIND_TOOL_CALLS.getFirst(),
            RuntimeToolResult.success(List.of(DocumentContent.documentContent(doc))),
            now,
            now);

    final var turn =
        nextTurn(
            composer.composeWithRuntimeToolResults(
                CONFIG, contextWithTools(runtimeDefinition), history, input, List.of(execution)));

    assertThat(turn.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
  }

  @Test
  void toolResults_ofAnAgentInstanceWithoutTheDocumentsFeature_ignoreTheDelivery() {
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
                legacy, result("abcdef", "getWeather", Map.of("file", doc)), dateTimeResult()));

    assertThat(turn.messages()).hasSize(2);
    assertThat(((UserMessage) turn.messages().get(1)).content())
        .contains(DocumentContent.documentContent(doc))
        .noneMatch(AgentConversationTurnInputComposerDocumentDeliveryTest::isTagWithContent);
  }
}
