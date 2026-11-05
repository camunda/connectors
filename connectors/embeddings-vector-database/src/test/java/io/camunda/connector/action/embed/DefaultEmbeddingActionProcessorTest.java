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
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.doc.parsing.DefaultTextSegmentExtractor;
import io.camunda.connector.embeddingmodel.DefaultEmbeddingModelFactory;
import io.camunda.connector.embeddingstore.ClosableEmbeddingStore;
import io.camunda.connector.embeddingstore.DefaultEmbeddingStoreFactory;
import io.camunda.connector.embeddingstore.SegmentSizeLimit;
import io.camunda.connector.fixture.EmbeddingsVectorDBRequestFixture;
import java.util.List;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class DefaultEmbeddingActionProcessorTest {

  @Test
  void embed_HappyCase() {
    final var store = Mockito.mock(ClosableEmbeddingStore.class);

    final var result = embed(store, segments(2));

    Assertions.assertThat(result).containsExactly("id-0", "id-1");
    Mockito.verify(store, Mockito.times(1))
        .addAll(ArgumentMatchers.anyList(), ArgumentMatchers.anyList());
  }

  @Test
  void embed_WritesInBatches() {
    final var store = Mockito.mock(ClosableEmbeddingStore.class);

    final var result = embed(store, segments(250));

    Assertions.assertThat(result).hasSize(250);
    final ArgumentCaptor<List<TextSegment>> batches = ArgumentCaptor.forClass(List.class);
    Mockito.verify(store, Mockito.times(3)).addAll(ArgumentMatchers.anyList(), batches.capture());
    Assertions.assertThat(batches.getAllValues())
        .extracting(List::size)
        .containsExactly(100, 100, 50);
  }

  @Test
  void embed_ChecksSegmentSizesBeforeWritingAnything() {
    final var store = Mockito.mock(ClosableEmbeddingStore.class);
    final var limitedStore =
        Mockito.mock(
            EmbeddingStore.class, Mockito.withSettings().extraInterfaces(SegmentSizeLimit.class));
    Mockito.when(store.getEmbeddingStore()).thenReturn(limitedStore);
    Mockito.doThrow(new ConnectorException("chunk 150 too large"))
        .when((SegmentSizeLimit) limitedStore)
        .checkSegments(ArgumentMatchers.anyList());

    Assertions.assertThatThrownBy(() -> embed(store, segments(250)))
        .isInstanceOf(ConnectorException.class)
        .hasMessage("chunk 150 too large");
    Mockito.verify(store, Mockito.never())
        .addAll(ArgumentMatchers.anyList(), ArgumentMatchers.anyList());
  }

  @SuppressWarnings("unchecked")
  private static List<String> embed(ClosableEmbeddingStore store, List<TextSegment> segments) {
    final var request = EmbeddingsVectorDBRequestFixture.createDefaultEmbedOperation();

    final var embeddingModelProvider = Mockito.mock(DefaultEmbeddingModelFactory.class);
    final var model = Mockito.mock(EmbeddingModel.class);
    Mockito.when(embeddingModelProvider.createEmbeddingModel(request.embeddingModelProvider()))
        .thenReturn(model);
    Mockito.when(model.embed((TextSegment) ArgumentMatchers.any()))
        .thenReturn(new Response<>(new Embedding(new float[] {0.1f, 0.2f})));

    final var embeddingStoreProvider = Mockito.mock(DefaultEmbeddingStoreFactory.class);
    Mockito.when(
            embeddingStoreProvider.initializeVectorStore(
                request.vectorStore(), model, request.vectorDatabaseConnectorOperation()))
        .thenReturn(store);
    Mockito.when(store.addAll(ArgumentMatchers.anyList(), ArgumentMatchers.anyList()))
        .thenAnswer(invocation -> {
          List<TextSegment> batch = invocation.getArgument(1);
          return batch.stream().map(TextSegment::text).map(text -> "id-" + text).toList();
        });

    final var textSegmentExtractor = Mockito.mock(DefaultTextSegmentExtractor.class);
    Mockito.when(textSegmentExtractor.fromRequest(request)).thenReturn(segments);

    final var testee =
        new DefaultEmbeddingActionProcessor(
            embeddingModelProvider, embeddingStoreProvider, textSegmentExtractor);

    return testee.embed(request);
  }

  private static List<TextSegment> segments(int count) {
    return IntStream.range(0, count).mapToObj(i -> TextSegment.from(String.valueOf(i))).toList();
  }
}
