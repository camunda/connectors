/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;

/**
 * Base for the AWS vector stores. The LangChain4j stores treat a missing index or table as an empty
 * search result and create it with their own defaults. This base checks the index or table first,
 * so a wrong name or region fails the job, and builds the LangChain4j store from the settings of
 * the index or table that actually exists.
 *
 * <p>Only the chunk text is stored. Retrieve never returns segment metadata, and parsed document
 * metadata can exceed S3 Vectors' metadata limits or collide with DynamoDB attribute names.
 */
abstract class AwsVectorEmbeddingStore implements EmbeddingStore<TextSegment> {

  /** S3 Vectors and DynamoDB both return at most 100 results per query. */
  static final int MAX_RESULTS = 100;

  private EmbeddingStore<TextSegment> writeStore;

  /**
   * Checks the index or table, creating it for the given dimension when missing, and returns the
   * store to write to.
   */
  protected abstract EmbeddingStore<TextSegment> openForWrite(int dimension);

  /**
   * Checks the index or table, failing when it is missing or does not match the given dimension,
   * and returns the store to search.
   */
  protected abstract EmbeddingStore<TextSegment> openForRead(int dimension);

  protected abstract String name();

  @Override
  public String add(Embedding embedding) {
    var id = generateIds(1).getFirst();
    add(id, embedding);
    return id;
  }

  @Override
  public void add(String id, Embedding embedding) {
    addAll(List.of(id), List.of(embedding), null);
  }

  @Override
  public String add(Embedding embedding, TextSegment segment) {
    var id = generateIds(1).getFirst();
    addAll(List.of(id), List.of(embedding), List.of(segment));
    return id;
  }

  @Override
  public List<String> addAll(List<Embedding> embeddings) {
    var ids = generateIds(embeddings.size());
    addAll(ids, embeddings, null);
    return ids;
  }

  @Override
  public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> segments) {
    if (embeddings.isEmpty()) {
      return;
    }
    if (writeStore == null) {
      writeStore = openForWrite(embeddings.getFirst().dimension());
    }
    writeStore.addAll(
        ids,
        embeddings,
        segments == null
            ? null
            : segments.stream().map(segment -> TextSegment.from(segment.text())).toList());
  }

  @Override
  public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
    if (request.maxResults() > MAX_RESULTS) {
      throw new ConnectorException(
          "%s returns at most %d results per query, but %d were requested"
              .formatted(name(), MAX_RESULTS, request.maxResults()));
    }
    return openForRead(request.queryEmbedding().dimension()).search(request);
  }
}
