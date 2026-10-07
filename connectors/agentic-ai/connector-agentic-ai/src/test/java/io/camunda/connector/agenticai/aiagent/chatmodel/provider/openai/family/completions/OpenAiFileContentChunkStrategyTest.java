/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiFileContentChunkStrategyTest {

  private final OpenAiCompletionsContentChunkStrategy strategy =
      OpenAiCompletionsContentChunkStrategy.openAi(TestObjectMapperSupplier.INSTANCE);

  private static Document mockDocument(String contentType, String base64) {
    final var document = mock(Document.class);
    final var metadata = mock(DocumentMetadata.class);
    when(document.metadata()).thenReturn(metadata);
    when(metadata.getContentType()).thenReturn(contentType);
    when(document.asBase64()).thenReturn(base64);
    return document;
  }

  private static Document pdfDocument() {
    final var document = mock(Document.class);
    final var metadata = mock(DocumentMetadata.class);
    when(document.metadata()).thenReturn(metadata);
    when(metadata.getContentType()).thenReturn("application/pdf");
    when(metadata.getFileName()).thenReturn("report.pdf");
    when(document.asBase64()).thenReturn("UERGQ09OVEVOVA==");
    return document;
  }

  @Test
  void mapsTextContentToTextPart() {
    final var parts = strategy.toContentParts(List.of(new TextContent("hello world", null)));

    assertThat(parts).hasSize(1);
    assertThat(parts.get(0).isText()).isTrue();
    assertThat(parts.get(0).asText().text()).isEqualTo("hello world");
  }

  @Test
  void mapsImageDocumentToImageUrlPart() {
    final var doc = mockDocument("image/png", "QUJD");

    final var parts = strategy.toContentParts(List.of((Content) new DocumentContent(doc, null)));

    assertThat(parts).hasSize(1);
    assertThat(parts.get(0).isImageUrl()).isTrue();
    assertThat(parts.get(0).asImageUrl().imageUrl().url()).isEqualTo("data:image/png;base64,QUJD");
  }

  @Test
  void mapsPdfDocumentToFilePart() {
    final var document = pdfDocument();

    final var parts =
        strategy.toContentParts(List.of((Content) new DocumentContent(document, null)));

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
    when(document.asByteArray()).thenReturn("plain text content".getBytes(StandardCharsets.UTF_8));

    final var parts =
        strategy.toContentParts(List.of((Content) new DocumentContent(document, null)));

    assertThat(parts).hasSize(1);
    assertThat(parts.get(0).isText()).isTrue();
    assertThat(parts.get(0).asText().text()).isEqualTo("plain text content");
  }

  @Test
  void throwsForUnsupportedDocumentContentType() {
    final var document = mockDocument("audio/mpeg", "QUJD");

    assertThatThrownBy(
            () -> strategy.toContentParts(List.of((Content) new DocumentContent(document, null))))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("audio/mpeg");
  }

  @Test
  void mapsObjectContentToTextPart() {
    final var parts =
        strategy.toContentParts(List.of((Content) new ObjectContent(Map.of("key", "value"), null)));

    assertThat(parts).hasSize(1);
    assertThat(parts.get(0).isText()).isTrue();
    assertThat(parts.get(0).asText().text()).isEqualTo("{\"key\":\"value\"}");
  }

  @Test
  void mapsReasoningContentToTextPartFallback() {
    final var parts =
        strategy.toContentParts(
            List.of(
                (Content) new ReasoningContent("openai", Map.of("type", "reasoning"), null, null)));

    assertThat(parts).hasSize(1);
    assertThat(parts.get(0).isText()).isTrue();
  }

  @Test
  void mapsProviderContentToTextPartFallback() {
    final var payload = Map.<String, Object>of("type", "server_tool_use", "id", "srvtoolu_01ABC");

    final var parts =
        strategy.toContentParts(List.of((Content) new ProviderContent("openai", payload, null)));

    assertThat(parts).hasSize(1);
    assertThat(parts.get(0).isText()).isTrue();
  }
}
