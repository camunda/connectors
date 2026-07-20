/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OpenAiContentConverterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OpenAiContentConverter converter = new OpenAiContentConverter(objectMapper);

  private static Document mockDocument(String contentType, String base64) {
    final var document = mock(Document.class);
    final var metadata = mock(DocumentMetadata.class);
    when(document.metadata()).thenReturn(metadata);
    when(metadata.getContentType()).thenReturn(contentType);
    when(document.asBase64()).thenReturn(base64);
    return document;
  }

  @Nested
  class ToResponsesContentParts {

    @Test
    void mapsTextContentToInputTextPart() {
      final var parts =
          converter.toResponsesContentParts(List.of(new TextContent("hello world", null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).isInputText()).isTrue();
      assertThat(parts.get(0).asInputText().text()).isEqualTo("hello world");
    }

    @Test
    void mapsImageDocumentToInputImagePart() {
      final var doc = mockDocument("image/png", "QUJD");

      final var parts = converter.toResponsesContentParts(List.of(new DocumentContent(doc, null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).isInputImage()).isTrue();
      assertThat(parts.get(0).asInputImage().imageUrl()).hasValue("data:image/png;base64,QUJD");
    }

    @Test
    void mapsPdfDocumentToInputFilePart() {
      final var document = mock(Document.class);
      final var metadata = mock(DocumentMetadata.class);
      when(document.metadata()).thenReturn(metadata);
      when(metadata.getContentType()).thenReturn("application/pdf");
      when(metadata.getFileName()).thenReturn("report.pdf");
      when(document.asBase64()).thenReturn("UERGQ09OVEVOVA==");

      final var parts =
          converter.toResponsesContentParts(List.of(new DocumentContent(document, null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).isInputFile()).isTrue();
      final var file = parts.get(0).asInputFile();
      assertThat(file.filename()).hasValue("report.pdf");
      assertThat(file.fileData()).hasValue("data:application/pdf;base64,UERGQ09OVEVOVA==");
    }

    @Test
    void mapsTextDocumentToInputTextPart() {
      final var document = mock(Document.class);
      final var metadata = mock(DocumentMetadata.class);
      when(document.metadata()).thenReturn(metadata);
      when(metadata.getContentType()).thenReturn("text/plain");
      when(document.asByteArray())
          .thenReturn("plain text content".getBytes(StandardCharsets.UTF_8));

      final var parts =
          converter.toResponsesContentParts(List.of(new DocumentContent(document, null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).isInputText()).isTrue();
      assertThat(parts.get(0).asInputText().text()).isEqualTo("plain text content");
    }

    @Test
    void mapsUnhandledModalityDocumentToInputTextPartFallback() {
      // audio/video mimes are neither IMAGE, DOCUMENT nor TEXT modality, so the converter falls
      // back to a JSON reference of the DocumentContent rather than crashing or dropping it. A
      // real Document-serializing ObjectMapper (mirroring production wiring) is required here:
      // Document has no bean-style getters, so a bare `new ObjectMapper()` fails to serialize it.
      final var document = mock(Document.class);
      final var metadata = mock(DocumentMetadata.class);
      final var reference = mock(DocumentReference.InlineDocumentReference.class);
      when(document.metadata()).thenReturn(metadata);
      when(metadata.getContentType()).thenReturn("audio/mpeg");
      when(document.reference()).thenReturn(reference);
      when(reference.content()).thenReturn("QUJD");
      when(reference.name()).thenReturn("clip.mp3");

      final var jsonAwareConverter = new OpenAiContentConverter(TestObjectMapperSupplier.INSTANCE);
      final var parts =
          jsonAwareConverter.toResponsesContentParts(List.of(new DocumentContent(document, null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).isInputText()).isTrue();
      assertThat(parts.get(0).asInputText().text()).isNotBlank();
    }

    @Test
    void mapsObjectContentToInputTextPart() {
      final var parts =
          converter.toResponsesContentParts(
              List.of(new ObjectContent(Map.of("key", "value"), null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).isInputText()).isTrue();
      assertThat(parts.get(0).asInputText().text()).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    void mapsReasoningContentToInputTextPartFallback() {
      final var parts =
          converter.toResponsesContentParts(List.of(new ReasoningContent(null, null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).isInputText()).isTrue();
    }

    @Test
    void mapsProviderContentToInputTextPartFallback() {
      final var payload = Map.<String, Object>of("type", "server_tool_use", "id", "srvtoolu_01ABC");

      final var parts =
          converter.toResponsesContentParts(
              List.of(new ProviderContent("openai", "server_tool_use", payload, null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).isInputText()).isTrue();
    }
  }

  @Nested
  class ToToolResultOutputItems {

    @Test
    void mapsTextContentToInputTextItem() {
      final var items =
          converter.toToolResultOutputItems(List.of(new TextContent("hello world", null)));

      assertThat(items).hasSize(1);
      assertThat(items.get(0).isInputText()).isTrue();
      assertThat(items.get(0).asInputText().text()).isEqualTo("hello world");
    }

    @Test
    void mapsImageDocumentToInputImageItem() {
      final var doc = mockDocument("image/png", "QUJD");

      final var items = converter.toToolResultOutputItems(List.of(new DocumentContent(doc, null)));

      assertThat(items).hasSize(1);
      assertThat(items.get(0).isInputImage()).isTrue();
      assertThat(items.get(0).asInputImage().imageUrl()).hasValue("data:image/png;base64,QUJD");
    }

    @Test
    void mapsPdfDocumentToInputFileItem() {
      final var document = mock(Document.class);
      final var metadata = mock(DocumentMetadata.class);
      when(document.metadata()).thenReturn(metadata);
      when(metadata.getContentType()).thenReturn("application/pdf");
      when(metadata.getFileName()).thenReturn("report.pdf");
      when(document.asBase64()).thenReturn("UERGQ09OVEVOVA==");

      final var items =
          converter.toToolResultOutputItems(List.of(new DocumentContent(document, null)));

      assertThat(items).hasSize(1);
      assertThat(items.get(0).isInputFile()).isTrue();
      final var file = items.get(0).asInputFile();
      assertThat(file.filename()).hasValue("report.pdf");
      assertThat(file.fileData()).hasValue("data:application/pdf;base64,UERGQ09OVEVOVA==");
    }

    @Test
    void mapsTextDocumentToInputTextItem() {
      final var document = mock(Document.class);
      final var metadata = mock(DocumentMetadata.class);
      when(document.metadata()).thenReturn(metadata);
      when(metadata.getContentType()).thenReturn("text/plain");
      when(document.asByteArray())
          .thenReturn("plain text content".getBytes(StandardCharsets.UTF_8));

      final var items =
          converter.toToolResultOutputItems(List.of(new DocumentContent(document, null)));

      assertThat(items).hasSize(1);
      assertThat(items.get(0).isInputText()).isTrue();
      assertThat(items.get(0).asInputText().text()).isEqualTo("plain text content");
    }

    @Test
    void mapsObjectContentToInputTextItem() {
      final var items =
          converter.toToolResultOutputItems(
              List.of(new ObjectContent(Map.of("key", "value"), null)));

      assertThat(items).hasSize(1);
      assertThat(items.get(0).isInputText()).isTrue();
      assertThat(items.get(0).asInputText().text()).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    void mapsMixedTextAndDocumentPreservingOrder() {
      final var doc = mockDocument("image/png", "QUJD");

      final var items =
          converter.toToolResultOutputItems(
              List.of(new TextContent("see attached", null), new DocumentContent(doc, null)));

      assertThat(items).hasSize(2);
      assertThat(items.get(0).isInputText()).isTrue();
      assertThat(items.get(0).asInputText().text()).isEqualTo("see attached");
      assertThat(items.get(1).isInputImage()).isTrue();
    }
  }

  @Nested
  class ToCompletionsContentParts {

    @Test
    void mapsTextContentToTextPart() {
      final var parts =
          converter.toCompletionsContentParts(List.of(new TextContent("hello world", null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).isText()).isTrue();
      assertThat(parts.get(0).asText().text()).isEqualTo("hello world");
    }

    @Test
    void mapsImageDocumentToImageUrlPart() {
      final var doc = mockDocument("image/png", "QUJD");

      final var parts =
          converter.toCompletionsContentParts(List.of((Content) new DocumentContent(doc, null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).isImageUrl()).isTrue();
      assertThat(parts.get(0).asImageUrl().imageUrl().url())
          .isEqualTo("data:image/png;base64,QUJD");
    }

    @Test
    void mapsPdfDocumentToFilePart() {
      final var document = mock(Document.class);
      final var metadata = mock(DocumentMetadata.class);
      when(document.metadata()).thenReturn(metadata);
      when(metadata.getContentType()).thenReturn("application/pdf");
      when(metadata.getFileName()).thenReturn("report.pdf");
      when(document.asBase64()).thenReturn("UERGQ09OVEVOVA==");

      final var parts =
          converter.toCompletionsContentParts(
              List.of((Content) new DocumentContent(document, null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).isFile()).isTrue();
      final var file = parts.get(0).asFile().file();
      assertThat(file.filename()).hasValue("report.pdf");
      assertThat(file.fileData()).hasValue("data:application/pdf;base64,UERGQ09OVEVOVA==");
    }

    @Test
    void mapsTextDocumentToTextPart() {
      final var document = mock(Document.class);
      final var metadata = mock(DocumentMetadata.class);
      when(document.metadata()).thenReturn(metadata);
      when(metadata.getContentType()).thenReturn("text/plain");
      when(document.asByteArray())
          .thenReturn("plain text content".getBytes(StandardCharsets.UTF_8));

      final var parts =
          converter.toCompletionsContentParts(
              List.of((Content) new DocumentContent(document, null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).isText()).isTrue();
      assertThat(parts.get(0).asText().text()).isEqualTo("plain text content");
    }

    @Test
    void mapsUnhandledModalityDocumentToTextPartFallback() {
      // audio/video mimes are neither IMAGE, DOCUMENT nor TEXT modality, so the converter falls
      // back to a JSON reference of the DocumentContent rather than crashing or dropping it. A
      // real Document-serializing ObjectMapper (mirroring production wiring) is required here:
      // Document has no bean-style getters, so a bare `new ObjectMapper()` fails to serialize it.
      final var document = mock(Document.class);
      final var metadata = mock(DocumentMetadata.class);
      final var reference = mock(DocumentReference.InlineDocumentReference.class);
      when(document.metadata()).thenReturn(metadata);
      when(metadata.getContentType()).thenReturn("audio/mpeg");
      when(document.reference()).thenReturn(reference);
      when(reference.content()).thenReturn("QUJD");
      when(reference.name()).thenReturn("clip.mp3");

      final var jsonAwareConverter = new OpenAiContentConverter(TestObjectMapperSupplier.INSTANCE);
      final var parts =
          jsonAwareConverter.toCompletionsContentParts(
              List.of((Content) new DocumentContent(document, null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).isText()).isTrue();
      assertThat(parts.get(0).asText().text()).isNotBlank();
    }

    @Test
    void mapsObjectContentToTextPart() {
      final var parts =
          converter.toCompletionsContentParts(
              List.of((Content) new ObjectContent(Map.of("key", "value"), null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).isText()).isTrue();
      assertThat(parts.get(0).asText().text()).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    void mapsReasoningContentToTextPartFallback() {
      final var parts =
          converter.toCompletionsContentParts(List.of((Content) new ReasoningContent(null, null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).isText()).isTrue();
    }

    @Test
    void mapsProviderContentToTextPartFallback() {
      final var payload = Map.<String, Object>of("type", "server_tool_use", "id", "srvtoolu_01ABC");

      final var parts =
          converter.toCompletionsContentParts(
              List.of((Content) new ProviderContent("openai", "server_tool_use", payload, null)));

      assertThat(parts).hasSize(1);
      assertThat(parts.get(0).isText()).isTrue();
    }
  }
}
