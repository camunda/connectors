/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.FunctionCall;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.error.ConnectorException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GeminiContentConverterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final GeminiContentConverter converter = new GeminiContentConverter(objectMapper);

  private static Document mockDocument(String contentType, byte[] bytes) {
    final var document = mock(Document.class);
    final var metadata = mock(DocumentMetadata.class);
    when(document.metadata()).thenReturn(metadata);
    when(metadata.getContentType()).thenReturn(contentType);
    when(document.asByteArray()).thenReturn(bytes);
    return document;
  }

  @Nested
  class ToParts {

    @Test
    void mapsTextContentToTextPart() {
      final var parts = converter.toParts(List.of(new TextContent("hello world", null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).text()).contains("hello world");
      assertThat(parts.get(0).thoughtSignature()).isEmpty();
    }

    @Test
    void mapsTextContentWithSignatureMetadataToTextPartCarryingTheSignature() {
      // Gemini attaches a thoughtSignature to whichever part carries the reasoning continuity,
      // including a plain answer text part - it must survive the replay, not just on thought parts.
      final var signatureBytes = "sig-bytes".getBytes(StandardCharsets.UTF_8);
      final var metadata =
          Map.<String, Object>of(
              "thoughtSignature", Base64.getEncoder().encodeToString(signatureBytes));

      final var parts = converter.toParts(List.of(new TextContent("the answer", metadata)));

      assertThat(parts).hasSize(1);
      final var part = parts.get(0);
      assertThat(part.text()).contains("the answer");
      assertThat(part.thought()).isEmpty();
      assertThat(part.thoughtSignature().orElseThrow()).isEqualTo(signatureBytes);
    }

    @Test
    void mapsTextContentWithRawByteArraySignatureMetadataPassesThrough() {
      // Same tolerance as the reasoning path: the in-process conversation store can hand back the
      // raw byte[] with no JSON round trip in between.
      final var signatureBytes = "sig-bytes".getBytes(StandardCharsets.UTF_8);

      final var parts =
          converter.toParts(
              List.of(
                  new TextContent(
                      "the answer", Map.of("thoughtSignature", (Object) signatureBytes))));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).thoughtSignature().orElseThrow()).isEqualTo(signatureBytes);
    }

    @Test
    void mapsTextContentWithUnrelatedMetadataWithoutSettingASignature() {
      final var parts =
          converter.toParts(
              List.of(new TextContent("the answer", Map.of("somethingElse", "value"))));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).text()).contains("the answer");
      assertThat(parts.get(0).thoughtSignature()).isEmpty();
    }

    @Test
    void mapsImageDocumentToInlineDataPart() {
      final var doc = mockDocument("image/png", "ABC".getBytes(StandardCharsets.UTF_8));

      final var parts = converter.toParts(List.of(new DocumentContent(doc, null)));

      assertThat(parts).hasSize(1);
      final var inlineData = parts.get(0).inlineData().orElseThrow();
      assertThat(inlineData.data().orElseThrow()).isEqualTo("ABC".getBytes(StandardCharsets.UTF_8));
      assertThat(inlineData.mimeType()).contains("image/png");
    }

    @Test
    void mapsImageDocumentWithContentTypeParametersToInlineDataPart() {
      final var doc =
          mockDocument("image/png; charset=UTF-8", "ABC".getBytes(StandardCharsets.UTF_8));

      final var parts = converter.toParts(List.of(new DocumentContent(doc, null)));

      assertThat(parts).hasSize(1);
      final var inlineData = parts.get(0).inlineData().orElseThrow();
      assertThat(inlineData.mimeType()).contains("image/png");
    }

    @Test
    void mapsPdfDocumentToInlineDataPart() {
      final var doc =
          mockDocument("application/pdf", "PDFCONTENT".getBytes(StandardCharsets.UTF_8));

      final var parts = converter.toParts(List.of(new DocumentContent(doc, null)));

      assertThat(parts).hasSize(1);
      final var inlineData = parts.get(0).inlineData().orElseThrow();
      assertThat(inlineData.data().orElseThrow())
          .isEqualTo("PDFCONTENT".getBytes(StandardCharsets.UTF_8));
      assertThat(inlineData.mimeType()).contains("application/pdf");
    }

    @Test
    void mapsTextDocumentToTextPart() {
      final var doc =
          mockDocument("text/plain", "plain text content".getBytes(StandardCharsets.UTF_8));

      final var parts = converter.toParts(List.of(new DocumentContent(doc, null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).text()).contains("plain text content");
    }

    @Test
    void mapsObjectContentToJsonTextPart() {
      final var parts = converter.toParts(List.of(new ObjectContent(Map.of("key", "value"), null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).text()).contains("{\"key\":\"value\"}");
    }

    @Test
    void mapsReasoningContentToThoughtPartWithSignatureRoundTrip() {
      final var signatureBytes = "sig-bytes".getBytes(StandardCharsets.UTF_8);
      final var metadata =
          Map.<String, Object>of(
              "thoughtSignature", Base64.getEncoder().encodeToString(signatureBytes));

      final var parts =
          converter.toParts(
              List.of(
                  new ReasoningContent(
                      "gemini", Map.of("type", "thought"), "Let me think it through", metadata)));

      assertThat(parts).hasSize(1);
      final var part = parts.get(0);
      assertThat(part.thought()).contains(true);
      assertThat(part.text()).contains("Let me think it through");
      assertThat(part.thoughtSignature().orElseThrow()).isEqualTo(signatureBytes);
    }

    @Test
    void mapsReasoningContentWithoutSignatureOmitsThoughtSignature() {
      final var parts =
          converter.toParts(
              List.of(
                  new ReasoningContent(
                      "gemini", Map.of("type", "thought"), "Let me think it through", null)));

      assertThat(parts).hasSize(1);
      final var part = parts.get(0);
      assertThat(part.thought()).contains(true);
      assertThat(part.text()).contains("Let me think it through");
      assertThat(part.thoughtSignature()).isEmpty();
    }

    @Test
    void mapsReasoningContentWithRawByteArraySignaturePassesThrough() {
      final var signatureBytes = "sig-bytes".getBytes(StandardCharsets.UTF_8);
      final var metadata = Map.<String, Object>of("thoughtSignature", signatureBytes);

      final var parts =
          converter.toParts(
              List.of(
                  new ReasoningContent(
                      "gemini", Map.of("type", "thought"), "Let me think it through", metadata)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).thoughtSignature().orElseThrow()).isEqualTo(signatureBytes);
    }

    @Test
    void throwsForUnsupportedThoughtSignatureMetadataValueType() {
      final var metadata = Map.<String, Object>of("thoughtSignature", 42);

      assertThatThrownBy(
              () ->
                  converter.toParts(
                      List.of(
                          new ReasoningContent(
                              "gemini",
                              Map.of("type", "thought"),
                              "Let me think it through",
                              metadata))))
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("thoughtSignature")
          .hasMessageContaining("Integer");
    }

    @Test
    void mapsReasoningContentWithNullTextDoesNotSetText() {
      final var parts =
          converter.toParts(
              List.of(new ReasoningContent("gemini", Map.of("type", "thought"), null, null)));

      assertThat(parts).hasSize(1);
      final var part = parts.get(0);
      assertThat(part.thought()).contains(true);
      assertThat(part.text()).isEmpty();
    }

    @Test
    void throwsForBlankDocumentContentType() {
      final var doc = mockDocument("", "CONTENT".getBytes(StandardCharsets.UTF_8));

      assertThatThrownBy(() -> converter.toParts(List.of(new DocumentContent(doc, null))))
          .isInstanceOf(ConnectorException.class);
    }

    @Test
    void mapsProviderContentPayloadToNativePartRoundTrip() {
      final var payload =
          Map.<String, Object>of(
              "functionCall", Map.of("name", "myFunction", "args", Map.of("a", 1)));

      final var parts = converter.toParts(List.of(new ProviderContent("gemini", payload, null)));

      assertThat(parts).hasSize(1);
      final FunctionCall functionCall = parts.get(0).functionCall().orElseThrow();
      assertThat(functionCall.name()).contains("myFunction");
    }

    @Test
    void throwsForUnsupportedDocumentContentType() {
      final var doc =
          mockDocument("application/zip", "ZIPCONTENT".getBytes(StandardCharsets.UTF_8));

      assertThatThrownBy(() -> converter.toParts(List.of(new DocumentContent(doc, null))))
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("application/zip");
    }

    @Test
    void mapsMultipleContentItemsInOrder() {
      final var doc = mockDocument("image/png", "ABC".getBytes(StandardCharsets.UTF_8));
      final List<Content> content =
          List.of(new TextContent("first", null), new DocumentContent(doc, null));

      final var parts = converter.toParts(content);

      assertThat(parts).hasSize(2);
      assertThat(parts.get(0).text()).isPresent();
      assertThat(parts.get(1).inlineData()).isPresent();
    }
  }

  @Nested
  class ToFunctionResponseParts {

    @Test
    void mapsTextContentToFunctionResponsePart() {
      final var parts = converter.toFunctionResponseParts(List.of(new TextContent("hello", null)));

      assertThat(parts).hasSize(1);
      final var response = parts.get(0).functionResponse().orElseThrow();
      assertThat(response.response().orElseThrow()).isEqualTo(Map.of("output", "hello"));
      assertThat(response.name()).isEmpty();
      assertThat(response.id()).isEmpty();
    }

    @Test
    void mapsObjectContentToFunctionResponsePartWrappedUnderOutputKey() {
      final var parts =
          converter.toFunctionResponseParts(List.of(new ObjectContent(Map.of("a", 1), null)));

      assertThat(parts).hasSize(1);
      final var response = parts.get(0).functionResponse().orElseThrow();
      assertThat(response.response().orElseThrow()).isEqualTo(Map.of("output", Map.of("a", 1)));
    }

    @Test
    void mapsImageDocumentToInlineDataPart() {
      final var doc = mockDocument("image/jpeg", "ABC".getBytes(StandardCharsets.UTF_8));

      final var parts = converter.toFunctionResponseParts(List.of(new DocumentContent(doc, null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).inlineData()).isPresent();
      assertThat(parts.get(0).functionResponse()).isEmpty();
    }

    @Test
    void mapsPdfDocumentToInlineDataPart() {
      final var doc =
          mockDocument("application/pdf", "PDFCONTENT".getBytes(StandardCharsets.UTF_8));

      final var parts = converter.toFunctionResponseParts(List.of(new DocumentContent(doc, null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).inlineData()).isPresent();
    }

    @Test
    void throwsForUnsupportedDocumentContentType() {
      final var doc =
          mockDocument("application/zip", "ZIPCONTENT".getBytes(StandardCharsets.UTF_8));

      assertThatThrownBy(
              () -> converter.toFunctionResponseParts(List.of(new DocumentContent(doc, null))))
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("application/zip");
    }

    @Test
    void mapsReasoningContentToJsonTextPartFallback() {
      final var parts =
          converter.toFunctionResponseParts(
              List.of(
                  new ReasoningContent(
                      "gemini", Map.of("thinking", "some reasoning"), null, null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).text().orElseThrow()).contains("some reasoning");
      assertThat(parts.get(0).functionResponse()).isEmpty();
    }

    @Test
    void mapsProviderContentToJsonTextPartFallback() {
      final var parts =
          converter.toFunctionResponseParts(
              List.of(new ProviderContent("gemini", Map.of("foo", "bar"), null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).text().orElseThrow()).contains("bar");
      assertThat(parts.get(0).functionResponse()).isEmpty();
    }
  }
}
