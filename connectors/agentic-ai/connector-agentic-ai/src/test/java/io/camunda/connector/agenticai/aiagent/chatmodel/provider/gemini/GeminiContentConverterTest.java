/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import static io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GOOGLE_GEMINI_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.genai.types.FunctionCall;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.document.jackson.DocumentReferenceModel.ExternalDocumentReferenceModel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GeminiContentConverterTest {

  private final GeminiContentConverter converter =
      new GeminiContentConverter(TestObjectMapperSupplier.INSTANCE);

  private static Document mockDocument(String contentType, byte[] bytes) {
    final var document = mock(Document.class);
    final var metadata = mock(DocumentMetadata.class);
    when(document.metadata()).thenReturn(metadata);
    when(metadata.getContentType()).thenReturn(contentType);
    when(document.asByteArray()).thenReturn(bytes);
    // stubbed so a reference-only fallback (tool-result documents) can serialize this mock via
    // JacksonModuleDocumentSerializer, which dispatches on Document#reference()
    when(document.reference())
        .thenReturn(new ExternalDocumentReferenceModel("https://example.com/document", "document"));
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
                      GOOGLE_GEMINI_ID,
                      Map.of("type", "thought"),
                      "Let me think it through",
                      metadata)));

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
                      GOOGLE_GEMINI_ID,
                      Map.of("type", "thought"),
                      "Let me think it through",
                      null)));

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
                      GOOGLE_GEMINI_ID,
                      Map.of("type", "thought"),
                      "Let me think it through",
                      metadata)));

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
                              GOOGLE_GEMINI_ID,
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
              List.of(
                  new ReasoningContent(GOOGLE_GEMINI_ID, Map.of("type", "thought"), null, null)));

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

      final var parts =
          converter.toParts(List.of(new ProviderContent(GOOGLE_GEMINI_ID, payload, null)));

      assertThat(parts).hasSize(1);
      final FunctionCall functionCall = parts.get(0).functionCall().orElseThrow();
      assertThat(functionCall.name()).contains("myFunction");
    }

    @Test
    void dropsReasoningContentFromAForeignProvider() {
      final var parts =
          converter.toParts(
              List.of(new ReasoningContent("openai", Map.of("type", "reasoning"), null, null)));

      assertThat(parts).isEmpty();
    }

    @Test
    void dropsProviderContentFromAForeignProvider() {
      final var parts =
          converter.toParts(
              List.of(new ProviderContent("anthropic", Map.of("type", "web_search"), null)));

      assertThat(parts).isEmpty();
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
    void mapsObjectContentToFunctionResponsePartAsJsonString() {
      // serialized to a JSON string via the connector's own ObjectMapper rather than handed to
      // the SDK as a raw object graph - the SDK's own (unconfigured) Jackson mapper has no
      // serializer for domain types such as Document that may be nested inside tool call output
      // (e.g. an ad-hoc tool result containing document attachments)
      final var parts =
          converter.toFunctionResponseParts(List.of(new ObjectContent(Map.of("a", 1), null)));

      assertThat(parts).hasSize(1);
      final var response = parts.get(0).functionResponse().orElseThrow();
      assertThat(response.response().orElseThrow()).isEqualTo(Map.of("output", "{\"a\":1}"));
    }

    @Test
    void mapsDocumentContentToFunctionResponsePartWithJsonReference() {
      // never embedded natively here, regardless of content type - the composer's synthetic
      // <doc/> fallback message already delivers the actual bytes for tool results, so this
      // renders the same JSON reference ObjectContent would, avoiding a double-send. Still wrapped
      // in a functionResponse (not a bare text Part): a document-only tool result must close out
      // the preceding functionCall like any other result content.
      final var doc = mockDocument("image/jpeg", "ABC".getBytes(StandardCharsets.UTF_8));

      final var parts = converter.toFunctionResponseParts(List.of(new DocumentContent(doc, null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).inlineData()).isEmpty();
      final var response = parts.get(0).functionResponse().orElseThrow();
      assertThat(response.response().orElseThrow().get("output"))
          .isEqualTo(
              "{\"url\":\"https://example.com/document\",\"name\":\"document\","
                  + "\"camunda.document.type\":\"external\"}");
    }

    @Test
    void mapsUnsupportedDocumentContentTypeToFunctionResponsePart() {
      // an unsupported content type is fine here, unlike toParts's native-embedding path -
      // classification never runs since the document is always flattened to a reference
      final var doc =
          mockDocument("application/zip", "ZIPCONTENT".getBytes(StandardCharsets.UTF_8));

      final var parts = converter.toFunctionResponseParts(List.of(new DocumentContent(doc, null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).functionResponse()).isPresent();
    }

    @Test
    void mapsReasoningContentToFunctionResponsePartWithJsonFallback() {
      final var parts =
          converter.toFunctionResponseParts(
              List.of(
                  new ReasoningContent(
                      GOOGLE_GEMINI_ID, Map.of("thinking", "some reasoning"), null, null)));

      assertThat(parts).hasSize(1);
      final var response = parts.get(0).functionResponse().orElseThrow();
      assertThat(response.response().orElseThrow().get("output").toString())
          .contains("some reasoning");
    }

    @Test
    void mapsProviderContentToFunctionResponsePartWithJsonFallback() {
      final var parts =
          converter.toFunctionResponseParts(
              List.of(new ProviderContent(GOOGLE_GEMINI_ID, Map.of("foo", "bar"), null)));

      assertThat(parts).hasSize(1);
      final var response = parts.get(0).functionResponse().orElseThrow();
      assertThat(response.response().orElseThrow().get("output").toString()).contains("bar");
    }
  }
}
