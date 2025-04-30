/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.doc.parsing;

import io.camunda.connector.fixture.EmbeddingsVectorDBRequestFixture;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultTextSegmentExtractorTest {

  @Test
  void segmentsFromRequestWithTxtDoc() {
    final var extractor = new DefaultTextSegmentExtractor();

    final var segments =
        extractor.fromRequest(EmbeddingsVectorDBRequestFixture.createDefaultEmbedOperation());

    Assertions.assertThat(segments).size().isEqualTo(1);
  }

  @Test
  void segmentsFromRequestWithPdfDoc() {
    final var extractor = new DefaultTextSegmentExtractor();

    final var segments =
        extractor.fromRequest(EmbeddingsVectorDBRequestFixture.createEmbedOperationWithPdfFile());

    Assertions.assertThat(segments).size().isEqualTo(1);
  }

  @Test
  void segmentsFromRequestWithPlainTextInput() {
    final var extractor = new DefaultTextSegmentExtractor();

    final var segments =
        extractor.fromRequest(EmbeddingsVectorDBRequestFixture.createEmbedOperationWithPlainText());

    Assertions.assertThat(segments).size().isEqualTo(1);
  }
}
