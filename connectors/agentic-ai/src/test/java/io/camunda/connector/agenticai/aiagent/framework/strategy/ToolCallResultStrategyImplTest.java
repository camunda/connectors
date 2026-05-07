/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.strategy;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static io.camunda.connector.agenticai.model.message.content.DocumentContent.documentContent;
import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.agent.ToolCallResultDocumentExtractor;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ToolCallResultStrategyImplTest {

  private static final InMemoryDocumentStore DOCUMENT_STORE = InMemoryDocumentStore.INSTANCE;
  private static final DocumentFactoryImpl DOCUMENT_FACTORY =
      new DocumentFactoryImpl(DOCUMENT_STORE);

  private static final ModelCapabilities INLINE_IMAGE_PDF =
      capabilities(
          List.of(Modality.TEXT, Modality.IMAGE, Modality.PDF),
          List.of(Modality.TEXT, Modality.IMAGE, Modality.PDF));
  private static final ModelCapabilities INLINE_IMAGE_ONLY =
      capabilities(List.of(Modality.TEXT, Modality.IMAGE), List.of(Modality.TEXT, Modality.IMAGE));
  private static final ModelCapabilities TEXT_ONLY =
      capabilities(List.of(Modality.TEXT), List.of(Modality.TEXT));

  private GatewayToolHandlerRegistry gatewayToolHandlers;
  private ToolCallResultStrategyImpl strategy;

  @BeforeEach
  void setUp() {
    DOCUMENT_STORE.clear();
    gatewayToolHandlers = Mockito.mock(GatewayToolHandlerRegistry.class);
    Mockito.when(gatewayToolHandlers.handlerForToolDefinition(Mockito.any()))
        .thenReturn(java.util.Optional.empty());
    strategy =
        new ToolCallResultStrategyImpl(new ToolCallResultDocumentExtractor(gatewayToolHandlers));
  }

  @Nested
  class ToolResultRouting {

    @Test
    void inlineImageStaysOnContentBlocksAndProducesNoSyntheticMessage() {
      final var doc = createDocument("img-bytes", "image/png", "img.png");
      final var request = requestWithToolResultDocument("call-1", "lookup", doc);

      final var result = strategy.apply(request, INLINE_IMAGE_PDF);

      assertThat(result.syntheticContextMessages()).isEmpty();
      final var rewrittenResult = singleResult(result.request());
      assertThat(rewrittenResult.contentBlocks()).containsExactly(documentContent(doc));
    }

    @Test
    void unsupportedPdfFallsBackToSyntheticUserMessage() {
      final var doc = createDocument("pdf-bytes", "application/pdf", "report.pdf");
      final var request = requestWithToolResultDocument("call-1", "lookup", doc);

      final var result = strategy.apply(request, INLINE_IMAGE_ONLY);

      assertThat(result.syntheticContextMessages()).hasSize(1);
      final var synthetic = result.syntheticContextMessages().getFirst();
      assertThat(synthetic.metadata())
          .containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
      assertThat(synthetic.content())
          .anySatisfy(c -> assertThat(c).isEqualTo(documentContent(doc)));

      final var rewrittenResult = singleResult(result.request());
      // No inline routing happened.
      assertThat(rewrittenResult.contentBlocks()).isNullOrEmpty();
    }

    @Test
    void mixedDocumentsAreSplitInOnePass() {
      final var image = createDocument("img-bytes", "image/png", "img.png");
      final var pdf = createDocument("pdf-bytes", "application/pdf", "report.pdf");
      final var request = requestWithToolResultDocuments("call-1", "lookup", image, pdf);

      final var result = strategy.apply(request, INLINE_IMAGE_ONLY);

      // image inline, pdf in synthetic
      final var rewrittenResult = singleResult(result.request());
      assertThat(rewrittenResult.contentBlocks()).containsExactly(documentContent(image));

      assertThat(result.syntheticContextMessages()).hasSize(1);
      final var synthetic = result.syntheticContextMessages().getFirst();
      assertThat(synthetic.content())
          .filteredOn(DocumentContent.class::isInstance)
          .extracting(c -> ((DocumentContent) c).document())
          .containsExactly(pdf);
    }

    @Test
    void syntheticMessageIsInsertedRightAfterToolResultMessageInRequest() {
      final var pdf = createDocument("pdf-bytes", "application/pdf", "r.pdf");
      final var toolResultMessage = toolResultMessage("call-1", "lookup", pdf);
      final var followingUserMessage = userMessage("follow-up event");
      final var initial =
          new ChatRequest(
              List.of(assistantMessage("call lookup"), toolResultMessage, followingUserMessage),
              List.of(),
              null);

      final var result = strategy.apply(initial, INLINE_IMAGE_ONLY);

      // Order: AssistantMessage, ToolCallResultMessage, syntheticUM, followingUserMessage
      assertThat(result.request().messages())
          .satisfiesExactly(
              m ->
                  assertThat(m)
                      .isInstanceOf(
                          io.camunda.connector.agenticai.model.message.AssistantMessage.class),
              m -> assertThat(m).isInstanceOf(ToolCallResultMessage.class),
              m -> {
                assertThat(m).isInstanceOf(UserMessage.class);
                assertThat(((UserMessage) m).metadata())
                    .containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
              },
              m -> assertThat(m).isEqualTo(followingUserMessage));
    }

    @Test
    void noDocumentsLeavesRequestUnchangedAndProducesNoSynthetic() {
      final var request =
          new ChatRequest(
              List.of(
                  assistantMessage("call lookup"),
                  ToolCallResultMessage.builder()
                      .results(
                          List.of(
                              ToolCallResult.builder()
                                  .id("call-1")
                                  .name("lookup")
                                  .content("plain text result")
                                  .build()))
                      .metadata(Map.of())
                      .build()),
              List.of(),
              null);

      final var result = strategy.apply(request, INLINE_IMAGE_PDF);

      assertThat(result.syntheticContextMessages()).isEmpty();
      assertThat(singleResult(result.request()).contentBlocks()).isNullOrEmpty();
    }
  }

  @Nested
  class UserMessageValidation {

    @Test
    void supportedDocumentsPassThroughUnchanged() {
      final var img = createDocument("img-bytes", "image/png", "img.png");
      final var userMsg =
          UserMessage.builder()
              .content(List.of(textContent("look at this"), documentContent(img)))
              .build();
      final var request = new ChatRequest(List.of(userMsg), List.of(), null);

      final var result = strategy.apply(request, INLINE_IMAGE_PDF);

      assertThat(result.request().messages()).containsExactly(userMsg);
    }

    @Test
    void unsupportedDocumentInUserMessageThrows() {
      final var pdf = createDocument("pdf-bytes", "application/pdf", "report.pdf");
      final var userMsg =
          UserMessage.builder().content(List.of(textContent("look"), documentContent(pdf))).build();
      final var request = new ChatRequest(List.of(userMsg), List.of(), null);

      assertThatThrownBy(() -> strategy.apply(request, INLINE_IMAGE_ONLY))
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("PDF")
          .hasMessageContaining("does not");
    }

    @Test
    void textOnlyUserMessageOnTextOnlyModelPassesThrough() {
      final var userMsg = userMessage("hello");
      final var request = new ChatRequest(List.of(userMsg), List.of(), null);

      final var result = strategy.apply(request, TEXT_ONLY);

      assertThat(result.request().messages()).containsExactly(userMsg);
      assertThat(result.syntheticContextMessages()).isEmpty();
    }
  }

  // ------- helpers -------

  private static ModelCapabilities capabilities(
      List<Modality> userMessage, List<Modality> toolResult) {
    return new ModelCapabilities(
        userMessage, toolResult, List.of(Modality.TEXT), false, false, false, false, null, null);
  }

  private static Document createDocument(String content, String contentType, String filename) {
    return DOCUMENT_FACTORY.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(filename)
            .build());
  }

  private static ChatRequest requestWithToolResultDocument(
      String callId, String toolName, Document doc) {
    return requestWithToolResultDocuments(callId, toolName, doc);
  }

  private static ChatRequest requestWithToolResultDocuments(
      String callId, String toolName, Document... docs) {
    return new ChatRequest(
        List.of(assistantMessage("call " + toolName), toolResultMessage(callId, toolName, docs)),
        List.<ToolDefinition>of(),
        null);
  }

  private static ToolCallResultMessage toolResultMessage(
      String callId, String toolName, Document... docs) {
    final Object content =
        docs.length == 1
            ? Map.<String, Object>of("file", docs[0])
            : Map.<String, Object>of("files", List.of((Object[]) docs));
    return ToolCallResultMessage.builder()
        .results(
            List.of(ToolCallResult.builder().id(callId).name(toolName).content(content).build()))
        .metadata(Map.of())
        .build();
  }

  private static ToolCallResult singleResult(ChatRequest request) {
    for (Message m : request.messages()) {
      if (m instanceof ToolCallResultMessage trm) {
        assertThat(trm.results()).hasSize(1);
        return trm.results().getFirst();
      }
    }
    throw new AssertionError("No ToolCallResultMessage in request");
  }
}
