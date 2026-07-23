/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.models.beta.messages.BetaBase64ImageSource;
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
      final var blocks = converter.toContentBlockParams(List.of(new ReasoningContent(null, null)));

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
          converter.toContentBlockParams(List.of(new ReasoningContent(payload, null)));

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
          converter.toContentBlockParams(List.of(new ReasoningContent(payload, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isRedactedThinking()).isTrue();
      assertThat(blocks.get(0).asRedactedThinking().data()).isEqualTo("encrypted-blob");
    }

    @Test
    void mapsProviderContentPayloadToNativeBlockRoundTrip() {
      // Any Anthropic block shape not otherwise modeled by a domain Content type round-trips
      // byte-identically through ProviderContent; a context-management block (unrelated to tool
      // calling) stands in here for the mechanism.
      final var payload =
          Map.<String, Object>of("type", "compaction", "content", "Summary of earlier turns");

      final var blocks =
          converter.toContentBlockParams(
              List.of(new ProviderContent("anthropic", "compaction", payload, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isCompaction()).isTrue();
      assertThat(blocks.get(0).asCompaction().content()).contains("Summary of earlier turns");
    }

    @Test
    void skipsProviderContentWithNullPayload() {
      final var blocks =
          converter.toContentBlockParams(
              List.of(new ProviderContent("anthropic", "compaction", null, null)));

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
          converter.toToolResultBlocks(
              List.of(new ReasoningContent(Map.of("thinking", "some reasoning"), null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isText()).isTrue();
      assertThat(blocks.get(0).text().orElseThrow().text()).contains("some reasoning");
    }
  }
}
