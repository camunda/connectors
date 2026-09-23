/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openai.core.JsonValue;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MistralDocumentUrlContentChunkStrategyTest {

  private final OpenAiCompletionsContentChunkStrategy strategy =
      OpenAiCompletionsContentChunkStrategy.mistral(TestObjectMapperSupplier.INSTANCE);

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
  void mapsPdfDocumentToDocumentUrlPartInsteadOfFilePart() {
    // Mistral's real API rejects OpenAI's `file`/`file_data` chunk for a PDF (verified against
    // the real API) and requires a `document_url` chunk carrying the data URI directly instead.
    final var document = pdfDocument();

    final var parts =
        strategy.toContentParts(List.of((Content) new DocumentContent(document, null)));

    assertThat(parts).hasSize(1);
    assertThat(parts.get(0).isFile()).isFalse();
    assertThat(parts.get(0)._json())
        .hasValue(
            JsonValue.from(
                Map.of(
                    "type",
                    "document_url",
                    "document_url",
                    "data:application/pdf;base64,UERGQ09OVEVOVA==")));
  }

  @Test
  void mapsImageDocumentToTheSameImageUrlPartAsOpenAi() {
    // Verified against the real Mistral API: the identical image_url shape OpenAI uses works
    // unchanged, so no provider-specific branch is needed here.
    final var document = mock(Document.class);
    final var metadata = mock(DocumentMetadata.class);
    when(document.metadata()).thenReturn(metadata);
    when(metadata.getContentType()).thenReturn("image/png");
    when(document.asBase64()).thenReturn("QUJD");

    final var parts =
        strategy.toContentParts(List.of((Content) new DocumentContent(document, null)));

    assertThat(parts).hasSize(1);
    assertThat(parts.get(0).isImageUrl()).isTrue();
    assertThat(parts.get(0).asImageUrl().imageUrl().url()).isEqualTo("data:image/png;base64,QUJD");
  }
}
