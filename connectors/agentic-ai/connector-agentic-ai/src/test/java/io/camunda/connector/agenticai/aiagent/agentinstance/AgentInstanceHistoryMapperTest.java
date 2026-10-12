/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.connector.agenticai.aiagent.localtool.LocalToolRegistryImpl;
import io.camunda.connector.agenticai.aiagent.localtool.ReturnDocumentTool;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentExpander;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AgentInstanceHistoryMapperTest {

  private static final HistoryDocuments NO_DOCUMENTS =
      new HistoryDocuments(List.of(), DocumentRegistry.empty());

  private static final OffsetDateTime TURN_INGESTION_TIMESTAMP =
      OffsetDateTime.parse("2026-07-02T10:00:00Z");
  private static final OffsetDateTime TOOL_RESULT_COMPLETED_AT =
      OffsetDateTime.parse("2026-07-02T09:59:55Z");

  private final AgentInstanceHistoryMapper mapper =
      new AgentInstanceHistoryMapper(
          Mockito.mock(GatewayToolHandlerRegistry.class),
          new LocalToolRegistryImpl(List.of(new ReturnDocumentTool())),
          new DocumentExpander(TestObjectMapperSupplier.INSTANCE));

  @Test
  void userMessageUsesThePassedInTurnIngestionTimestamp() {
    final var userMessage =
        UserMessage.builder().content(MessageUtil.singleTextContent("hi")).build();

    final var items =
        mapper.inputHistoryItems(userMessage, Map.of(), TURN_INGESTION_TIMESTAMP, NO_DOCUMENTS);

    assertThat(items).singleElement().extracting("producedAt").isEqualTo(TURN_INGESTION_TIMESTAMP);
  }

  private static List<String> texts(List<AgentInstanceHistoryContent> content) {
    return content.stream()
        .filter(AgentInstanceHistoryContent.TextContent.class::isInstance)
        .map(c -> ((AgentInstanceHistoryContent.TextContent) c).getText())
        .toList();
  }

  @Test
  void aUserMessageWithADocumentTagIsReportedWithTheTagAndTheDocumentFromTheRegistry() {
    final var document =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
            .create(
                DocumentCreationRequest.from("content".getBytes(StandardCharsets.UTF_8))
                    .contentType("text/plain")
                    .fileName("report.txt")
                    .build());
    final var registry =
        DocumentRegistry.empty()
            .withDocuments(
                List.of(new IngestedDocument(document, DocumentSource.userMessage(null))));
    final var tag = DocumentReferenceXmlTag.from(registry.entries().getFirst()).toContent();
    final var userMessage =
        UserMessage.builder().content(List.of(TextContent.textContent("see"), tag)).build();

    final var items =
        mapper.inputHistoryItems(
            userMessage,
            Map.of(),
            TURN_INGESTION_TIMESTAMP,
            new HistoryDocuments(List.of(), registry));

    assertThat(items)
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.content()).hasSize(3);
              assertThat(texts(item.content())).containsExactly("see", "`" + tag.text() + "`");
              assertThat(item.content().get(2))
                  .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);
            });
  }

  @Test
  void theMessageThatLoadsADocumentIsReportedAsAUserItemWithTheTagAndTheDocumentReference() {
    final var document =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
            .create(
                DocumentCreationRequest.from("content".getBytes(StandardCharsets.UTF_8))
                    .contentType("text/plain")
                    .fileName("report.txt")
                    .build());
    final var registry =
        DocumentRegistry.empty()
            .withDocuments(
                List.of(
                    new IngestedDocument(
                        document, DocumentSource.toolCall("fetch", "call-1", null))));
    final var tag = DocumentReferenceXmlTag.from(registry.entries().getFirst()).toLoadedContent();
    final var loadedMessage =
        UserMessage.builder()
            .content(List.of(TextContent.textContent("Documents loaded by c8__get_document:"), tag))
            .metadata(Map.of(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true))
            .build();

    final var items =
        mapper.inputHistoryItems(
            loadedMessage,
            Map.of(),
            TURN_INGESTION_TIMESTAMP,
            new HistoryDocuments(List.of(), registry));

    assertThat(items)
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.role()).isEqualTo(AgentInstanceHistoryRole.USER);
              assertThat(texts(item.content()))
                  .containsExactly("Documents loaded by c8__get_document:", "`" + tag.text() + "`");
              assertThat(item.content()).hasSize(3);
              assertThat(item.content().get(2))
                  .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);
            });
  }

  @Test
  void aDocumentTagOfAnUnknownDocumentIsReportedAsTextOnly() {
    final var tag =
        new DocumentReferenceXmlTag("unknown", null, null, null, null, null).toContent();
    final var userMessage = UserMessage.builder().content(List.of(tag)).build();

    final var items =
        mapper.inputHistoryItems(userMessage, Map.of(), TURN_INGESTION_TIMESTAMP, NO_DOCUMENTS);

    assertThat(items)
        .singleElement()
        .satisfies(
            item -> assertThat(texts(item.content())).containsExactly("`" + tag.text() + "`"));
  }

  @Test
  void everyDocTagOfATextIsWrappedInInlineCodeBackticks() {
    final var userMessage =
        UserMessage.builder()
            .content(
                List.of(
                    TextContent.textContent(
                        "Documents (<doc /> tag + content pair): <doc id=\"a\" /> and <doc id=\"b\""
                            + " fileName=\"b.txt\" />.")))
            .build();

    final var items =
        mapper.inputHistoryItems(userMessage, Map.of(), TURN_INGESTION_TIMESTAMP, NO_DOCUMENTS);

    assertThat(items)
        .singleElement()
        .satisfies(
            item ->
                assertThat(texts(item.content()))
                    .containsExactly(
                        "Documents (`<doc />` tag + content pair): `<doc id=\"a\" />` and"
                            + " `<doc id=\"b\" fileName=\"b.txt\" />`."));
  }

  @Test
  void aDocTagThatIsAlreadyInsideBackticksIsLeftAsItIs() {
    final var userMessage =
        UserMessage.builder()
            .content(
                List.of(
                    TextContent.textContent(
                        "quoted `<doc id=\"a\" />` and plain <doc id=\"b\" />")))
            .build();

    final var items =
        mapper.inputHistoryItems(userMessage, Map.of(), TURN_INGESTION_TIMESTAMP, NO_DOCUMENTS);

    assertThat(items)
        .singleElement()
        .satisfies(
            item ->
                assertThat(texts(item.content()))
                    .containsExactly("quoted `<doc id=\"a\" />` and plain `<doc id=\"b\" />`"));
  }

  @Test
  void textWithoutADocTagIsReportedAsIs() {
    final var userMessage =
        UserMessage.builder()
            .content(List.of(TextContent.textContent("a <document> and <b>bold</b> text")))
            .build();

    final var items =
        mapper.inputHistoryItems(userMessage, Map.of(), TURN_INGESTION_TIMESTAMP, NO_DOCUMENTS);

    assertThat(items)
        .singleElement()
        .satisfies(
            item ->
                assertThat(texts(item.content()))
                    .containsExactly("a <document> and <b>bold</b> text"));
  }

  @Test
  void aToolResultTextQuotingADocTagIsWrappedInInlineCodeBackticks() {
    final var toolCall = ToolCall.builder().id("call-1").name("getWeather").build();
    final var result =
        ToolCallResultContent.builder()
            .id("call-1")
            .name("getWeather")
            .elementId("getWeather")
            .content(
                List.of(TextContent.textContent("Unknown id. Known documents:\n<doc id=\"a\" />")))
            .completedAt(TURN_INGESTION_TIMESTAMP)
            .build();

    final var items =
        mapper.inputHistoryItems(
            ToolCallResultMessage.builder().results(List.of(result)).build(),
            Map.of("call-1", toolCall),
            TURN_INGESTION_TIMESTAMP,
            NO_DOCUMENTS);

    assertThat(items)
        .singleElement()
        .satisfies(
            item ->
                assertThat(texts(item.content()))
                    .containsExactly("Unknown id. Known documents:\n`<doc id=\"a\" />`"));
  }

  @Test
  void anAssistantTextWithADocTagIsWrappedInInlineCodeBackticks() {
    final var content =
        mapper.assistantContent(
            AssistantMessage.builder()
                .content(List.of(TextContent.textContent("I use <doc id=\"a\" />")))
                .build(),
            NO_DOCUMENTS);

    assertThat(texts(content)).containsExactly("I use `<doc id=\"a\" />`");
  }

  @Test
  void withoutTheDocumentsFeatureTextsAndLocalToolNamesAreReportedAsStored() {
    final var withoutDocumentFlow = HistoryDocuments.withoutDocumentFlow(List.of(), false);
    final var tag = "<doc toolName=\"t\" toolCallId=\"c\" documentId=\"d1\" />";
    final var toolCall =
        ToolCall.builder().id("call-1").name(ReturnDocumentTool.NAME).arguments(Map.of()).build();

    final var assistant =
        AssistantMessage.builder()
            .content(List.of(TextContent.textContent("I use " + tag)))
            .toolCalls(List.of(toolCall))
            .build();
    final var user =
        UserMessage.builder()
            .content(List.of(TextContent.textContent(tag), TextContent.textContent("see " + tag)))
            .build();

    assertThat(texts(mapper.assistantContent(assistant, withoutDocumentFlow)))
        .containsExactly("I use " + tag);
    assertThat(mapper.assistantToolCalls(assistant, withoutDocumentFlow))
        .singleElement()
        .satisfies(call -> assertThat(call.getElementId()).isEqualTo(ReturnDocumentTool.NAME));
    final var userItems =
        mapper.inputHistoryItems(user, Map.of(), TURN_INGESTION_TIMESTAMP, withoutDocumentFlow);
    assertThat(texts(userItems.getFirst().content())).containsExactly(tag, "see " + tag);
  }

  @Test
  void toolResultUsesItsOwnCompletedAtNotTheTurnIngestionTimestamp() {
    final var toolCall = ToolCall.builder().id("call-1").name("getWeather").build();
    final var result =
        ToolCallResultContent.builder()
            .id("call-1")
            .name("getWeather")
            .elementId("getWeather")
            .content(List.of(TextContent.textContent("sunny")))
            .completedAt(TOOL_RESULT_COMPLETED_AT)
            .build();
    final var message = ToolCallResultMessage.builder().results(List.of(result)).build();

    final var items =
        mapper.inputHistoryItems(
            message, Map.of("call-1", toolCall), TURN_INGESTION_TIMESTAMP, NO_DOCUMENTS);

    assertThat(items)
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.role()).isEqualTo(AgentInstanceHistoryRole.TOOL_RESULT);
              assertThat(item.producedAt()).isEqualTo(TOOL_RESULT_COMPLETED_AT);
            });
  }

  @Test
  void aCallAndAResultOfALocalToolHaveNoElementId() {
    final var toolCall =
        ToolCall.builder()
            .id("call-1")
            .name("c8__return_document")
            .arguments(Map.of("key", "report", "document", "a"))
            .build();
    final var result =
        ToolCallResultContent.builder()
            .id("call-1")
            .name("c8__return_document")
            .content(
                List.of(TextContent.textContent("Returned the document under the key 'report'.")))
            .completedAt(TOOL_RESULT_COMPLETED_AT)
            .build();

    final var assistantCalls =
        mapper.assistantToolCalls(
            AssistantMessage.builder().toolCalls(List.of(toolCall)).build(), NO_DOCUMENTS);
    final var items =
        mapper.inputHistoryItems(
            ToolCallResultMessage.builder().results(List.of(result)).build(),
            Map.of("call-1", toolCall),
            TURN_INGESTION_TIMESTAMP,
            NO_DOCUMENTS);

    assertThat(assistantCalls)
        .singleElement()
        .satisfies(
            call -> {
              assertThat(call.getToolName()).isEqualTo("c8__return_document");
              assertThat(call.getElementId()).isNull();
            });
    assertThat(items)
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.role()).isEqualTo(AgentInstanceHistoryRole.TOOL_RESULT);
              assertThat(item.content())
                  .singleElement()
                  .isInstanceOfSatisfying(
                      AgentInstanceHistoryContent.TextContent.class,
                      text ->
                          assertThat(text.getText())
                              .isEqualTo("Returned the document under the key 'report'."));
              assertThat(item.toolCalls())
                  .singleElement()
                  .satisfies(
                      call -> {
                        assertThat(call.getToolName()).isEqualTo("c8__return_document");
                        assertThat(call.getToolCallId()).isEqualTo("call-1");
                        assertThat(call.getElementId()).isNull();
                        assertThat(call.getArguments()).containsEntry("key", "report");
                      });
            });
  }

  @Nested
  class DocumentArguments {

    private final Document document =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
            .create(
                DocumentCreationRequest.from("content".getBytes(StandardCharsets.UTF_8))
                    .contentType("text/plain")
                    .fileName("report.txt")
                    .build());
    private final String documentId = DocumentHandle.idFor(document);
    private final DocumentRegistry registry =
        DocumentRegistry.empty()
            .withDocuments(
                List.of(new IngestedDocument(document, DocumentSource.userMessage(null))));
    private final Map<String, Object> reference =
        registry.findById(documentId).orElseThrow().reference();

    private final ToolDefinition upload =
        ToolDefinition.builder()
            .name("upload")
            .inputSchema(
                Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of(
                        "file",
                        Map.of("type", "string", "format", "camunda-document-id"),
                        "attachments",
                        Map.of(
                            "type",
                            "array",
                            "items",
                            Map.of("type", "string", "format", "camunda-document-id")))))
            .build();
    private final HistoryDocuments documents = new HistoryDocuments(List.of(upload), registry);

    private final ToolCall uploadCall =
        ToolCall.builder()
            .id("call-1")
            .name("upload")
            .arguments(
                Map.of(
                    "file", documentId, "attachments", List.of(documentId, "unknown"), "note", "n"))
            .build();
    private final Map<String, Object> reportedArguments =
        Map.of("file", reference, "attachments", List.of(reference, "unknown"), "note", "n");

    @Test
    void assistantToolCallShowsTheDocumentReferenceInsteadOfTheId() {
      final var calls =
          mapper.assistantToolCalls(
              AssistantMessage.builder().toolCalls(List.of(uploadCall)).build(), documents);

      assertThat(calls)
          .singleElement()
          .satisfies(call -> assertThat(call.getArguments()).isEqualTo(reportedArguments));
      assertThat(reference)
          .containsKey("camunda.document.type")
          .containsEntry("documentId", documentId);
    }

    @Test
    void toolResultShowsTheSameArgumentsAsTheAssistantToolCall() {
      final var result =
          ToolCallResultContent.builder()
              .id("call-1")
              .name("upload")
              .elementId("upload")
              .content(List.of(TextContent.textContent("200")))
              .completedAt(TOOL_RESULT_COMPLETED_AT)
              .build();

      final var items =
          mapper.inputHistoryItems(
              ToolCallResultMessage.builder().results(List.of(result)).build(),
              Map.of("call-1", uploadCall),
              TURN_INGESTION_TIMESTAMP,
              documents);

      assertThat(items)
          .singleElement()
          .satisfies(
              item ->
                  assertThat(item.toolCalls())
                      .singleElement()
                      .satisfies(
                          call -> assertThat(call.getArguments()).isEqualTo(reportedArguments)));
    }

    @Test
    void showsTheReferencesOfTheDocumentParametersOfALocalToolEvenWhenItIsNotOffered() {
      final var call =
          ToolCall.builder()
              .id("call-2")
              .name("c8__return_document")
              .arguments(Map.of("key", "report", "document", documentId))
              .build();

      // c8__return_document is not among the offered tool definitions
      final var calls =
          mapper.assistantToolCalls(
              AssistantMessage.builder().toolCalls(List.of(call)).build(), documents);

      assertThat(calls)
          .singleElement()
          .satisfies(
              reported ->
                  assertThat(reported.getArguments())
                      .isEqualTo(Map.of("key", "report", "document", reference)));
    }

    @Test
    void leavesArgumentsAsGivenWithoutKnownDocumentsOrDocumentParameters() {
      final var withoutRegistry = new HistoryDocuments(List.of(upload), DocumentRegistry.empty());
      final var plainTool =
          ToolCall.builder()
              .id("call-3")
              .name("getWeather")
              .arguments(Map.of("file", documentId))
              .build();

      assertThat(
              mapper.assistantToolCalls(
                  AssistantMessage.builder().toolCalls(List.of(uploadCall)).build(),
                  withoutRegistry))
          .singleElement()
          .satisfies(call -> assertThat(call.getArguments()).isEqualTo(uploadCall.arguments()));
      assertThat(
              mapper.assistantToolCalls(
                  AssistantMessage.builder().toolCalls(List.of(plainTool)).build(), documents))
          .singleElement()
          .satisfies(call -> assertThat(call.getArguments()).isEqualTo(plainTool.arguments()));
    }
  }

  @Test
  void distinguishesProducedAtAcrossTwoToolResultsInTheSameTurn() {
    final var fastCompletedAt = OffsetDateTime.parse("2026-07-02T09:59:50Z");
    final var slowCompletedAt = OffsetDateTime.parse("2026-07-02T09:59:58Z");
    final var fastToolCall = ToolCall.builder().id("fast").name("getWeather").build();
    final var slowToolCall = ToolCall.builder().id("slow").name("downloadFile").build();
    final var fastResult =
        ToolCallResultContent.builder()
            .id("fast")
            .name("getWeather")
            .elementId("getWeather")
            .content(List.of(TextContent.textContent("sunny")))
            .completedAt(fastCompletedAt)
            .build();
    final var slowResult =
        ToolCallResultContent.builder()
            .id("slow")
            .name("downloadFile")
            .elementId("downloadFile")
            .content(List.of(TextContent.textContent("done")))
            .completedAt(slowCompletedAt)
            .build();
    final var message =
        ToolCallResultMessage.builder().results(List.of(fastResult, slowResult)).build();

    final var items =
        mapper.inputHistoryItems(
            message,
            Map.of("fast", fastToolCall, "slow", slowToolCall),
            TURN_INGESTION_TIMESTAMP,
            NO_DOCUMENTS);

    assertThat(items).hasSize(2);
    assertThat(items.get(0).producedAt()).isEqualTo(fastCompletedAt);
    assertThat(items.get(1).producedAt()).isEqualTo(slowCompletedAt);
    assertThat(items.get(0).producedAt()).isNotEqualTo(items.get(1).producedAt());
  }

  @Test
  void throwsWhenToolResultHasNoCompletedAt() {
    // an invariant violation: ingestion normalization (ADR 008) must resolve completedAt on
    // every tool call result before it reaches the history mapper
    final var toolCall = ToolCall.builder().id("call-1").name("getWeather").build();
    final var result =
        ToolCallResultContent.builder()
            .id("call-1")
            .name("getWeather")
            .elementId("getWeather")
            .content(List.of(TextContent.textContent("sunny")))
            .build();
    final var message = ToolCallResultMessage.builder().results(List.of(result)).build();

    assertThatThrownBy(
            () ->
                mapper.inputHistoryItems(
                    message, Map.of("call-1", toolCall), TURN_INGESTION_TIMESTAMP, NO_DOCUMENTS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("call-1")
        .hasMessageContaining("completedAt");
  }

  @Test
  void reasoningContentMapsToAgentInstanceReasoningObjectContent() {
    final Map<String, Object> payload = Map.of("signature", "abc123");
    final Map<String, Object> metadata = Map.of("foo", "bar");
    final var reasoningContent =
        new ReasoningContent("anthropic", payload, "some reasoning", metadata);
    final var assistantMessage =
        AssistantMessage.builder().content(List.of(reasoningContent)).build();

    final var content = mapper.assistantContent(assistantMessage, NO_DOCUMENTS);

    assertThat(content)
        .singleElement()
        .isInstanceOfSatisfying(
            AgentInstanceHistoryContent.ObjectContent.class,
            object ->
                assertThat(object.getObject())
                    .isEqualTo(agentInstanceReasoning("some reasoning", payload)));
  }

  @Test
  void reasoningContentOmitsNullTextFromAgentInstanceReasoningObjectContent() {
    final var reasoningContent =
        new ReasoningContent("anthropic", Map.of("signature", "abc123"), null, Map.of());
    final var assistantMessage =
        AssistantMessage.builder().content(List.of(reasoningContent)).build();

    final var content = mapper.assistantContent(assistantMessage, NO_DOCUMENTS);

    assertThat(content)
        .singleElement()
        .isInstanceOfSatisfying(
            AgentInstanceHistoryContent.ObjectContent.class,
            object ->
                assertThat(object.getObject())
                    .isEqualTo(agentInstanceReasoning(null, Map.of("signature", "abc123"))));
  }

  @Test
  void reasoningContentOmitsBlankTextFromAgentInstanceReasoningObjectContent() {
    final var payload = Map.of("thinking", "   ", "signature", "abc123");
    final var reasoningContent = new ReasoningContent("anthropic", payload, "   ", Map.of());
    final var assistantMessage =
        AssistantMessage.builder().content(List.of(reasoningContent)).build();

    final var content = mapper.assistantContent(assistantMessage, NO_DOCUMENTS);

    assertThat(content)
        .singleElement()
        .isInstanceOfSatisfying(
            AgentInstanceHistoryContent.ObjectContent.class,
            object ->
                assertThat(object.getObject()).isEqualTo(agentInstanceReasoning(null, payload)));
  }

  @Test
  void preservesTranscriptOrderAndDoesNotInferReasoningFromTextOrObjects() {
    final var objectValue = Map.of("key", "value");
    final var reasoningContent =
        new ReasoningContent("anthropic", Map.of("signature", "abc123"), "thinking", Map.of());
    final var assistantMessage =
        AssistantMessage.builder()
            .content(
                List.of(
                    TextContent.textContent("before"),
                    reasoningContent,
                    new ObjectContent(objectValue, Map.of("ignored", true)),
                    TextContent.textContent("after")))
            .build();

    final var content = mapper.assistantContent(assistantMessage, NO_DOCUMENTS);

    assertThat(content).hasSize(4);
    assertThat(content.get(0))
        .isInstanceOfSatisfying(
            AgentInstanceHistoryContent.TextContent.class,
            text -> assertThat(text.getText()).isEqualTo("before"));
    assertThat(content.get(1))
        .isInstanceOfSatisfying(
            AgentInstanceHistoryContent.ObjectContent.class,
            object ->
                assertThat(object.getObject())
                    .isEqualTo(agentInstanceReasoning("thinking", Map.of("signature", "abc123"))));
    assertThat(content.get(2))
        .isInstanceOfSatisfying(
            AgentInstanceHistoryContent.ObjectContent.class,
            object -> assertThat(object.getObject()).isEqualTo(objectValue));
    assertThat(content.get(3))
        .isInstanceOfSatisfying(
            AgentInstanceHistoryContent.TextContent.class,
            text -> assertThat(text.getText()).isEqualTo("after"));
  }

  @Test
  void providerContentMapsToAnObjectHistoryBlockOfTheProviderContentItself() {
    final var payload = Map.of("id", "srvtoolu_01", "input", Map.of("query", "search term"));
    final var providerContent = ProviderContent.providerContent("anthropic", payload);
    final var assistantMessage =
        AssistantMessage.builder().content(List.of(providerContent)).build();

    final var content = mapper.assistantContent(assistantMessage, NO_DOCUMENTS);

    assertThat(content)
        .singleElement()
        .isInstanceOfSatisfying(
            AgentInstanceHistoryContent.ObjectContent.class,
            object -> assertThat(object.getObject()).isEqualTo(providerContent));
  }

  @Test
  void providerContentWithTextMapsToTextContent() {
    final var providerContent =
        new ProviderContent(
            "openai", Map.of("id", "msg_1", "phase", "commentary"), "Checking the weather", null);

    final var content =
        mapper.assistantContent(
            AssistantMessage.builder().content(List.of(providerContent)).build(), NO_DOCUMENTS);

    assertThat(content)
        .singleElement()
        .isInstanceOfSatisfying(
            AgentInstanceHistoryContent.TextContent.class,
            text -> assertThat(text.getText()).isEqualTo("Checking the weather"));
  }

  @Test
  void providerContentWithoutUsableTextMapsToTheRecordItself() {
    final var noText = new ProviderContent("openai", Map.of("id", "a"), null, null);
    final var blankText = new ProviderContent("openai", Map.of("id", "b"), "  ", null);

    for (final var providerContent : List.of(noText, blankText)) {
      final var content =
          mapper.assistantContent(
              AssistantMessage.builder().content(List.of(providerContent)).build(), NO_DOCUMENTS);

      assertThat(content)
          .singleElement()
          .isInstanceOfSatisfying(
              AgentInstanceHistoryContent.ObjectContent.class,
              object -> assertThat(object.getObject()).isEqualTo(providerContent));
    }
  }

  private static Map<String, Object> agentInstanceReasoning(String text, Object payload) {
    if (text == null || text.isBlank()) {
      return Map.of("camunda.agenticai.content.type", "reasoning", "payload", payload);
    }
    return Map.of("camunda.agenticai.content.type", "reasoning", "text", text, "payload", payload);
  }

  @Test
  void historyMetricsMapsAllTokenCountsAndDuration() {
    final var tokenUsage =
        AgentMetrics.TokenUsage.builder()
            .inputTokenCount(100)
            .outputTokenCount(50)
            .cacheReadTokenCount(200)
            .cacheCreationTokenCount(30)
            .reasoningTokenCount(20)
            .build();
    final var metrics =
        AgentMetrics.builder()
            .modelCalls(1)
            .tokenUsage(tokenUsage)
            .toolCalls(0)
            .executionTime(Duration.ofMillis(1234))
            .build();

    final var result = mapper.historyMetrics(metrics);

    assertThat(result.getInputTokens()).isEqualTo(100L);
    assertThat(result.getOutputTokens()).isEqualTo(50L);
    assertThat(result.getCacheReadTokenCount()).isEqualTo(200L);
    assertThat(result.getCacheCreationTokenCount()).isEqualTo(30L);
    assertThat(result.getReasoningTokenCount()).isEqualTo(20L);
    assertThat(result.getDurationMs()).isEqualTo(1234L);
  }
}
