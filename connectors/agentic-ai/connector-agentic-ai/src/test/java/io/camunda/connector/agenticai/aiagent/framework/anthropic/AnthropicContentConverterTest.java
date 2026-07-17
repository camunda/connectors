/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.models.beta.messages.BetaBase64ImageSource;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AnthropicContentConverterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AnthropicContentConverter converter = new AnthropicContentConverter(objectMapper);

  private static Document mockDocument(String contentType, String base64) {
    final var document = mock(Document.class);
    final var metadata = mock(DocumentMetadata.class);
    when(document.metadata()).thenReturn(metadata);
    when(metadata.getContentType()).thenReturn(contentType);
    when(document.asBase64()).thenReturn(base64);
    return document;
  }

  @Nested
  class ToContentBlockParams {

    @Test
    void mapsTextContentToTextBlock() {
      final var blocks =
          converter.toContentBlockParams(List.of(new TextContent("hello world", null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isText()).isTrue();
      assertThat(blocks.get(0).text().orElseThrow().text()).isEqualTo("hello world");
    }

    @Test
    void mapsImageDocumentToBase64ImageBlock() {
      final var doc = mockDocument("image/png", "QUJD");

      final var blocks = converter.toContentBlockParams(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      final var image = blocks.get(0).image().orElseThrow();
      assertThat(image.source().base64().orElseThrow().data()).isEqualTo("QUJD");
      assertThat(image.source().base64().orElseThrow().mediaType())
          .isEqualTo(BetaBase64ImageSource.MediaType.IMAGE_PNG);
    }

    @Test
    void mapsPdfDocumentToDocumentBlock() {
      final var doc = mockDocument("application/pdf", "UERGQ09OVEVOVA==");

      final var blocks = converter.toContentBlockParams(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      final var document = blocks.get(0).document().orElseThrow();
      assertThat(document.source().isBase64()).isTrue();
      assertThat(document.source().asBase64().data()).isEqualTo("UERGQ09OVEVOVA==");
    }

    @Test
    void mapsTextDocumentToPlainTextSourceDocumentBlock() {
      final var doc = mock(Document.class);
      final var metadata = mock(DocumentMetadata.class);
      when(doc.metadata()).thenReturn(metadata);
      when(metadata.getContentType()).thenReturn("text/plain");
      when(doc.asByteArray()).thenReturn("plain text content".getBytes(StandardCharsets.UTF_8));

      final var blocks = converter.toContentBlockParams(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      final var document = blocks.get(0).document().orElseThrow();
      assertThat(document.source().isText()).isTrue();
      assertThat(document.source().asText().data()).isEqualTo("plain text content");
    }

    @Test
    void mapsObjectContentToJsonTextBlock() {
      final var blocks =
          converter.toContentBlockParams(List.of(new ObjectContent(Map.of("key", "value"), null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isText()).isTrue();
      assertThat(blocks.get(0).text().orElseThrow().text()).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    void skipsReasoningContentWithNullProviderPayload() {
      // e.g. reasoning content produced by the LangChain4J-routed path, which never populates
      // providerPayload; there is no raw block to replay, so it is skipped rather than dropped
      // silently as a null content block.
      final var blocks =
          converter.toContentBlockParams(
              List.of(new ReasoningContent("some reasoning", null, null)));

      assertThat(blocks).isEmpty();
    }

    @Test
    void mapsReasoningContentThinkingPayloadToNativeBlockRoundTrip() {
      final var payload =
          Map.<String, Object>of(
              "type", "thinking",
              "thinking", "Let me think it through",
              "signature", "sig-123");

      final var blocks =
          converter.toContentBlockParams(
              List.of(new ReasoningContent("Let me think it through", payload, null)));

      assertThat(blocks).hasSize(1);
      final var thinking = blocks.get(0).thinking().orElseThrow();
      assertThat(thinking.thinking()).isEqualTo("Let me think it through");
      assertThat(thinking.signature()).isEqualTo("sig-123");
    }

    @Test
    void mapsReasoningContentRedactedThinkingPayloadToNativeBlockRoundTrip() {
      final var payload =
          Map.<String, Object>of("type", "redacted_thinking", "data", "encrypted-blob");

      final var blocks =
          converter.toContentBlockParams(List.of(new ReasoningContent(null, payload, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isRedactedThinking()).isTrue();
      assertThat(blocks.get(0).asRedactedThinking().data()).isEqualTo("encrypted-blob");
    }

    @Test
    void mapsProviderContentPayloadToNativeBlockRoundTrip() {
      final var payload =
          Map.<String, Object>of(
              "type",
              "server_tool_use",
              "id",
              "srvtoolu_01ABC",
              "name",
              "code_execution",
              "input",
              Map.of("code", "print('hi')"));

      final var blocks =
          converter.toContentBlockParams(
              List.of(new ProviderContent("anthropic", "server_tool_use", payload, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isServerToolUse()).isTrue();
      assertThat(blocks.get(0).asServerToolUse().id()).isEqualTo("srvtoolu_01ABC");
    }

    @Test
    void mapsContainerUploadProviderContentToNativeBlockRoundTrip() {
      final var payload =
          Map.<String, Object>of("type", "container_upload", "file_id", "file_01ABC");

      final var blocks =
          converter.toContentBlockParams(
              List.of(new ProviderContent("anthropic", "container_upload", payload, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isContainerUpload()).isTrue();
      assertThat(blocks.get(0).asContainerUpload().fileId()).isEqualTo("file_01ABC");
    }

    @Test
    void mapsWebSearchToolResultProviderContentToNativeBlockRoundTrip() {
      final var payload =
          Map.<String, Object>of(
              "type",
              "web_search_tool_result",
              "tool_use_id",
              "srvtoolu_01DEF",
              "content",
              List.of(
                  Map.of(
                      "type", "web_search_result",
                      "title", "Example",
                      "url", "https://example.com",
                      "encrypted_content", "abc123")));

      final var blocks =
          converter.toContentBlockParams(
              List.of(new ProviderContent("anthropic", "web_search_tool_result", payload, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isWebSearchToolResult()).isTrue();
      assertThat(blocks.get(0).asWebSearchToolResult().toolUseId()).isEqualTo("srvtoolu_01DEF");
    }

    @Test
    void survivesNumericPayloadCoercionAcrossPersistenceHop() throws Exception {
      final var payload =
          Map.<String, Object>of(
              "type",
              "code_execution_tool_result",
              "tool_use_id",
              "srvtoolu_01GHI",
              "content",
              Map.of(
                  "type", "code_execution_result",
                  "return_code", 0,
                  "stdout", "",
                  "stderr", "",
                  "content", List.of()));

      // Simulate conversation-memory persistence: a plain Jackson ObjectMapper restores
      // numeric fields as Integer, not Long, unlike the SDK's own JSON mapper.
      final var persistenceMapper = new ObjectMapper();
      final var json = persistenceMapper.writeValueAsString(payload);
      final Map<String, Object> restoredPayload =
          persistenceMapper.readValue(json, new TypeReference<>() {});

      final var blocks =
          converter.toContentBlockParams(
              List.of(
                  new ProviderContent(
                      "anthropic", "code_execution_tool_result", restoredPayload, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isCodeExecutionToolResult()).isTrue();
      assertThat(blocks.get(0).asCodeExecutionToolResult().toolUseId()).isEqualTo("srvtoolu_01GHI");
    }

    @Test
    void skipsProviderContentWithNullPayload() {
      final var blocks =
          converter.toContentBlockParams(
              List.of(new ProviderContent("anthropic", "server_tool_use", null, null)));

      assertThat(blocks).isEmpty();
    }

    @Test
    void mapsMultipleContentItemsInOrder() {
      final var doc = mockDocument("image/png", "QUJD");
      final List<Content> content =
          List.of(new TextContent("first", null), new DocumentContent(doc, null));

      final var blocks = converter.toContentBlockParams(content);

      assertThat(blocks).hasSize(2);
      assertThat(blocks.get(0).isText()).isTrue();
      assertThat(blocks.get(1).isImage()).isTrue();
    }
  }

  @Nested
  class ToToolResultBlocks {

    @Test
    void mapsTextContentToTextBlock() {
      final var blocks = converter.toToolResultBlocks(List.of(new TextContent("hello", null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isText()).isTrue();
      assertThat(blocks.get(0).text().orElseThrow().text()).isEqualTo("hello");
    }

    @Test
    void mapsImageDocumentToImageBlock() {
      final var doc = mockDocument("image/jpeg", "QUJD");

      final var blocks = converter.toToolResultBlocks(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isImage()).isTrue();
      assertThat(blocks.get(0).image().orElseThrow().source().base64().orElseThrow().data())
          .isEqualTo("QUJD");
    }

    @Test
    void mapsPdfDocumentToDocumentBlock() {
      final var doc = mockDocument("application/pdf", "UERGQ09OVEVOVA==");

      final var blocks = converter.toToolResultBlocks(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isDocument()).isTrue();
      assertThat(blocks.get(0).document().orElseThrow().source().asBase64().data())
          .isEqualTo("UERGQ09OVEVOVA==");
    }

    @Test
    void mapsObjectContentToTextBlock() {
      final var blocks =
          converter.toToolResultBlocks(List.of(new ObjectContent(Map.of("a", 1), null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isText()).isTrue();
      assertThat(blocks.get(0).text().orElseThrow().text()).isEqualTo("{\"a\":1}");
    }

    @Test
    void mapsReasoningContentToJsonTextBlockFallback() {
      final var blocks =
          converter.toToolResultBlocks(List.of(new ReasoningContent("some reasoning", null, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isText()).isTrue();
      assertThat(blocks.get(0).text().orElseThrow().text()).contains("some reasoning");
    }
  }
}
