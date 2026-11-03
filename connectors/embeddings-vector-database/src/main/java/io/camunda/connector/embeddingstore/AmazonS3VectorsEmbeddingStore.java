/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import dev.langchain4j.community.store.embedding.s3.S3VectorsEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.Optional;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.ConflictException;
import software.amazon.awssdk.services.s3vectors.model.CreateIndexRequest;
import software.amazon.awssdk.services.s3vectors.model.DataType;
import software.amazon.awssdk.services.s3vectors.model.DistanceMetric;
import software.amazon.awssdk.services.s3vectors.model.GetIndexRequest;
import software.amazon.awssdk.services.s3vectors.model.Index;
import software.amazon.awssdk.services.s3vectors.model.MetadataConfiguration;
import software.amazon.awssdk.services.s3vectors.model.NotFoundException;

/**
 * Amazon S3 Vectors store. Only the chunk text is stored, as non-filterable metadata: S3 Vectors
 * allows at most 2 KB of filterable metadata and 50 metadata keys per vector, which chunk text and
 * parsed document metadata would exceed.
 */
class AmazonS3VectorsEmbeddingStore extends AwsVectorEmbeddingStore {

  static final String TEXT_METADATA_KEY = S3VectorsEmbeddingStore.DEFAULT_TEXT_METADATA_KEY;
  private static final Duration INDEX_READY_TIMEOUT = Duration.ofSeconds(30);

  private final S3VectorsClient client;
  private final String region;
  private final String vectorBucketName;
  private final String indexName;

  AmazonS3VectorsEmbeddingStore(
      S3VectorsClient client, String region, String vectorBucketName, String indexName) {
    this.client = client;
    this.region = region;
    this.vectorBucketName = vectorBucketName;
    this.indexName = indexName;
  }

  @Override
  protected String name() {
    return "Amazon S3 Vectors";
  }

  @Override
  protected TextSegment toStoredSegment(TextSegment segment) {
    return TextSegment.from(segment.text());
  }

  @Override
  protected EmbeddingStore<TextSegment> openForWrite(int dimension) {
    var index = findIndex().orElseGet(() -> createIndex(dimension));
    if (index.dimension() != dimension) {
      throw new ConnectorException(
          "Vector index '%s' has %d dimensions, but the embedding model produces %d"
              .formatted(indexName, index.dimension(), dimension));
    }
    var metadata = index.metadataConfiguration();
    if (metadata == null || !metadata.nonFilterableMetadataKeys().contains(TEXT_METADATA_KEY)) {
      throw new ConnectorException(
          ("Vector index '%s' must declare '%s' as a non-filterable metadata key, because the chunk"
                  + " text is stored there. Use a new index name to let the connector create it.")
              .formatted(indexName, TEXT_METADATA_KEY));
    }
    return libraryStore(index);
  }

  @Override
  protected EmbeddingStore<TextSegment> openForRead() {
    return libraryStore(
        findIndex()
            .orElseThrow(() ->
                new ConnectorException(
                    "Vector index '%s' not found in vector bucket '%s' in region '%s'"
                        .formatted(indexName, vectorBucketName, region))));
  }

  private EmbeddingStore<TextSegment> libraryStore(Index index) {
    return S3VectorsEmbeddingStore.builder()
        .s3VectorsClient(client)
        .vectorBucketName(vectorBucketName)
        .indexName(indexName)
        .distanceMetric(index.distanceMetric())
        .createIndexIfNotExists(false)
        .build();
  }

  private Optional<Index> findIndex() {
    try {
      return Optional.of(
          client
              .getIndex(
                  GetIndexRequest.builder()
                      .vectorBucketName(vectorBucketName)
                      .indexName(indexName)
                      .build())
              .index());
    } catch (NotFoundException e) {
      return Optional.empty();
    }
  }

  private Index createIndex(int dimension) {
    try {
      client.createIndex(
          CreateIndexRequest.builder()
              .vectorBucketName(vectorBucketName)
              .indexName(indexName)
              .dataType(DataType.FLOAT32)
              .dimension(dimension)
              .distanceMetric(DistanceMetric.COSINE)
              .metadataConfiguration(
                  MetadataConfiguration.builder()
                      .nonFilterableMetadataKeys(TEXT_METADATA_KEY)
                      .build())
              .build());
    } catch (ConflictException e) {
      // created concurrently by another job
    } catch (NotFoundException e) {
      throw new ConnectorException(
          "Vector bucket '%s' not found in region '%s'".formatted(vectorBucketName, region));
    }
    return AwsWait.<Index>until(
        this::findIndex,
        INDEX_READY_TIMEOUT,
        "Vector index '%s' was not available within %d seconds"
            .formatted(indexName, INDEX_READY_TIMEOUT.toSeconds()));
  }
}
