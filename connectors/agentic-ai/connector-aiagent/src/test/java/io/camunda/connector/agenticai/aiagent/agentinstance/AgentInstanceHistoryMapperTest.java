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
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.document.DocumentTags;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments.NewDocument;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentOrigin;
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
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.InlineDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
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
          Mockito.mock(GatewayToolHandlerRegistry.class),
          TestDocuments.documentRestorer(),
          TestDocuments.objectTagRenderer());

  @Test
  void userMessageUsesThePassedInTurnIngestionTimestamp() {
    final var userMessage =
        UserMessage.builder().content(MessageUtil.singleTextContent("hi")).build();

    final var items =
        mapper.inputHistoryItems(
            userMessage, Map.of(), TURN_INGESTION_TIMESTAMP, NEW_INSTANCE.documentHandling());

    assertThat(items).singleElement().extracting("producedAt").isEqualTo(TURN_INGESTION_TIMESTAMP);
  }

  private static List<String> texts(List<AgentInstanceHistoryContent> content) {
    return content.stream()
        .filter(AgentInstanceHistoryContent.TextContent.class::isInstance)
        .map(c -> ((AgentInstanceHistoryContent.TextContent) c).getText())
        .toList();
  }

  @Test
  void aUserMessageWithADocumentTagIsReportedWithTheTagAndTheDocumentFromTheWorkspace() {
    final var document =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
            .create(
                DocumentCreationRequest.from("content".getBytes(StandardCharsets.UTF_8))
                    .contentType("text/plain")
                    .fileName("report.txt")
                    .build());
    final var workspace =
        AgentDocuments.empty()
            .register(List.of(new NewDocument(document, DocumentOrigin.userMessage())));
    final var tag = AgentDocumentContent.of(AgentDocumentId.of(document));
    final var userMessage =
        UserMessage.builder().content(List.of(TextContent.textContent("see"), tag)).build();

    final var items =
        mapper.inputHistoryItems(
            userMessage,
            Map.of(),
            TURN_INGESTION_TIMESTAMP,
            WITH_DOCUMENTS.withDocuments(workspace).documentHandling());

    assertThat(items)
        .singleElement()
        .satisfies(item -> {
          assertThat(item.content()).hasSize(3);
          assertThat(texts(item.content()))
              .containsExactly("see", "`" + DocumentTags.longTag(workspace, tag.id()) + "`");
          assertThat(item.content().get(2))
              .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);
        });
  }

  @Test
  void aDocumentTagOfAnUnknownDocumentIsReportedAsTextOnly() {
    final var tag =
        new AgentDocumentContent(AgentDocumentId.of("unknown"), DocumentDelivery.REFERENCE, null);
    final var userMessage = UserMessage.builder().content(List.of(tag)).build();

    final var items =
        mapper.inputHistoryItems(
            userMessage, Map.of(), TURN_INGESTION_TIMESTAMP, WITH_DOCUMENTS.documentHandling());

    assertThat(items)
        .singleElement()
        .satisfies(item ->
            assertThat(texts(item.content()))
                .containsExactly("`" + DocumentTags.idOnlyTag(tag.id()) + "`"));
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
            userMessage, Map.of(), TURN_INGESTION_TIMESTAMP, WITH_DOCUMENTS.documentHandling());

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
            WITH_DOCUMENTS.documentHandling());

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
            user, Map.of(), TURN_INGESTION_TIMESTAMP, withoutDocumentFlow.documentHandling());
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
            NEW_INSTANCE.documentHandling());

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
            NEW_INSTANCE.documentHandling());

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
                NEW_INSTANCE.documentHandling()))
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
            NEW_INSTANCE.documentHandling());

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
    private final String tag = DocumentTags.shortTag(document);
    private final String otherTag = DocumentTags.shortTag(otherDocument);

    private final AgentDocuments workspace =
        AgentDocuments.empty()
            .register(
                List.of(
                    new NewDocument(document, DocumentOrigin.toolCall("fetch", "call-1")),
                    new NewDocument(otherDocument, DocumentOrigin.toolCall("fetch", "call-1"))));
    private final AgentContext agentContext = WITH_DOCUMENTS.withDocuments(workspace);

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
              agentContext.documentHandling());
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
    void aBareLiveDocumentIsTheShortTagInInlineCodeFollowedByTheDocument() {
      final var item = item(message(DocumentContent.documentContent(document)), agentContext);

      assertThat(item.role()).isEqualTo(AgentInstanceHistoryRole.TOOL_RESULT);
      assertThat(item.content()).hasSize(2);
      assertThat(texts(item.content())).containsExactly("`" + tag + "`");
      assertThat(tag).doesNotContain("toolName").doesNotContain("toolCallId");
      assertThat(item.content().get(1))
          .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);
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
    void aReferenceBlockBetweenTextsIsTheShortTagInInlineCodeAndTheDocumentInTheOrderOfTheBlocks() {
      // the workspace knows the tool call that returned the document, but the model reads the short
      // tag of a document in a tool result
      final var block = AgentDocumentContent.of(AgentDocumentId.of(document));

      final var item =
          item(
              message(
                  TextContent.textContent("Loaded the document."),
                  block,
                  TextContent.textContent("Its content follows in the next message.")),
              agentContext);

      assertThat(DocumentTags.longTag(workspace, block.id()))
          .contains("toolName")
          .contains("toolCallId");
      assertThat(tag).doesNotContain("toolName").doesNotContain("toolCallId");
      assertThat(item.content()).hasSize(4);
      assertThat(described(item).get(0)).isEqualTo("TEXT Loaded the document.");
      assertThat(described(item).get(1)).isEqualTo("TEXT `" + tag + "`");
      assertThat(item.content().get(2))
          .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);
      assertThat(described(item).get(3)).isEqualTo("TEXT Its content follows in the next message.");
    }

    @Test
    void aTagWrittenInTheTextOfAToolResultIsReportedVerbatimNextToTheReferenceBlock() {
      final var item =
          item(
              message(
                  TextContent.textContent("The tool wrote " + tag),
                  AgentDocumentContent.of(AgentDocumentId.of(otherDocument))),
              agentContext);

      assertThat(texts(item.content()).getFirst()).isEqualTo("The tool wrote " + tag);
      assertThat(texts(item.content()).get(1)).isEqualTo("`" + otherTag + "`");
    }

    @Test
    void aTypedReferenceOfAnUnknownDocumentIsItsTagOnly() {
      final var unknown =
          new AgentDocumentContent(AgentDocumentId.of("unknown"), DocumentDelivery.REFERENCE, null);

      final var item = item(message(unknown), WITH_DOCUMENTS);

      assertThat(item.content()).hasSize(1);
      assertThat(texts(item.content()))
          .containsExactly("`" + DocumentTags.idOnlyTag(unknown.id()) + "`");
    }

    @Test
    void aNestedDocumentIsTheTagStringInTheObjectAndTheDocumentFollowsTheObject() {
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
                          tree(Map.of("status", 200, "file", tag, "meta", Map.of("kind", "pdf")))));
      assertThat(item.content().get(1))
          .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);
    }

    @Test
    void theTagInAnObjectIsNotWrappedInBackticksAndEqualsWhatTheModelSees() throws Exception {
      final var message = message(ObjectContent.objectContent(Map.of("file", document)));
      final var item = item(message, agentContext);

      final var reported =
          ((AgentInstanceHistoryContent.ObjectContent) item.content().getFirst()).getObject();
      assertThat(reported).isEqualTo(Map.of("file", tag));
      final var modelFacing =
          (ToolCallResultMessage)
              TestDocuments.documentExpander()
                  .expand(
                      new ConversationSnapshot(List.of(message), List.of()),
                      new DocumentHandling.Tracked(AgentDocuments.empty()))
                  .messages()
                  .getFirst();
      assertThat(modelFacing.results().getFirst().content())
          .containsExactly(ObjectContent.objectContent(reported));
    }

    @Test
    void aDocumentNestedAtAnyDepthAndInARecordIsReplacedByItsTag() {
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
          .isEqualTo(tree(Map.of("attachments", List.of(Map.of("name", "a", "file", tag)))));
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
      assertThat(texts(item.content())).containsExactly("done", "`" + otherTag + "`");
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
    void anExternalDocumentIsItsTagAndTheExternalReference() {
      final var external = new ExternalDocument("https://example.com/a.pdf", "a.pdf", u -> null);
      final var externalTag = DocumentTags.shortTag(external);

      final var item = item(message(DocumentContent.documentContent(external)), agentContext);

      assertThat(texts(item.content())).containsExactly("`" + externalTag + "`");
      assertThat(item.content()).hasSize(2);
      assertThat(item.content().get(1))
          .isInstanceOf(AgentInstanceHistoryContent.ObjectContent.class);
      assertThat(externalTag).doesNotContain("example.com");
    }

    @Test
    void anInlineDocumentHasItsTagOnly() {
      final var inline = new InlineDocument("{\"a\":1}", "data.json", "application/json");
      final var inlineTag = DocumentTags.shortTag(inline);

      final var bare = item(message(DocumentContent.documentContent(inline)), agentContext);
      final var nested =
          item(message(ObjectContent.objectContent(Map.of("file", inline))), agentContext);

      assertThat(texts(bare.content())).containsExactly("`" + inlineTag + "`");
      assertThat(bare.content()).hasSize(1);
      assertThat(nested.content()).hasSize(1);
      assertThat(
              ((AgentInstanceHistoryContent.ObjectContent) nested.content().getFirst()).getObject())
          .isEqualTo(Map.of("file", inlineTag));
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
