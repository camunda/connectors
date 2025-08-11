/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.action.embed;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.camunda.connector.doc.parsing.DefaultTextSegmentExtractor;
import io.camunda.connector.embeddingmodel.DefaultEmbeddingModelFactory;
import io.camunda.connector.embeddingstore.DefaultEmbeddingStoreFactory;
import io.camunda.connector.fixture.EmbeddingsVectorDBRequestFixture;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class DefaultEmbeddingActionProcessorTest {

  @Test
  void embed_HappyCase() {
    final var request = EmbeddingsVectorDBRequestFixture.createDefaultEmbedOperation();

    final var embeddingModelProvider = Mockito.mock(DefaultEmbeddingModelFactory.class);
    final var model = Mockito.mock(EmbeddingModel.class);
    Mockito.when(embeddingModelProvider.createEmbeddingModel(request.embeddingModelProvider()))
        .thenReturn(model);
    Mockito.when(model.embed((TextSegment) ArgumentMatchers.any()))
        .thenReturn(new Response<>(new Embedding(new float[] {0.1f, 0.2f})));

    final var embeddingStoreProvider = Mockito.mock(DefaultEmbeddingStoreFactory.class);
    final var store = Mockito.mock(EmbeddingStore.class);
    Mockito.when(
            embeddingStoreProvider.initializeVectorStore(
                request.vectorStore(), model, request.vectorDatabaseConnectorOperation()))
        .thenReturn(store);

    final var textSegmentExtractor = Mockito.mock(DefaultTextSegmentExtractor.class);
    Mockito.when(textSegmentExtractor.fromRequest(request))
        .thenReturn(List.of(TextSegment.from("Document 1"), TextSegment.from("Document 2")));

    final var testee =
        new DefaultEmbeddingActionProcessor(
            embeddingModelProvider, embeddingStoreProvider, textSegmentExtractor);

    final var result = testee.embed(request);

    Assertions.assertThat(result).hasSize(2);
  }
}
