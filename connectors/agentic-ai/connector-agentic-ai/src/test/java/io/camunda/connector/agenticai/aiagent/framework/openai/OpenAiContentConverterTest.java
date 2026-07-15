/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import java.util.List;
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
  }
}
