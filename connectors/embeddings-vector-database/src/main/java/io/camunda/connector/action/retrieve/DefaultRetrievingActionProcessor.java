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
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.embeddingmodel.DefaultEmbeddingModelFactory;
import io.camunda.connector.embeddingstore.ClosableEmbeddingStore;
import io.camunda.connector.embeddingstore.DefaultEmbeddingStoreFactory;
import io.camunda.connector.model.EmbeddingsVectorDBRequest;
import io.camunda.connector.model.operation.RetrieveDocumentOperation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.core.internal.util.Mimetype;

public class DefaultRetrievingActionProcessor implements RetrievingActionProcessor {

  private final DefaultEmbeddingModelFactory embeddingModelProvider;
  private final DefaultEmbeddingStoreFactory embeddingStoreProvider;

  public DefaultRetrievingActionProcessor() {
    this(new DefaultEmbeddingModelFactory(), new DefaultEmbeddingStoreFactory());
  }

  public DefaultRetrievingActionProcessor(
      final DefaultEmbeddingModelFactory embeddingModelProvider,
      final DefaultEmbeddingStoreFactory embeddingStoreProvider) {
    this.embeddingModelProvider = embeddingModelProvider;
    this.embeddingStoreProvider = embeddingStoreProvider;
  }

  @Override
  public RetrievingActionProcessorResponse retrieve(
      final EmbeddingsVectorDBRequest request, final DocumentFactory documentFactory) {
    final var retrieveRequest =
        (RetrieveDocumentOperation) request.vectorDatabaseConnectorOperation();
    EmbeddingModel model =
        embeddingModelProvider.createEmbeddingModel(request.embeddingModelProvider());

    Embedding queryEmbedding = model.embed(retrieveRequest.query()).content();
    EmbeddingSearchRequest embeddingSearchRequest =
        EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(retrieveRequest.documentLimit())
            // min score 0.0 means every document is applicable
            .minScore(retrieveRequest.minScore() == null ? 0d : retrieveRequest.minScore())
            .build();

    try (ClosableEmbeddingStore store =
        embeddingStoreProvider.initializeVectorStore(
            request.vectorStore(), model, retrieveRequest)) {
      List<EmbeddingMatch<TextSegment>> relevant = store.search(embeddingSearchRequest).matches();

      // original document was split into chunks,
      // thus we return most applicable chunks and,
      // store them into document storage;
      // then they can be used by other connectors
      final var chunks = new ArrayList<RetrievedChunk>();
      for (EmbeddingMatch<TextSegment> matched : relevant) {
        final var persistedDoc =
            documentFactory.create(
                DocumentCreationRequest.from(
                        matched.embedded().text().getBytes(StandardCharsets.UTF_8))
                    .contentType(
                        Mimetype.MIMETYPE_TEXT_PLAIN) // TODO: for v1 we support only plain text
                    .build());
        chunks.add(
            new RetrievedChunk(
                matched.embeddingId(),
                persistedDoc.reference(),
                matched.score(),
                matched.embedded().text()));
      }

      return new RetrievingActionProcessorResponse(chunks);
    }
  }
}
