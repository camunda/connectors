/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryToolCall;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.document.DocumentRestorer;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

class AgentInstanceHistoryMapperTest {

  private static final AgentContext NEW_INSTANCE =
      AgentContext.empty().withFeatures(AgentFeatures.ofNewInstance());
  private static final AgentContext WITH_DOCUMENTS =
      AgentContext.empty()
          .withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS));

  private static final OffsetDateTime TURN_INGESTION_TIMESTAMP =
      OffsetDateTime.parse("2026-07-02T10:00:00Z");
  private static final OffsetDateTime TOOL_RESULT_COMPLETED_AT =
      OffsetDateTime.parse("2026-07-02T09:59:55Z");

  private final AgentInstanceHistoryMapper mapper =
      new AgentInstanceHistoryMapper(
          Mockito.mock(GatewayToolHandlerRegistry.class), TestDocuments.objectHandleRenderer());

  private static AgentInstanceHistoryToolCall historyToolCall(
      String id, String name, String elementId, Map<String, Object> arguments) {
    return new AgentInstanceHistoryToolCall()
        .toolCallId(id)
        .toolName(name)
        .elementId(elementId)
        .arguments(arguments);
  }

  private static AgentInstanceHistoryMapper.InputHistoryItem toolResultItem(
      String id, String name, String elementId, String text, Map<String, Object> arguments) {
    return new AgentInstanceHistoryMapper.InputHistoryItem(
        AgentInstanceHistoryRole.TOOL_RESULT,
        List.of(new AgentInstanceHistoryContent.TextContent(text)),
        List.of(historyToolCall(id, name, elementId, arguments)),
        TOOL_RESULT_COMPLETED_AT,
        id);
  }

  private static HistoryDocuments historyDocuments(DocumentHandling handling) {
    return new HistoryDocuments(handling, new DocumentRestorer(TestDocuments.documentFactory()));
  }

  @Test
  void userMessageUsesThePassedInTurnIngestionTimestamp() {
    final var userMessage =
        UserMessage.builder().content(MessageUtil.singleTextContent("hi")).build();

    final var items =
        mapper.inputHistoryItems(
            userMessage,
            Map.of(),
            TURN_INGESTION_TIMESTAMP,
            historyDocuments(NEW_INSTANCE.documentHandling()));

    assertThat(items).singleElement().extracting("producedAt").isEqualTo(TURN_INGESTION_TIMESTAMP);
  }

  private static String objectJson(AgentInstanceHistoryContent content) {
    return TestObjectMapperSupplier.INSTANCE
        .valueToTree(((AgentInstanceHistoryContent.ObjectContent) content).getObject())
        .toString();
  }

  private static List<String> texts(List<AgentInstanceHistoryContent> content) {
    return content.stream()
        .filter(AgentInstanceHistoryContent.TextContent.class::isInstance)
        .map(c -> ((AgentInstanceHistoryContent.TextContent) c).getText())
        .toList();
  }

  @Test
  void
      aUserMessageWithADocumentBlockIsReportedWithTheHandleObjectAndTheDocumentFromTheAgentDocuments() {
    final var document =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
            .create(
                DocumentCreationRequest.from("content".getBytes(StandardCharsets.UTF_8))
                    .contentType("text/plain")
                    .fileName("report.txt")
                    .build());
    final var agentDocuments =
        AgentDocuments.empty()
            .register(
                List.of(
                    AgentDocument.of(
                        document, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))));
    final var block = AgentDocumentContent.of(AgentDocumentId.of(document));
    final var userMessage =
        UserMessage.builder().content(List.of(TextContent.textContent("see"), block)).build();

    final var items =
        mapper.inputHistoryItems(
            userMessage,
            Map.of(),
            TURN_INGESTION_TIMESTAMP,
            historyDocuments(WITH_DOCUMENTS.withDocuments(agentDocuments).documentHandling()));

    assertThat(items)
        .singleElement()
        .satisfies(item -> {
          assertThat(item.content()).hasSize(3);
          assertThat(texts(item.content())).containsExactly("see");
          assertThat(objectJson(item.content().get(1)))
              .isEqualTo(
                  "{\"type\":\"camunda.agenticai.document\",\"id\":\"%s\",\"fileName\":\"report.txt\",\"contentType\":\"text/plain\"}"
                      .formatted(block.id().value()));
          assertThat(item.content().get(2))
              .isInstanceOfSatisfying(
                  AgentInstanceHistoryContent.DocumentContent.class,
                  chip ->
                      assertThat(chip.getDocumentReference().getMetadata().getFileName())
                          .isEqualTo("report.txt"));
        });
  }

  @Test
  void theMessageThatLoadsADocumentIsReportedAsAUserItemWithTheHandleAndTheDocumentReference() {
    final var document =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
            .create(
                DocumentCreationRequest.from("content".getBytes(StandardCharsets.UTF_8))
                    .contentType("text/plain")
                    .fileName("report.txt")
                    .build());
    final var agentDocuments =
        AgentDocuments.empty()
            .register(
                List.of(
                    AgentDocument.of(document, AgentDocumentOrigin.toolCall("fetch", "call-1"))));
    final var tag =
        AgentDocumentContent.of(AgentDocumentId.of(document))
            .withDelivery(DocumentDelivery.CONTENT);
    final var loadedMessage =
        UserMessage.builder()
            .content(
                List.of(
                    TextContent.textContent(
                        "Document content from tool calls (added by the system):"),
                    tag))
            .metadata(Map.of(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true))
            .build();

    final var items =
        mapper.inputHistoryItems(
            loadedMessage,
            Map.of(),
            TURN_INGESTION_TIMESTAMP,
            historyDocuments(WITH_DOCUMENTS.withDocuments(agentDocuments).documentHandling()));

    assertThat(items)
        .singleElement()
        .satisfies(item -> {
          assertThat(item.role()).isEqualTo(AgentInstanceHistoryRole.USER);
          assertThat(texts(item.content()))
              .containsExactly("Document content from tool calls (added by the system):");
          assertThat(item.content()).hasSize(3);
          assertThat(objectJson(item.content().get(1)))
              .isEqualTo(TestDocuments.handleText(agentDocuments, tag.id()));
          assertThat(item.content().get(2))
              .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);
        });
  }

  private static final String CONTENT_PREAMBLE =
      "Document content from tool calls (added by the system):";

  private static AgentDocuments knownDocuments(Document... documents) {
    return AgentDocuments.empty()
        .register(
            Arrays.stream(documents)
                .map(document ->
                    AgentDocument.of(document, AgentDocumentOrigin.toolCall("fetch", "call-1")))
                .toList());
  }

  private static Document reportDocument(String fileName) {
    return new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
        .create(
            DocumentCreationRequest.from("content".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName(fileName)
                .build());
  }

  @Test
  void theContentMessageIsReportedAsThePreambleTheDeliveredHandleWithItsChipAndTheWithheldHandle() {
    final var delivered = reportDocument("delivered.txt");
    final var withheld = reportDocument("withheld.zip");
    final var agentDocuments = knownDocuments(delivered, withheld);
    final var deliveredBlock =
        AgentDocumentContent.of(AgentDocumentId.of(delivered))
            .withDelivery(DocumentDelivery.CONTENT);
    final var withheldHandle =
        AgentDocumentHandle.of(agentDocuments, AgentDocumentId.of(withheld))
            .withNote(
                "Documents of type application/zip cannot be loaded. If one of your tools can process the document, pass the handle to it.");
    final var message =
        UserMessage.builder()
            .content(
                List.of(
                    TextContent.textContent(CONTENT_PREAMBLE),
                    deliveredBlock,
                    TextContent.textContent(withheldHandle.text())))
            .metadata(Map.of(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true))
            .build();

    final var items =
        mapper.inputHistoryItems(
            message,
            Map.of(),
            TURN_INGESTION_TIMESTAMP,
            historyDocuments(WITH_DOCUMENTS.withDocuments(agentDocuments).documentHandling()));

    assertThat(items)
        .singleElement()
        .satisfies(item -> {
          assertThat(item.content()).hasSize(4);
          assertThat(texts(item.content())).containsExactly(CONTENT_PREAMBLE);
          assertThat(objectJson(item.content().get(1)))
              .isEqualTo(TestDocuments.handleText(agentDocuments, deliveredBlock.id()));
          assertThat(item.content().get(2))
              .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);
          assertThat(objectJson(item.content().get(3))).isEqualTo(withheldHandle.text());
          assertThat(objectJson(item.content().get(3))).contains("\"note\":\"Documents of type");
        });
  }

  @Test
  void aTextOfAContentMessageThatIsNotTheHandleOfAKnownDocumentStaysText() {
    final var known = reportDocument("known.txt");
    final var agentDocuments = knownDocuments(known);
    final var unknownHandle =
        TestDocuments.unknownHandleText(AgentDocumentId.of("doc_000000000000"));
    final var quoted = "See " + TestDocuments.handleText(agentDocuments, AgentDocumentId.of(known));
    final var message =
        UserMessage.builder()
            .content(
                List.of(TextContent.textContent(unknownHandle), TextContent.textContent(quoted)))
            .metadata(Map.of(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true))
            .build();

    final var items =
        mapper.inputHistoryItems(
            message,
            Map.of(),
            TURN_INGESTION_TIMESTAMP,
            historyDocuments(WITH_DOCUMENTS.withDocuments(agentDocuments).documentHandling()));

    assertThat(items)
        .singleElement()
        .satisfies(
            item -> assertThat(texts(item.content())).containsExactly(unknownHandle, quoted));
  }

  @Test
  void aTextOfAnOrdinaryUserMessageThatIsAHandleStaysText() {
    final var known = reportDocument("known.txt");
    final var agentDocuments = knownDocuments(known);
    final var handle = TestDocuments.handleText(agentDocuments, AgentDocumentId.of(known));
    final var message =
        UserMessage.builder().content(List.of(TextContent.textContent(handle))).build();

    final var items =
        mapper.inputHistoryItems(
            message,
            Map.of(),
            TURN_INGESTION_TIMESTAMP,
            historyDocuments(WITH_DOCUMENTS.withDocuments(agentDocuments).documentHandling()));

    assertThat(items)
        .singleElement()
        .satisfies(item -> assertThat(texts(item.content())).containsExactly(handle));
  }

  @Test
  void theAnswerOfAToolThatIsExactlyAHandleIsReportedAsTheHandleObjectAndItsSentenceAsText() {
    final var known = reportDocument("known.txt");
    final var agentDocuments = knownDocuments(known);
    final var handle = TestDocuments.handleText(agentDocuments, AgentDocumentId.of(known));
    final var toolCall = ToolCall.builder().id("call-1").name("c8__load_document").build();
    final var result =
        ToolCallResultContent.builder()
            .id("call-1")
            .name("c8__load_document")
            .content(
                List.of(
                    TextContent.textContent(handle),
                    TextContent.textContent("The content follows in the next message.")))
            .completedAt(TURN_INGESTION_TIMESTAMP)
            .build();

    final var items =
        mapper.inputHistoryItems(
            ToolCallResultMessage.builder().results(List.of(result)).build(),
            Map.of("call-1", toolCall),
            TURN_INGESTION_TIMESTAMP,
            historyDocuments(WITH_DOCUMENTS.withDocuments(agentDocuments).documentHandling()));

    assertThat(items)
        .singleElement()
        .satisfies(item -> {
          assertThat(item.content()).hasSize(2);
          assertThat(objectJson(item.content().get(0))).isEqualTo(handle);
          assertThat(texts(item.content()))
              .containsExactly("The content follows in the next message.");
        });
  }

  @Test
  void aDocumentBlockOfAnUnknownDocumentIsReportedAsTheHandleObjectOnly() {
    final var tag =
        new AgentDocumentContent(AgentDocumentId.of("unknown"), DocumentDelivery.REFERENCE, null);
    final var userMessage = UserMessage.builder().content(List.of(tag)).build();

    final var items =
        mapper.inputHistoryItems(
            userMessage,
            Map.of(),
            TURN_INGESTION_TIMESTAMP,
            historyDocuments(WITH_DOCUMENTS.documentHandling()));

    assertThat(items)
        .singleElement()
        .satisfies(item -> {
          assertThat(item.content()).hasSize(1);
          assertThat(objectJson(item.content().getFirst()))
              .isEqualTo(TestDocuments.unknownHandleText(tag.id()));
        });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Documents (<doc /> tag + content pair): <doc id=\"a\" /> and <doc id=\"b\" fileName=\"b.txt\" />.",
        "quoted `<doc id=\"a\" />` and plain <doc id=\"b\" />",
        "a <document> and <b>bold</b> text"
      })
  void aTextOfAUserWithOrWithoutADocTagIsReportedVerbatim(String text) {
    final var userMessage =
        UserMessage.builder().content(List.of(TextContent.textContent(text))).build();

    final var items =
        mapper.inputHistoryItems(
            userMessage,
            Map.of(),
            TURN_INGESTION_TIMESTAMP,
            historyDocuments(WITH_DOCUMENTS.documentHandling()));

    assertThat(items)
        .singleElement()
        .satisfies(item -> assertThat(texts(item.content())).containsExactly(text));
  }

  @Test
  void aToolResultTextQuotingADocTagIsReportedVerbatim() {
    final var toolCall = ToolCall.builder().id("call-1").name("getWeather").build();
    final var result =
        ToolCallResultContent.builder()
            .id("call-1")
            .name("getWeather")
            .elementId("getWeather")
            .content(
                List.of(
                    TextContent.textContent(
                        "Parameter 'id' must be the <doc /> tag of a known document, got '<doc id=\"a\" />'.")))
            .completedAt(TURN_INGESTION_TIMESTAMP)
            .build();

    final var items =
        mapper.inputHistoryItems(
            ToolCallResultMessage.builder().results(List.of(result)).build(),
            Map.of("call-1", toolCall),
            TURN_INGESTION_TIMESTAMP,
            historyDocuments(WITH_DOCUMENTS.documentHandling()));

    assertThat(items)
        .singleElement()
        .satisfies(item ->
            assertThat(texts(item.content()))
                .containsExactly(
                    "Parameter 'id' must be the <doc /> tag of a known document, got '<doc id=\"a\" />'."));
  }

  @Test
  void anAssistantTextWithADocTagIsReportedVerbatim() {
    final var content =
        mapper.assistantContent(
            AssistantMessage.builder()
                .content(List.of(TextContent.textContent("I use <doc id=\"a\" />")))
                .build());

    assertThat(texts(content)).containsExactly("I use <doc id=\"a\" />");
  }

  @Test
  void withoutTheDocumentsFeatureTextsAreReportedAsStored() {
    final var withoutDocumentFlow = AgentContext.empty();
    final var tag = "<doc toolName=\"t\" toolCallId=\"c\" documentId=\"d1\" />";
    final var assistant =
        AssistantMessage.builder()
            .content(List.of(TextContent.textContent("I use " + tag)))
            .build();
    final var user =
        UserMessage.builder()
            .content(List.of(TextContent.textContent(tag), TextContent.textContent("see " + tag)))
            .build();

    assertThat(texts(mapper.assistantContent(assistant))).containsExactly("I use " + tag);
    final var userItems =
        mapper.inputHistoryItems(
            user,
            Map.of(),
            TURN_INGESTION_TIMESTAMP,
            historyDocuments(withoutDocumentFlow.documentHandling()));
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
            message,
            Map.of("call-1", toolCall),
            TURN_INGESTION_TIMESTAMP,
            historyDocuments(NEW_INSTANCE.documentHandling()));

    assertThat(items)
        .singleElement()
        .satisfies(item -> {
          assertThat(item.role()).isEqualTo(AgentInstanceHistoryRole.TOOL_RESULT);
          assertThat(item.producedAt()).isEqualTo(TOOL_RESULT_COMPLETED_AT);
        });
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
            historyDocuments(NEW_INSTANCE.documentHandling()));

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

    assertThatThrownBy(() ->
            mapper.inputHistoryItems(
                message,
                Map.of("call-1", toolCall),
                TURN_INGESTION_TIMESTAMP,
                historyDocuments(NEW_INSTANCE.documentHandling())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("call-1")
        .hasMessageContaining("completedAt");
  }

  @Test
  void aCallAndAResultOfARuntimeToolHaveNoElementId() {
    final var toolCall =
        ToolCall.builder()
            .id("call-1")
            .name("acme_lookup")
            .arguments(Map.of("customerId", "42"))
            .kind(ToolKind.RUNTIME)
            .build();
    final var result =
        ToolCallResultContent.builder()
            .id("call-1")
            .name("acme_lookup")
            .content(List.of(TextContent.textContent("Found the customer.")))
            .completedAt(TOOL_RESULT_COMPLETED_AT)
            .build();

    final var assistantCalls =
        mapper.assistantToolCalls(AssistantMessage.builder().toolCalls(List.of(toolCall)).build());
    final var items =
        mapper.inputHistoryItems(
            ToolCallResultMessage.builder().results(List.of(result)).build(),
            Map.of("call-1", toolCall),
            TURN_INGESTION_TIMESTAMP,
            historyDocuments(NEW_INSTANCE.documentHandling()));

    assertThat(assistantCalls)
        .singleElement()
        .satisfies(call -> {
          assertThat(call.getToolName()).isEqualTo("acme_lookup");
          assertThat(call.getElementId()).isNull();
        });
    assertThat(items)
        .singleElement()
        .satisfies(item -> {
          assertThat(item.role()).isEqualTo(AgentInstanceHistoryRole.TOOL_RESULT);
          assertThat(item.content())
              .singleElement()
              .isInstanceOfSatisfying(
                  AgentInstanceHistoryContent.TextContent.class,
                  text -> assertThat(text.getText()).isEqualTo("Found the customer."));
          assertThat(item.toolCalls())
              .singleElement()
              .satisfies(call -> {
                assertThat(call.getToolName()).isEqualTo("acme_lookup");
                assertThat(call.getToolCallId()).isEqualTo("call-1");
                assertThat(call.getElementId()).isNull();
                assertThat(call.getArguments()).containsEntry("customerId", "42");
              });
        });
  }

  @Test
  void aCallOfAProcessToolKeepsItsElementIdWhateverItsName() {
    final var assistantCalls =
        mapper.assistantToolCalls(
            AssistantMessage.builder()
                .toolCalls(List.of(ToolCall.builder().id("call-1").name("acme_lookup").build()))
                .build());

    assertThat(assistantCalls)
        .singleElement()
        .satisfies(call -> assertThat(call.getElementId()).isEqualTo("acme_lookup"));
  }

  @Test
  void reasoningContentMapsToAgentInstanceReasoningObjectContent() {
    final Map<String, Object> payload = Map.of("signature", "abc123");
    final Map<String, Object> metadata = Map.of("foo", "bar");
    final var reasoningContent =
        new ReasoningContent("anthropic", payload, "some reasoning", metadata);
    final var assistantMessage =
        AssistantMessage.builder().content(List.of(reasoningContent)).build();

    final var content = mapper.assistantContent(assistantMessage);

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

    final var content = mapper.assistantContent(assistantMessage);

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

    final var content = mapper.assistantContent(assistantMessage);

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

    final var content = mapper.assistantContent(assistantMessage);

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

    final var content = mapper.assistantContent(assistantMessage);

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
            AssistantMessage.builder().content(List.of(providerContent)).build());

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
              AssistantMessage.builder().content(List.of(providerContent)).build());

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

  @Nested
  class DocumentArguments {

    private final Document document =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
            .create(
                DocumentCreationRequest.from("content".getBytes(StandardCharsets.UTF_8))
                    .contentType("text/plain")
                    .fileName("report.txt")
                    .build());
    private final AgentDocumentId documentId = AgentDocumentId.of(document);
    private final AgentDocuments documents =
        AgentDocuments.empty()
            .register(
                List.of(
                    AgentDocument.of(
                        document, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))));
    private final AgentContext agentContext = WITH_DOCUMENTS.withDocuments(documents);

    private final ToolCall uploadCall =
        ToolCall.builder()
            .id("call-1")
            .name("upload")
            .arguments(
                Map.of(
                    "file",
                    "<doc id=\"" + documentId + "\" />",
                    "attachments",
                    List.of("<doc id=\"" + documentId + "\" />", "unknown"),
                    "note",
                    "n"))
            .build();

    @Test
    void assistantToolCallShowsTheArgumentsAsTheModelSentThem() {
      final var calls =
          mapper.assistantToolCalls(
              AssistantMessage.builder().toolCalls(List.of(uploadCall)).build());

      assertThat(calls)
          .usingRecursiveComparison()
          .isEqualTo(
              List.of(historyToolCall("call-1", "upload", "upload", uploadCall.arguments())));
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
              historyDocuments(agentContext.documentHandling()));

      assertThat(items)
          .usingRecursiveComparison()
          .isEqualTo(
              List.of(toolResultItem("call-1", "upload", "upload", "200", uploadCall.arguments())));
    }

    @Test
    void showsTheTagOfTheDocumentParameterOfARuntimeToolCallAsTheModelSentIt() {
      final var tag = "<doc id=\"" + documentId + "\" />";
      final var call =
          ToolCall.builder()
              .id("call-2")
              .name("acme_lookup")
              .arguments(Map.of("key", "report", "document", tag))
              .kind(ToolKind.RUNTIME)
              .build();

      final var calls =
          mapper.assistantToolCalls(AssistantMessage.builder().toolCalls(List.of(call)).build());

      assertThat(calls)
          .usingRecursiveComparison()
          .isEqualTo(
              List.of(
                  historyToolCall(
                      "call-2", "acme_lookup", null, Map.of("key", "report", "document", tag))));
    }

    @Test
    void aRejectedCallOfAToolOfTheProcessHasNoElementIdAndShowsTheArgumentsAsTheModelSentThem() {
      final var rejected =
          ToolCall.builder()
              .id("call-3")
              .name("upload")
              .arguments(Map.of("file", "bogus"))
              .rejection("Parameter 'file' must be a tag.")
              .build();
      final var result =
          ToolCallResultContent.builder()
              .id("call-3")
              .name("upload")
              .content(List.of(TextContent.textContent("Parameter 'file' must be a tag.")))
              .completedAt(TOOL_RESULT_COMPLETED_AT)
              .build();

      final var calls =
          mapper.assistantToolCalls(
              AssistantMessage.builder().toolCalls(List.of(rejected)).build());
      final var items =
          mapper.inputHistoryItems(
              ToolCallResultMessage.builder().results(List.of(result)).build(),
              Map.of("call-3", rejected),
              TURN_INGESTION_TIMESTAMP,
              historyDocuments(agentContext.documentHandling()));

      assertThat(calls)
          .usingRecursiveComparison()
          .isEqualTo(List.of(historyToolCall("call-3", "upload", null, rejected.arguments())));
      assertThat(items)
          .usingRecursiveComparison()
          .isEqualTo(
              List.of(
                  toolResultItem(
                      "call-3",
                      "upload",
                      null,
                      "Parameter 'file' must be a tag.",
                      rejected.arguments())));
    }

    @Test
    void showsTheArgumentsAsGivenForAnAgentWithoutDocuments() {
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
              historyDocuments(NEW_INSTANCE.documentHandling()));

      assertThat(items)
          .usingRecursiveComparison()
          .isEqualTo(
              List.of(toolResultItem("call-1", "upload", "upload", "200", uploadCall.arguments())));
    }
  }

  @Nested
  class ToolResultDocuments {

    private final ObjectMapper objectMapper = TestObjectMapperSupplier.INSTANCE;
    private final DocumentFactoryImpl documentFactory =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

    private final Document document =
        documentFactory.create(
            DocumentCreationRequest.from("content".getBytes(StandardCharsets.UTF_8))
                .contentType("application/pdf")
                .fileName("report.pdf")
                .timeToLive(Duration.ofHours(2))
                .processDefinitionId("process-1")
                .processInstanceKey(42L)
                .customProperties(Map.of("origin", "test"))
                .build());
    private final Document otherDocument =
        documentFactory.create(
            DocumentCreationRequest.from("other".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName("other.txt")
                .build());
    private final String handle = TestDocuments.handleText(document);
    private final Map<String, Object> handleObject = TestDocuments.handleObject(document);
    private final String otherHandle = TestDocuments.handleText(otherDocument);

    private final AgentDocuments agentDocuments =
        AgentDocuments.empty()
            .register(
                List.of(
                    AgentDocument.of(document, AgentDocumentOrigin.toolCall("fetch", "call-1")),
                    AgentDocument.of(
                        otherDocument, AgentDocumentOrigin.toolCall("fetch", "call-1"))));
    private final AgentContext agentContext = WITH_DOCUMENTS.withDocuments(agentDocuments);

    private final ToolCall call =
        ToolCall.builder()
            .id("call-1")
            .name("fetch")
            .arguments(Map.of("document", AgentDocumentId.of(document), "n", 1))
            .build();

    private ToolCallResultMessage message(Content... content) {
      return ToolCallResultMessage.builder()
          .results(
              List.of(
                  ToolCallResultContent.builder()
                      .id("call-1")
                      .name("fetch")
                      .elementId("fetch")
                      .content(List.of(content))
                      .completedAt(TOOL_RESULT_COMPLETED_AT)
                      .build()))
          .build();
    }

    private AgentInstanceHistoryMapper.InputHistoryItem item(
        ToolCallResultMessage message, AgentContext agentContext) {
      final var items =
          mapper.inputHistoryItems(
              message,
              Map.of("call-1", call),
              TURN_INGESTION_TIMESTAMP,
              historyDocuments(agentContext.documentHandling()));
      assertThat(items).hasSize(1);
      return items.getFirst();
    }

    /** The message as the conversation store gives it back in the next job. */
    private ToolCallResultMessage persisted(ToolCallResultMessage message) throws Exception {
      return objectMapper.readValue(
          objectMapper.writeValueAsString(message), ToolCallResultMessage.class);
    }

    private String describe(AgentInstanceHistoryContent content) {
      return switch (content) {
        case AgentInstanceHistoryContent.TextContent text -> "TEXT " + text.getText();
        case AgentInstanceHistoryContent.ObjectContent object ->
            "OBJECT " + objectMapper.valueToTree(object.getObject());
        case AgentInstanceHistoryContent.DocumentContent doc -> {
          final var reference = doc.getDocumentReference();
          final var metadata = reference.getMetadata();
          yield "DOCUMENT %s %s %s %s %s %s %s %s %s"
              .formatted(
                  reference.getDocumentId(),
                  reference.getStoreId(),
                  reference.getContentHash(),
                  metadata.getFileName(),
                  metadata.getContentType(),
                  metadata.getSize(),
                  metadata.getExpiresAt(),
                  metadata.getProcessDefinitionId() + "/" + metadata.getProcessInstanceKey(),
                  metadata.getCustomProperties());
        }
        default -> content.toString();
      };
    }

    private JsonNode tree(Object value) {
      return objectMapper.valueToTree(value);
    }

    private List<String> described(AgentInstanceHistoryMapper.InputHistoryItem item) {
      return item.content().stream().map(this::describe).toList();
    }

    @Test
    void theExpiryOfADocumentIsInUtcWithAndWithoutTracking() {
      final var metadata = Mockito.mock(DocumentMetadata.class);
      Mockito.when(metadata.getExpiresAt())
          .thenReturn(OffsetDateTime.parse("2026-07-02T12:00:00+02:00"));
      Mockito.when(metadata.getFileName()).thenReturn("report.pdf");
      Mockito.when(metadata.getContentType()).thenReturn("application/pdf");
      final var reference = Mockito.mock(CamundaDocumentReference.class);
      Mockito.when(reference.getStoreId()).thenReturn("in-memory");
      Mockito.when(reference.getDocumentId()).thenReturn("expiring");
      Mockito.when(reference.getContentHash()).thenReturn("hash");
      Mockito.when(reference.getMetadata()).thenReturn(metadata);
      final var expiring = Mockito.mock(Document.class);
      Mockito.when(expiring.reference()).thenReturn(reference);
      Mockito.when(expiring.metadata()).thenReturn(metadata);
      final var message = message(DocumentContent.documentContent(expiring));

      assertThat(
              expiresAtOf(
                  item(
                      message,
                      AgentContext.empty().withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS)))))
          .isEqualTo("2026-07-02T10:00Z");
      assertThat(expiresAtOf(item(message, agentContext))).isEqualTo("2026-07-02T10:00Z");
    }

    private String expiresAtOf(AgentInstanceHistoryMapper.InputHistoryItem item) {
      return item.content().stream()
          .filter(AgentInstanceHistoryContent.DocumentContent.class::isInstance)
          .map(AgentInstanceHistoryContent.DocumentContent.class::cast)
          .map(content ->
              String.valueOf(content.getDocumentReference().getMetadata().getExpiresAt()))
          .findFirst()
          .orElseThrow();
    }

    @Test
    void aToolResultThatIsOneLiveDocumentIsTheHandleObjectAndTheChip() {
      final var item = item(message(DocumentContent.documentContent(document)), agentContext);

      assertThat(item.role()).isEqualTo(AgentInstanceHistoryRole.TOOL_RESULT);
      assertThat(item.content()).hasSize(2);
      assertThat(described(item).get(0)).isEqualTo("OBJECT " + handle);
      assertThat(item.content().get(1))
          .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);
    }

    @Test
    void aDocumentOfAnEventIsTheHandleObjectAndTheChip() {
      final var event =
          ToolCallResultMessage.builder()
              .results(
                  List.of(
                      ToolCallResultContent.builder()
                          .name("event")
                          .elementId("event")
                          .content(List.of(DocumentContent.documentContent(document)))
                          .completedAt(TOOL_RESULT_COMPLETED_AT)
                          .build()))
              .build();

      final var items =
          mapper.inputHistoryItems(
              event,
              Map.of(),
              TURN_INGESTION_TIMESTAMP,
              historyDocuments(agentContext.documentHandling()));

      assertThat(items)
          .singleElement()
          .satisfies(item -> {
            assertThat(item.content()).hasSize(2);
            assertThat(described(item).get(0)).isEqualTo("OBJECT " + handle);
            assertThat(item.content().get(1))
                .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);
          });
    }

    @Test
    void theChipOfADocumentWithoutAFileNameShowsTheAgentDocumentId() {
      final var unnamed =
          documentFactory.create(
              DocumentCreationRequest.from("unnamed".getBytes(StandardCharsets.UTF_8))
                  .contentType("text/plain")
                  .build());
      final var id = AgentDocumentId.of(unnamed).value();
      final var tracked =
          WITH_DOCUMENTS.withDocuments(
              AgentDocuments.empty()
                  .register(
                      List.of(
                          AgentDocument.of(
                              unnamed, AgentDocumentOrigin.toolCall("fetch", "call-1")))));

      final var item = item(message(DocumentContent.documentContent(unnamed)), tracked);

      assertThat(described(item).get(0))
          .isEqualTo(
              "OBJECT {\"type\":\"camunda.agenticai.document\",\"id\":\"%s\",\"contentType\":\"text/plain\"}"
                  .formatted(id));
      assertThat(item.content().get(1))
          .isInstanceOfSatisfying(
              AgentInstanceHistoryContent.DocumentContent.class,
              chip -> {
                assertThat(chip.getDocumentReference().getMetadata().getFileName()).isEqualTo(id);
                assertThat(chip.getDocumentReference().getMetadata().getContentType())
                    .isEqualTo("text/plain");
              });
      // the document and its reference are left as they are
      assertThat(unnamed.metadata().getFileName()).isNotEqualTo(id);
      // without tracking there is no handle, so the chip keeps the stored file name
      final var untracked =
          item(message(DocumentContent.documentContent(unnamed)), AgentContext.empty());
      assertThat(untracked.content())
          .singleElement()
          .isInstanceOfSatisfying(
              AgentInstanceHistoryContent.DocumentContent.class,
              chip ->
                  assertThat(chip.getDocumentReference().getMetadata().getFileName())
                      .isEqualTo(unnamed.metadata().getFileName()));
    }

    @Test
    void aBareTypedReferenceAndTheEarlyAndTheLaterWriteOfABareDocumentAreReportedAlike()
        throws Exception {
      final var liveMessage = message(DocumentContent.documentContent(document));
      final var live = item(liveMessage, agentContext);
      final var stored =
          item(message(AgentDocumentContent.of(AgentDocumentId.of(document))), agentContext);

      // the early write knows no document of this turn yet
      final var early = item(liveMessage, WITH_DOCUMENTS);
      final var later =
          item(
              persisted(message(AgentDocumentContent.of(AgentDocumentId.of(document)))),
              agentContext);

      assertThat(described(stored)).isEqualTo(described(live));
      assertThat(described(later)).isEqualTo(described(early)).hasSize(2);
    }

    @Test
    void aReferenceBlockBetweenTextsIsTheHandleObjectAndTheDocumentInTheOrderOfTheBlocks() {
      final var block = AgentDocumentContent.of(AgentDocumentId.of(document));

      final var item =
          item(
              message(
                  TextContent.textContent("Loaded the document."),
                  block,
                  TextContent.textContent("Its content follows in the next message.")),
              agentContext);

      assertThat(item.content()).hasSize(4);
      assertThat(described(item).get(0)).isEqualTo("TEXT Loaded the document.");
      assertThat(described(item).get(1))
          .isEqualTo(
              "OBJECT {\"type\":\"camunda.agenticai.document\",\"id\":\"%s\",\"fileName\":\"report.pdf\",\"contentType\":\"application/pdf\"}"
                  .formatted(block.id().value()));
      assertThat(item.content().get(2))
          .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);
      assertThat(described(item).get(3)).isEqualTo("TEXT Its content follows in the next message.");
    }

    @Test
    void aHandleWrittenInTheTextOfAToolResultIsReportedVerbatimNextToTheReferenceBlock() {
      final var item =
          item(
              message(
                  TextContent.textContent("The tool wrote " + handle),
                  AgentDocumentContent.of(AgentDocumentId.of(otherDocument))),
              agentContext);

      assertThat(texts(item.content())).containsExactly("The tool wrote " + handle);
      assertThat(described(item).get(1)).isEqualTo("OBJECT " + otherHandle);
    }

    @Test
    void aTypedReferenceOfAnUnknownDocumentIsItsHandleOnly() {
      final var unknown =
          new AgentDocumentContent(AgentDocumentId.of("unknown"), DocumentDelivery.REFERENCE, null);

      final var item = item(message(unknown), WITH_DOCUMENTS);

      assertThat(item.content()).hasSize(1);
      assertThat(described(item))
          .containsExactly("OBJECT " + TestDocuments.unknownHandleText(unknown.id()));
    }

    @Test
    void aNestedDocumentIsTheHandleObjectInTheObjectAndTheDocumentFollowsTheObject() {
      final var item =
          item(
              message(
                  ObjectContent.objectContent(
                      Map.of("status", 200, "file", document, "meta", Map.of("kind", "pdf")))),
              agentContext);

      assertThat(item.content()).hasSize(2);
      assertThat(item.content().getFirst())
          .isInstanceOfSatisfying(
              AgentInstanceHistoryContent.ObjectContent.class,
              object ->
                  assertThat(tree(object.getObject()))
                      .isEqualTo(
                          tree(
                              Map.of(
                                  "status",
                                  200,
                                  "file",
                                  handleObject,
                                  "meta",
                                  Map.of("kind", "pdf")))));
      assertThat(item.content().get(1))
          .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);
    }

    @Test
    void theHandleInAnObjectEqualsWhatTheModelSees() throws Exception {
      final var message = message(ObjectContent.objectContent(Map.of("file", document)));
      final var item = item(message, agentContext);

      final var reported =
          ((AgentInstanceHistoryContent.ObjectContent) item.content().getFirst()).getObject();
      assertThat(reported).isEqualTo(Map.of("file", handleObject));
      final var modelFacing =
          (ToolCallResultMessage)
              TestDocuments.documentExpander()
                  .expand(
                      new ConversationSnapshot(List.of(message), List.of()),
                      new DocumentHandling.Tracked(AgentDocuments.empty()),
                      TestDocuments.documentFactory())
                  .messages()
                  .getFirst();
      assertThat(modelFacing.results().getFirst().content())
          .containsExactly(ObjectContent.objectContent(reported));
    }

    @Test
    void aDocumentNestedAtAnyDepthAndInARecordIsReplacedByItsHandle() {
      record Attachment(String name, Document file) {}

      final var item =
          item(
              message(
                  ObjectContent.objectContent(
                      Map.of("attachments", List.of(new Attachment("a", document))))),
              agentContext);

      assertThat(item.content()).hasSize(2);
      assertThat(
              tree(
                  ((AgentInstanceHistoryContent.ObjectContent) item.content().getFirst())
                      .getObject()))
          .isEqualTo(
              tree(Map.of("attachments", List.of(Map.of("name", "a", "file", handleObject)))));
    }

    @Test
    void theDocumentsOfAnObjectAreReportedOnceEachInOrderOfAppearance() {
      final var item =
          item(
              message(
                  ObjectContent.objectContent(
                      List.of(
                          Map.of("file", document),
                          Map.of("file", otherDocument),
                          Map.of("again", document)))),
              agentContext);

      assertThat(item.content()).hasSize(3);
      assertThat(item.content().get(1))
          .isInstanceOfSatisfying(
              AgentInstanceHistoryContent.DocumentContent.class,
              doc ->
                  assertThat(doc.getDocumentReference().getDocumentId())
                      .isEqualTo(
                          ((CamundaDocumentReference) document.reference()).getDocumentId()));
      assertThat(item.content().get(2))
          .isInstanceOfSatisfying(
              AgentInstanceHistoryContent.DocumentContent.class,
              doc ->
                  assertThat(doc.getDocumentReference().getDocumentId())
                      .isEqualTo(
                          ((CamundaDocumentReference) otherDocument.reference()).getDocumentId()));
    }

    @Test
    void theEarlyAndTheLaterWriteOfANestedDocumentAreIdentical() throws Exception {
      final var content =
          ObjectContent.objectContent(
              Map.of("status", 200, "file", document, "list", List.of(otherDocument)));

      final var early = item(message(content), WITH_DOCUMENTS);
      final var later = item(persisted(message(content)), agentContext);

      assertThat(described(later)).isEqualTo(described(early)).hasSize(3);
    }

    @Test
    void aMixedResultKeepsTheOrderOfItsContent() {
      final var item =
          item(
              message(
                  TextContent.textContent("done"),
                  AgentDocumentContent.of(AgentDocumentId.of(otherDocument)),
                  ObjectContent.objectContent(Map.of("file", document))),
              agentContext);

      assertThat(item.content()).hasSize(5);
      assertThat(texts(item.content())).containsExactly("done");
      assertThat(described(item).get(1)).isEqualTo("OBJECT " + otherHandle);
      assertThat(item.content().get(2))
          .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);
      assertThat(item.content().get(3))
          .isInstanceOf(AgentInstanceHistoryContent.ObjectContent.class);
      assertThat(item.content().get(4))
          .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);
    }

    @Test
    void anObjectWithoutDocumentsIsReportedAsItIs() {
      final var value = Map.of("status", 200, "items", List.of("a", "b"));

      final var item = item(message(ObjectContent.objectContent(value)), agentContext);

      assertThat(item.content())
          .singleElement()
          .isInstanceOfSatisfying(
              AgentInstanceHistoryContent.ObjectContent.class,
              object -> assertThat(object.getObject()).isSameAs(value));
    }

    @Test
    void withoutTheDocumentsFeatureAnExternalDocumentIsReportedAsItsReference() {
      final var external = new ExternalDocument("https://example.com/a.pdf", "a.pdf", u -> null);

      final var item =
          item(message(DocumentContent.documentContent(external)), AgentContext.empty());

      assertThat(item.content()).hasSize(1);
      assertThat(objectJson(item.content().getFirst()))
          .isEqualTo(
              "{\"url\":\"https://example.com/a.pdf\",\"name\":\"a.pdf\",\"camunda.document.type\":\"external\"}");
    }

    @Test
    void theArgumentsAreTheOnesTheModelSent() {
      final var item =
          item(
              message(
                  AgentDocumentContent.of(AgentDocumentId.of(document)),
                  ObjectContent.objectContent(Map.of("file", otherDocument))),
              agentContext);

      assertThat(item.toolCalls())
          .usingRecursiveComparison()
          .isEqualTo(List.of(historyToolCall("call-1", "fetch", "fetch", call.arguments())));
    }

    @Test
    void withoutTheDocumentsFeatureAToolResultIsReportedAsStored() {
      final var legacy = AgentContext.empty();

      final var bare = item(message(DocumentContent.documentContent(document)), legacy);
      final var nested =
          item(message(ObjectContent.objectContent(Map.of("file", document))), legacy);

      assertThat(bare.content())
          .singleElement()
          .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);
      assertThat(nested.content())
          .singleElement()
          .isInstanceOfSatisfying(
              AgentInstanceHistoryContent.ObjectContent.class,
              object ->
                  assertThat(((Map<?, ?>) object.getObject()).get("file")).isSameAs(document));
    }
  }
}
