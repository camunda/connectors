/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.action.retrieve;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.embeddingmodel.DefaultEmbeddingModelFactory;
import io.camunda.connector.embeddingstore.ClosableEmbeddingStore;
import io.camunda.connector.embeddingstore.DefaultEmbeddingStoreFactory;
import io.camunda.connector.fixture.CamundaDocumentFixture;
import io.camunda.connector.fixture.EmbeddingsVectorDBRequestFixture;
import io.camunda.connector.model.operation.RetrieveDocumentOperation;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class DefaultRetrievingActionProcessorTest {

  @Test
  void retrieve_HappyCase() {
    final var request = EmbeddingsVectorDBRequestFixture.createDefaultRetrieve();
    final var matchedChunkScore = 0.88d;
    final var matchedChunkEmbeddingId = "emb-id-01";
    final var matchedChunkContent = "Returned content";

    final var embeddingModelProvider = Mockito.mock(DefaultEmbeddingModelFactory.class);
    final var model = Mockito.mock(EmbeddingModel.class);
    Mockito.when(embeddingModelProvider.createEmbeddingModel(request.embeddingModelProvider()))
        .thenReturn(model);
    Mockito.when(
            model.embed(
                ((RetrieveDocumentOperation) request.vectorDatabaseConnectorOperation()).query()))
        .thenReturn(Response.from(Embedding.from(List.of(0.1f, 0.2f, 0.3f))));

    final var embeddingStoreProvider = Mockito.mock(DefaultEmbeddingStoreFactory.class);
    final var store = Mockito.mock(ClosableEmbeddingStore.class);
    Mockito.when(
            embeddingStoreProvider.initializeVectorStore(
                request.vectorStore(), model, request.vectorDatabaseConnectorOperation()))
        .thenReturn(store);
    final var embeddingSearchResult = Mockito.mock(EmbeddingSearchResult.class);
    Mockito.when(store.search(ArgumentMatchers.any())).thenReturn(embeddingSearchResult);
    Mockito.when(embeddingSearchResult.matches())
        .thenReturn(
            List.of(
                new EmbeddingMatch<>(
                    matchedChunkScore,
                    matchedChunkEmbeddingId,
                    Embedding.from(List.of(0.2f, 0.3f, 0.4f)),
                    TextSegment.from(matchedChunkContent))));

    final var documentFactory = Mockito.mock(DocumentFactory.class);
    final var document = CamundaDocumentFixture.inMemoryTxtDocument();
    Mockito.when(documentFactory.create(ArgumentMatchers.any())).thenReturn(document);
    final var defaultRetrievingActionProcessor =
        new DefaultRetrievingActionProcessor(embeddingModelProvider, embeddingStoreProvider);

    final var response = defaultRetrievingActionProcessor.retrieve(request, documentFactory);

    Mockito.verify(documentFactory, Mockito.times(1)).create(ArgumentMatchers.any());
    Assertions.assertThat(response.chunks()).hasSize(1);
    Assertions.assertThat(response.chunks().getFirst().score()).isEqualTo(matchedChunkScore);
    Assertions.assertThat(response.chunks().getFirst().chunkId())
        .isEqualTo(matchedChunkEmbeddingId);
  }
}
