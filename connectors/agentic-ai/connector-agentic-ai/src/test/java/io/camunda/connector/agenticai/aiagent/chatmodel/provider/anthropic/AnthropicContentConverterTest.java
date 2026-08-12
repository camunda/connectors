/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.models.messages.Base64ImageSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.document.jackson.DocumentReferenceModel.ExternalDocumentReferenceModel;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AnthropicContentConverterTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JacksonModuleDocumentSerializer());
  private final AnthropicContentConverter converter = new AnthropicContentConverter(objectMapper);

  private static Document mockDocument(String contentType, String base64) {
    final var document = mock(Document.class);
    final var metadata = mock(DocumentMetadata.class);
    when(document.metadata()).thenReturn(metadata);
    when(metadata.getContentType()).thenReturn(contentType);
    when(document.asBase64()).thenReturn(base64);
    // stubbed so a reference-only fallback (tool-result documents) can serialize this mock via
    // JacksonModuleDocumentSerializer, which dispatches on Document#reference()
    when(document.reference())
        .thenReturn(new ExternalDocumentReferenceModel("https://example.com/document", "document"));
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
          .isEqualTo(Base64ImageSource.MediaType.IMAGE_PNG);
    }

    @Test
    void mapsImageDocumentWithContentTypeParametersToBase64ImageBlock() {
      final var doc = mockDocument("image/png; charset=UTF-8", "QUJD");

      final var blocks = converter.toContentBlockParams(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      final var image = blocks.get(0).image().orElseThrow();
      assertThat(image.source().base64().orElseThrow().mediaType())
          .isEqualTo(Base64ImageSource.MediaType.IMAGE_PNG);
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
    void mapsReasoningContentThinkingPayloadToNativeBlockRoundTrip() {
      // The `thinking` text is stored separately in the `text` field, stripped from the payload
      // (see AnthropicMessageResponseConverter), and must be merged back in here.
      final var payload =
          Map.<String, Object>of(
              "type", "thinking",
              "signature", "sig-123");

      final var blocks =
          converter.toContentBlockParams(
              List.of(new ReasoningContent("anthropic", payload, "Let me think it through", null)));

      assertThat(blocks).hasSize(1);
      final var thinking = blocks.get(0).thinking().orElseThrow();
      assertThat(thinking.thinking()).isEqualTo("Let me think it through");
      assertThat(thinking.signature()).isEqualTo("sig-123");
    }

    @Test
    void mapsReasoningContentThinkingPayloadWithoutTextFallsBackToPayloadAsIs() {
      // Older/pre-existing payloads may already contain the `thinking` key with no separate
      // `text` field populated; the converter must not fail or blank it out.
      final var payload =
          Map.<String, Object>of(
              "type", "thinking",
              "thinking", "Let me think it through",
              "signature", "sig-123");

      final var blocks =
          converter.toContentBlockParams(
              List.of(new ReasoningContent("anthropic", payload, null, null)));

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
          converter.toContentBlockParams(
              List.of(new ReasoningContent("anthropic", payload, null, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isRedactedThinking()).isTrue();
      assertThat(blocks.get(0).asRedactedThinking().data()).isEqualTo("encrypted-blob");
    }

    @Test
    void mapsProviderContentPayloadToNativeBlockRoundTrip() {
      // Any Anthropic block shape not otherwise modeled by a domain Content type round-trips
      // byte-identically through ProviderContent; a container-upload block (unrelated to tool
      // calling) stands in here for the mechanism.
      final var payload =
          Map.<String, Object>of("type", "container_upload", "file_id", "file_abc123");

      final var blocks =
          converter.toContentBlockParams(List.of(new ProviderContent("anthropic", payload, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isContainerUpload()).isTrue();
      assertThat(blocks.get(0).asContainerUpload().fileId()).isEqualTo("file_abc123");
    }

    @Test
    void throwsForUnsupportedDocumentContentType() {
      final var doc = mockDocument("application/zip", "UEsDBA==");

      assertThatThrownBy(
              () -> converter.toContentBlockParams(List.of(new DocumentContent(doc, null))))
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("application/zip");
    }

    @Test
    void dropsReasoningContentFromAForeignProvider() {
      final var payload = Map.<String, Object>of("type", "reasoning", "id", "rs_1");

      final var blocks =
          converter.toContentBlockParams(
              List.of(new ReasoningContent("openai", payload, null, null)));

      assertThat(blocks).isEmpty();
    }

    @Test
    void dropsProviderContentFromAForeignProvider() {
      final var payload = Map.<String, Object>of("type", "web_search_call", "id", "ws_1");

      final var blocks =
          converter.toContentBlockParams(List.of(new ProviderContent("openai", payload, null)));

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
    void mapsDocumentContentToTextBlockReference() {
      // never embedded natively here, regardless of content type - the composer's synthetic
      // <doc/> fallback message already delivers the actual bytes for tool results, so this
      // renders the same JSON reference ObjectContent would, avoiding a double-send
      final var doc = mockDocument("image/jpeg", "QUJD");

      final var blocks = converter.toToolResultBlocks(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isText()).isTrue();
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
              List.of(
                  new ReasoningContent(
                      "anthropic", Map.of("thinking", "some reasoning"), null, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).isText()).isTrue();
      assertThat(blocks.get(0).text().orElseThrow().text()).contains("some reasoning");
    }
  }
}
