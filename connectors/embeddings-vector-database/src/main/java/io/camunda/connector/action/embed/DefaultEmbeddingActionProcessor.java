/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.action.embed;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.camunda.connector.doc.parsing.DefaultTextSegmentExtractor;
import io.camunda.connector.embeddingmodel.DefaultEmbeddingModelFactory;
import io.camunda.connector.embeddingstore.ClosableEmbeddingStore;
import io.camunda.connector.embeddingstore.DefaultEmbeddingStoreFactory;
import io.camunda.connector.embeddingstore.SegmentSizeLimit;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.model.EmbeddingsVectorDBRequest;
import java.util.ArrayList;
import java.util.List;

public class DefaultEmbeddingActionProcessor implements EmbeddingActionProcessor {

  // Bounds each store write; S3 Vectors accepts at most 500 vectors per request.
  static final int BATCH_SIZE = 100;

  private final DefaultEmbeddingModelFactory embeddingModelProvider;
  private final DefaultEmbeddingStoreFactory embeddingStoreProvider;
  private final DefaultTextSegmentExtractor textSegmentExtractor;

  public DefaultEmbeddingActionProcessor(ProxyConfiguration proxyConfiguration) {
    this(
        new DefaultEmbeddingModelFactory(proxyConfiguration),
        new DefaultEmbeddingStoreFactory(proxyConfiguration),
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
    try (ClosableEmbeddingStore<TextSegment> store =
        embeddingStoreProvider.initializeVectorStore(
            request.vectorStore(), model, request.vectorDatabaseConnectorOperation())) {

      // split incoming documents into chunks (segments) so that converting those
      // to vector-normal formal preserves maximum amount of properties.
      final var segments = textSegmentExtractor.fromRequest(request);
      if (store.getEmbeddingStore() instanceof SegmentSizeLimit limit) {
        limit.checkSegments(segments);
      }

      // convert chunks (segments) into vector form, persist
      // in a vector DB and return chunks identifiers
      final var ids = new ArrayList<String>(segments.size());
      for (int start = 0; start < segments.size(); start += BATCH_SIZE) {
        final var batch = segments.subList(start, Math.min(start + BATCH_SIZE, segments.size()));
        final var embeddings =
            batch.stream().map(segment -> model.embed(segment).content()).toList();
        ids.addAll(store.addAll(embeddings, batch));
      }
      return ids;
    }
  }
}
