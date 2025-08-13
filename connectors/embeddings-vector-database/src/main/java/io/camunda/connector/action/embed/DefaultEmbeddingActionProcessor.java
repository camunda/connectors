/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.action.embed;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.camunda.connector.doc.parsing.DefaultTextSegmentExtractor;
import io.camunda.connector.embeddingmodel.DefaultEmbeddingModelFactory;
import io.camunda.connector.embeddingstore.ClosableEmbeddingStore;
import io.camunda.connector.embeddingstore.DefaultEmbeddingStoreFactory;
import io.camunda.connector.model.EmbeddingsVectorDBRequest;
import java.util.List;

public class DefaultEmbeddingActionProcessor implements EmbeddingActionProcessor {

  private final DefaultEmbeddingModelFactory embeddingModelProvider;
  private final DefaultEmbeddingStoreFactory embeddingStoreProvider;
  private final DefaultTextSegmentExtractor textSegmentExtractor;

  public DefaultEmbeddingActionProcessor() {
    this(
        new DefaultEmbeddingModelFactory(),
        new DefaultEmbeddingStoreFactory(),
        new DefaultTextSegmentExtractor());
  }

  public DefaultEmbeddingActionProcessor(
      final DefaultEmbeddingModelFactory embeddingModelProvider,
      final DefaultEmbeddingStoreFactory embeddingStoreProvider,
      DefaultTextSegmentExtractor textSegmentExtractor) {
    this.embeddingModelProvider = embeddingModelProvider;
    this.embeddingStoreProvider = embeddingStoreProvider;
    this.textSegmentExtractor = textSegmentExtractor;
  }

  @Override
  public List<String> embed(EmbeddingsVectorDBRequest request) {
    EmbeddingModel model =
        embeddingModelProvider.createEmbeddingModel(request.embeddingModelProvider());
    try (ClosableEmbeddingStore store =
        embeddingStoreProvider.initializeVectorStore(
            request.vectorStore(), model, request.vectorDatabaseConnectorOperation())) {

      // split incoming documents into chunks (segments) so that converting those
      // to vector-normal formal preserves maximum amount of properties.
      final var segments = textSegmentExtractor.fromRequest(request);

      // convert chunks (segments) into vector form, persist
      // in a vector DB and return chunks identifiers
      return segments.stream()
          .map(segment -> store.add(model.embed(segment).content(), segment))
          .toList();
    }
  }
}
