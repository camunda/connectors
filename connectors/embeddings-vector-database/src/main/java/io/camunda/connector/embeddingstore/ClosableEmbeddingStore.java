/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrapper for {@link EmbeddingStore} that implements {@link AutoCloseable} to ensure proper
 * resource management. This class delegates all embedding store operations to the wrapped instance.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * try (ClosableEmbeddingStore<TextSegment> store = ClosableEmbeddingStore.wrap(embeddingStore)) {
 *     store.add(embedding, textSegment);
 *     // store will be automatically closed
 * }
 * }</pre>
 */
public class ClosableEmbeddingStore<Embedded> implements EmbeddingStore<Embedded>, AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClosableEmbeddingStore.class);

  private final EmbeddingStore<Embedded> delegate;
  private final Runnable closeAction;

  public static <Embedded> ClosableEmbeddingStore<Embedded> wrap(
      EmbeddingStore<Embedded> embeddingStore) {
    return new ClosableEmbeddingStore<>(embeddingStore, null);
  }

  public static <Embedded> ClosableEmbeddingStore<Embedded> wrap(
      EmbeddingStore<Embedded> embeddingStore, Runnable closeAction) {
    return new ClosableEmbeddingStore<>(embeddingStore, closeAction);
  }

  protected ClosableEmbeddingStore(EmbeddingStore<Embedded> embeddingStore, Runnable closeAction) {
    this.delegate = embeddingStore;
    this.closeAction = closeAction;
  }

  /**
   * Returns the underlying embedding store instance.
   *
   * @return the wrapped embedding store
   */
  public EmbeddingStore<Embedded> getEmbeddingStore() {
    return delegate;
  }

  @Override
  public String add(Embedding embedding) {
    return delegate.add(embedding);
  }

  @Override
  public void add(String id, Embedding embedding) {
    delegate.add(id, embedding);
  }

  @Override
  public String add(Embedding embedding, Embedded embedded) {
    return delegate.add(embedding, embedded);
  }

  @Override
  public List<String> addAll(List<Embedding> embeddings) {
    return delegate.addAll(embeddings);
  }

  @Override
  public List<String> addAll(List<Embedding> embeddings, List<Embedded> embedded) {
    return delegate.addAll(embeddings, embedded);
  }

  @Override
  public EmbeddingSearchResult<Embedded> search(EmbeddingSearchRequest request) {
    return delegate.search(request);
  }

  @Override
  public void addAll(List<String> ids, List<Embedding> embeddings, List<Embedded> embedded) {
    delegate.addAll(ids, embeddings, embedded);
  }

  @Override
  public void remove(String id) {
    delegate.remove(id);
  }

  @Override
  public void removeAll(Collection<String> ids) {
    delegate.removeAll(ids);
  }

  @Override
  public void removeAll(Filter filter) {
    delegate.removeAll(filter);
  }

  @Override
  public void removeAll() {
    delegate.removeAll();
  }

  @Override
  public void close() {
    try {
      if (closeAction != null) {
        closeAction.run();
      } else if (delegate instanceof AutoCloseable autoCloseable) {
        autoCloseable.close();
      }
    } catch (Exception e) {
      LOGGER.warn("Failed to close embedding store: {}", e.getMessage(), e);
    }
  }
}
