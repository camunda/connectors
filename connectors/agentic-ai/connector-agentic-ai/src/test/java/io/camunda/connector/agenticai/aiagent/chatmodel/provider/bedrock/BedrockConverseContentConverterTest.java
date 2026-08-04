/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.runtime.core.document.InlineDocument;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.model.CachePointBlock;
import software.amazon.awssdk.services.bedrockruntime.model.CachePointType;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentFormat;
import software.amazon.awssdk.services.bedrockruntime.model.ImageFormat;

class BedrockConverseContentConverterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final BedrockConverseContentConverter converter =
      new BedrockConverseContentConverter(objectMapper);

  private static Document inlineDocument(String content, String name, String contentType) {
    return new InlineDocument(content, name, contentType);
  }

  @Nested
  class ToContentBlocks {

    @Test
    void mapsTextContentToTextBlock() {
      final var blocks = converter.toContentBlocks(List.of(new TextContent("hello world", null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).text()).isEqualTo("hello world");
    }

    @Test
    void mapsImageDocumentToImageBlock() {
      final var doc = inlineDocument("fake-png-bytes", "photo.png", "image/png");

      final var blocks = converter.toContentBlocks(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      final var image = blocks.get(0).image();
      assertThat(image).isNotNull();
      assertThat(image.format()).isEqualTo(ImageFormat.PNG);
      assertThat(image.source().bytes().asByteArray())
          .isEqualTo("fake-png-bytes".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void mapsNativeDocumentFormatToDocumentBlockWithBytesSource() {
      final var doc = inlineDocument("fake-pdf-bytes", "report.pdf", "application/pdf");

      final var blocks = converter.toContentBlocks(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      final var document = blocks.get(0).document();
      assertThat(document).isNotNull();
      assertThat(document.format()).isEqualTo(DocumentFormat.PDF);
      assertThat(document.source().bytes().asByteArray())
          .isEqualTo("fake-pdf-bytes".getBytes(StandardCharsets.UTF_8));
      assertThat(document.name()).isEqualTo(DocumentHandle.idFor(doc));
    }

    @Test
    void mapsAnotherNativeDocumentFormatToDocumentBlock() {
      final var doc =
          inlineDocument(
              "fake-docx-bytes",
              "report.docx",
              "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

      final var blocks = converter.toContentBlocks(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).document().format()).isEqualTo(DocumentFormat.DOCX);
    }

    @Test
    void mapsTextIshDocumentToTxtFormatDocumentBlockWithTextSource() {
      final var doc = inlineDocument("{\"key\":\"value\"}", "data.json", "application/json");

      final var blocks = converter.toContentBlocks(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      final var document = blocks.get(0).document();
      assertThat(document).isNotNull();
      assertThat(document.format()).isEqualTo(DocumentFormat.TXT);
      assertThat(document.source().text()).isEqualTo("{\"key\":\"value\"}");
      assertThat(document.name()).isEqualTo(DocumentHandle.idFor(doc));
    }

    @Test
    void documentBlockNameMatchesBedrockAllowedCharset() {
      final var doc = inlineDocument("plain text content", "notes.txt", "text/plain");

      final var blocks = converter.toContentBlocks(List.of(new DocumentContent(doc, null)));

      final var name = blocks.get(0).document().name();
      assertThat(name).isEqualTo(DocumentHandle.idFor(doc));
      assertThat(name).matches("[A-Za-z0-9 ()\\[\\]-]{1,200}");
    }

    @Test
    void mapsObjectContentToJsonTextBlock() {
      final var blocks =
          converter.toContentBlocks(List.of(new ObjectContent(Map.of("key", "value"), null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).text()).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    void throwsForUnsupportedDocumentContentType() {
      final var doc = inlineDocument("zip-bytes", "archive.zip", "application/zip");

      assertThatThrownBy(() -> converter.toContentBlocks(List.of(new DocumentContent(doc, null))))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue("errorCode", "FAILED_MODEL_CALL")
          .hasMessageContaining("application/zip");
    }

    @Test
    void mapsReasoningContentWithTextMergedBackIntoPayload() {
      final var payload = Map.<String, Object>of("reasoningText", Map.of("signature", "sig-123"));

      final var blocks =
          converter.toContentBlocks(
              List.of(
                  new ReasoningContent("bedrock", payload, "Let me think it through", Map.of())));

      assertThat(blocks).hasSize(1);
      final var reasoningContent = blocks.get(0).reasoningContent();
      assertThat(reasoningContent).isNotNull();
      assertThat(reasoningContent.reasoningText().text()).isEqualTo("Let me think it through");
      assertThat(reasoningContent.reasoningText().signature()).isEqualTo("sig-123");
    }

    @Test
    void mapsReasoningContentWithRedactedContentAndNoText() {
      final var redacted =
          java.util.Base64.getEncoder()
              .encodeToString("encrypted-blob".getBytes(StandardCharsets.UTF_8));
      final var payload = Map.<String, Object>of("redactedContent", redacted);

      final var blocks =
          converter.toContentBlocks(List.of(new ReasoningContent("bedrock", payload, null, null)));

      assertThat(blocks).hasSize(1);
      final var reasoningContent = blocks.get(0).reasoningContent();
      assertThat(reasoningContent).isNotNull();
      assertThat(reasoningContent.redactedContent().asUtf8String()).isEqualTo("encrypted-blob");
    }

    @Test
    void throwsWhenReasoningContentPayloadIsNotAMap() {
      assertThatThrownBy(
              () ->
                  converter.toContentBlocks(
                      List.of(new ReasoningContent("bedrock", "not-a-map", null, null))))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue("errorCode", "FAILED_MODEL_CALL");
    }

    @Test
    void mapsProviderContentPayloadToNativeBlockRoundTrip() {
      // Any ContentBlock member beyond text/toolUse/reasoningContent round-trips byte-identically
      // through ProviderContent via the generic codec; cachePoint stands in for the mechanism.
      final var original =
          ContentBlock.fromCachePoint(
              CachePointBlock.builder().type(CachePointType.DEFAULT).build());
      final var payload = BedrockSdkPojoCodec.capture(original);

      final var blocks =
          converter.toContentBlocks(List.of(new ProviderContent("bedrock", payload, null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0)).isEqualTo(original);
    }

    @Test
    void throwsWhenProviderContentPayloadIsNotAMap() {
      assertThatThrownBy(
              () ->
                  converter.toContentBlocks(
                      List.of(new ProviderContent("bedrock", "not-a-map", null))))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue("errorCode", "FAILED_MODEL_CALL");
    }

    @Test
    void mapsMultipleContentItemsInOrder() {
      final var doc = inlineDocument("fake-png-bytes", "photo.png", "image/png");
      final List<Content> content =
          List.of(new TextContent("first", null), new DocumentContent(doc, null));

      final var blocks = converter.toContentBlocks(content);

      assertThat(blocks).hasSize(2);
      assertThat(blocks.get(0).text()).isEqualTo("first");
      assertThat(blocks.get(1).image()).isNotNull();
    }
  }

  @Nested
  class ToToolResultBlocks {

    @Test
    void mapsTextContentToTextBlock() {
      final var blocks = converter.toToolResultBlocks(List.of(new TextContent("hello", null)));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.get(0).text()).isEqualTo("hello");
    }

    @Test
    void mapsImageDocumentToImageBlock() {
      final var doc = inlineDocument("fake-jpeg-bytes", "photo.jpg", "image/jpeg");

      final var blocks = converter.toToolResultBlocks(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      final var image = blocks.get(0).image();
      assertThat(image).isNotNull();
      assertThat(image.format()).isEqualTo(ImageFormat.JPEG);
      assertThat(image.source().bytes().asByteArray())
          .isEqualTo("fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void mapsNativeDocumentFormatToDocumentBlock() {
      final var doc = inlineDocument("fake-pdf-bytes", "report.pdf", "application/pdf");

      final var blocks = converter.toToolResultBlocks(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      final var document = blocks.get(0).document();
      assertThat(document).isNotNull();
      assertThat(document.format()).isEqualTo(DocumentFormat.PDF);
      assertThat(document.name()).isEqualTo(DocumentHandle.idFor(doc));
    }

    @Test
    void mapsTextIshDocumentToTxtFormatDocumentBlock() {
      final var doc = inlineDocument("plain text content", "notes.txt", "text/plain");

      final var blocks = converter.toToolResultBlocks(List.of(new DocumentContent(doc, null)));

      assertThat(blocks).hasSize(1);
      final var document = blocks.get(0).document();
      assertThat(document).isNotNull();
      assertThat(document.format()).isEqualTo(DocumentFormat.TXT);
    }

    @Test
    void mapsObjectContentToNativeJsonBlock() {
      final var blocks =
          converter.toToolResultBlocks(List.of(new ObjectContent(Map.of("a", 1), null)));

      assertThat(blocks).hasSize(1);
      final var json = blocks.get(0).json();
      assertThat(json).isNotNull();
      assertThat(json.isMap()).isTrue();
      assertThat(json.asMap().get("a").asNumber().intValue()).isEqualTo(1);
    }

    @Test
    void mapsNestedObjectContentToNativeJsonBlock() {
      final var blocks =
          converter.toToolResultBlocks(
              List.of(
                  new ObjectContent(
                      Map.of("items", List.of("a", "b"), "count", 2, "ok", true), null)));

      assertThat(blocks).hasSize(1);
      final var json = blocks.get(0).json();
      assertThat(json).isNotNull();
      assertThat(json.asMap().get("items").asList())
          .extracting(software.amazon.awssdk.core.document.Document::asString)
          .containsExactly("a", "b");
      assertThat(json.asMap().get("count").asNumber().intValue()).isEqualTo(2);
      assertThat(json.asMap().get("ok").asBoolean()).isTrue();
    }

    @Test
    void throwsForUnsupportedDocumentContentType() {
      final var doc = inlineDocument("zip-bytes", "archive.zip", "application/zip");

      assertThatThrownBy(
              () -> converter.toToolResultBlocks(List.of(new DocumentContent(doc, null))))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue("errorCode", "FAILED_MODEL_CALL")
          .hasMessageContaining("application/zip");
    }

    @Test
    void throwsForReasoningContent() {
      assertThatThrownBy(
              () ->
                  converter.toToolResultBlocks(
                      List.of(
                          new ReasoningContent(
                              "bedrock", Map.of("reasoningText", Map.of()), null, null))))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue("errorCode", "FAILED_MODEL_CALL");
    }

    @Test
    void throwsForProviderContent() {
      assertThatThrownBy(
              () ->
                  converter.toToolResultBlocks(
                      List.of(
                          new ProviderContent("bedrock", Map.of("cachePoint", Map.of()), null))))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue("errorCode", "FAILED_MODEL_CALL");
    }
  }
}
