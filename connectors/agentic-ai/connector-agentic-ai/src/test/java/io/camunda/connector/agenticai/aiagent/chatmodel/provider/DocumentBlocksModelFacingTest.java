/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.TestMessagesFixture;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConverseContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiContentRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolCallConverterImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.CompletionsRequestSpec;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsContentChunkStrategy;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsContentDialect;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsRequestConverter;
import io.camunda.connector.agenticai.aiagent.document.DocumentExpander;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiRequestCustomizations;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentBlocksModelFacingTest {

  private static final ObjectMapper OBJECT_MAPPER = TestObjectMapperSupplier.INSTANCE;

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  private final Document report = create("report.txt", "text/plain");
  private final Document archive = create("archive.pdf", "application/pdf");

  private Document create(String fileName, String contentType) {
    return documentFactory.create(
        DocumentCreationRequest.from(fileName.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(fileName)
            .build());
  }

  private List<Message> windowed() {
    final var registry =
        DocumentRegistry.empty()
            .withDocuments(
                List.of(
                    new IngestedDocument(report, DocumentSource.userMessage("evicted")),
                    new IngestedDocument(
                        archive, DocumentSource.toolCall("search", "call-0", "evicted"))));
    final var call =
        new ToolCall("call-1", "c8__get_document", Map.of("id", DocumentHandle.idFor(report)));
    final var previous =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("loading", List.of(call))));
    final var answer =
        ToolCallResultMessage.builder()
            .results(
                List.of(
                    ToolCallResultContent.builder()
                        .id("call-1")
                        .name("c8__get_document")
                        .content(
                            List.<Content>of(
                                textContent("Loaded the document."),
                                DocumentReferenceContent.from(
                                    registry.entries().getFirst(), DocumentDelivery.REFERENCE),
                                textContent("Its content follows in the next message.")))
                        .build()))
            .build();
    final var configuration =
        new AgentConfiguration(
            TestMessagesFixture.TEST_CHAT_MODEL,
            TestMessagesFixture.TEST_SYSTEM_PROMPT,
            null,
            null,
            null,
            null,
            null);
    final var context =
        AgentContext.builder()
            .state(AgentState.READY)
            .features(AgentContext.newInstanceFeatures())
            .conversation(InProcessConversationContext.builder("conv").build())
            .build()
            .withDocuments(registry.entries());
    return AgentConversation.rehydrate(configuration, context, previous, null, List.of(answer))
        .window(100, new DocumentExpander(OBJECT_MAPPER))
        .messages();
  }

  private List<Content> toolResultContent(List<Message> messages) {
    return messages.stream()
        .filter(ToolCallResultMessage.class::isInstance)
        .map(ToolCallResultMessage.class::cast)
        .findFirst()
        .orElseThrow()
        .results()
        .getFirst()
        .content();
  }

  private List<Content> noteContent(List<Message> messages) {
    return ((UserMessage) messages.getLast()).content();
  }

  private static JsonNode asJson(ObjectMapper mapper, Object value) {
    return mapper.valueToTree(value);
  }

  private String loadedTag() {
    return "<doc id=\"%s\" fileName=\"report.txt\" contentType=\"text/plain\" />"
        .formatted(DocumentHandle.idFor(report));
  }

  private String evictedTag() {
    return ("<doc id=\"%s\" fileName=\"archive.pdf\" contentType=\"application/pdf\""
            + " toolName=\"search\" toolCallId=\"call-0\" />")
        .formatted(DocumentHandle.idFor(archive));
  }

  private static final String NOTE_PREAMBLE =
      "Documents from earlier messages that are no longer part of this conversation context. They are still available by id:";

  @Test
  void theWindowedConversationKeepsTheToolResultPartsSeparateAndEndsWithTheNote() {
    final var messages = windowed();

    assertThat(messages)
        .extracting(message -> message.getClass().getSimpleName())
        .containsExactly("UserMessage", "AssistantMessage", "ToolCallResultMessage", "UserMessage");
    assertThat(toolResultContent(messages)).hasSize(3);
    assertThat(noteContent(messages)).hasSize(2);
  }

  @Test
  void anthropicSendsTheTextsAndTheDocumentTagAsSeparateBlocks() throws Exception {
    final var messages = windowed();
    final var converter = new AnthropicContentConverter(OBJECT_MAPPER);
    final var json = com.anthropic.core.ObjectMappers.jsonMapper();

    assertThat(asJson(json, converter.toToolResultBlocks(toolResultContent(messages))))
        .isEqualTo(
            OBJECT_MAPPER.readTree(
                OBJECT_MAPPER.writeValueAsString(
                    List.of(
                        Map.of("type", "text", "text", "Loaded the document."),
                        Map.of("type", "text", "text", loadedTag()),
                        Map.of(
                            "type", "text", "text", "Its content follows in the next message.")))));
    assertThat(asJson(json, converter.toContentBlockParams(noteContent(messages))))
        .isEqualTo(
            OBJECT_MAPPER.readTree(
                OBJECT_MAPPER.writeValueAsString(
                    List.of(
                        Map.of("type", "text", "text", NOTE_PREAMBLE),
                        Map.of("type", "text", "text", evictedTag())))));
  }

  @Test
  void bedrockSendsTheTextsAndTheDocumentTagAsSeparateBlocks() {
    final var messages = windowed();
    final var converter = new BedrockConverseContentConverter(OBJECT_MAPPER);

    assertThat(converter.toToolResultBlocks(toolResultContent(messages)))
        .extracting(block -> block.text())
        .containsExactly(
            "Loaded the document.", loadedTag(), "Its content follows in the next message.");
    assertThat(converter.toContentBlocks(noteContent(messages)))
        .extracting(block -> block.text())
        .containsExactly(NOTE_PREAMBLE, evictedTag());
  }

  @Test
  void geminiSendsTheTextsAndTheDocumentTagAsOneFunctionResponseOutputList() throws Exception {
    final var snapshot = new ConversationSnapshot(windowed(), List.of());

    final var contents =
        new GeminiContentRequestConverter(new GeminiContentConverter(OBJECT_MAPPER))
            .toContents(snapshot);

    assertThat(contents).hasSize(4);
    assertThat(OBJECT_MAPPER.readTree(contents.get(2).toJson()))
        .isEqualTo(
            OBJECT_MAPPER.readTree(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        "role",
                        "user",
                        "parts",
                        List.of(
                            Map.of(
                                "functionResponse",
                                Map.of(
                                    "id",
                                    "call-1",
                                    "name",
                                    "c8__get_document",
                                    "response",
                                    Map.of(
                                        "output",
                                        List.of(
                                            "Loaded the document.",
                                            loadedTag(),
                                            "Its content follows in the next message.")))))))));
    assertThat(OBJECT_MAPPER.readTree(contents.get(3).toJson()))
        .isEqualTo(
            OBJECT_MAPPER.readTree(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        "role",
                        "user",
                        "parts",
                        List.of(Map.of("text", NOTE_PREAMBLE), Map.of("text", evictedTag()))))));
  }

  @Test
  void openAiResponsesSendsTheTextsAndTheDocumentTagAsSeparateInputTexts() throws Exception {
    final var messages = windowed();
    final var converter = new OpenAiContentConverter(OBJECT_MAPPER);
    final var json = com.openai.core.ObjectMappers.jsonMapper();

    assertThat(
            asJson(json, converter.toResponsesToolResultOutputItems(toolResultContent(messages))))
        .isEqualTo(
            OBJECT_MAPPER.readTree(
                OBJECT_MAPPER.writeValueAsString(
                    List.of(
                        Map.of("type", "input_text", "text", "Loaded the document."),
                        Map.of("type", "input_text", "text", loadedTag()),
                        Map.of(
                            "type",
                            "input_text",
                            "text",
                            "Its content follows in the next message.")))));
    assertThat(asJson(json, converter.toResponsesContentParts(noteContent(messages))))
        .isEqualTo(
            OBJECT_MAPPER.readTree(
                OBJECT_MAPPER.writeValueAsString(
                    List.of(
                        Map.of("type", "input_text", "text", NOTE_PREAMBLE),
                        Map.of("type", "input_text", "text", evictedTag())))));
  }

  @Test
  void openAiCompletionsJoinsTheToolResultPartsWithNewlinesAndSendsTheNoteAsTextParts()
      throws Exception {
    final var mapper = new ObjectMapper().registerModule(new JacksonModuleDocumentSerializer());
    final var converter =
        new OpenAiCompletionsRequestConverter(
            "openai",
            new OpenAiContentConverter(mapper),
            OpenAiCompletionsContentChunkStrategy.openAi(mapper),
            mapper,
            OpenAiCompletionsContentDialect.none());

    final var params =
        converter.toRequest(
            new CompletionsRequestSpec(
                "gpt-4o",
                null,
                null,
                null,
                null,
                null,
                new OpenAiRequestCustomizations(null, null, null)),
            null,
            new ConversationSnapshot(windowed(), List.of()));

    final JsonNode body = com.openai.core.ObjectMappers.jsonMapper().valueToTree(params._body());
    final var messages = body.path("messages");
    assertThat(messages).hasSize(4);
    assertThat(messages.get(2).path("role").asText()).isEqualTo("tool");
    assertThat(messages.get(2).path("content").asText())
        .isEqualTo(
            "Loaded the document.\n" + loadedTag() + "\nIts content follows in the next message.");
    assertThat(messages.get(3))
        .isEqualTo(
            OBJECT_MAPPER.readTree(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        "role",
                        "user",
                        "content",
                        List.of(
                            Map.of("type", "text", "text", NOTE_PREAMBLE),
                            Map.of("type", "text", "text", evictedTag()))))));
  }

  @Test
  void langChain4jJoinsTheToolResultPartsWithNewlines() {
    final var result =
        ((ToolCallResultMessage)
                windowed().stream()
                    .filter(ToolCallResultMessage.class::isInstance)
                    .findFirst()
                    .orElseThrow())
            .results()
            .getFirst();

    assertThat(new ToolCallConverterImpl(OBJECT_MAPPER).asToolExecutionResultMessage(result).text())
        .isEqualTo(
            "Loaded the document.\n" + loadedTag() + "\nIts content follows in the next message.");
  }
}
