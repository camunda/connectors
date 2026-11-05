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
import java.util.List;
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
 * Amazon S3 Vectors store. The chunk text is stored as non-filterable metadata, because S3 Vectors
 * allows at most 2 KB of filterable metadata per vector. All metadata of a vector together is
 * limited to 40 KB, which caps the size of a chunk.
 */
class AmazonS3VectorsEmbeddingStore extends AwsVectorEmbeddingStore implements SegmentSizeLimit {

  static final String TEXT_METADATA_KEY = S3VectorsEmbeddingStore.DEFAULT_TEXT_METADATA_KEY;
  static final int MAX_METADATA_BYTES = 40_960;
  private static final Duration INDEX_READY_TIMEOUT = Duration.ofSeconds(30);

  private final S3VectorsClient client;
  private final String region;
  private final String vectorBucketName;
  private final String indexName;
  private final String indexLabel;

  AmazonS3VectorsEmbeddingStore(
      S3VectorsClient client, String region, String vectorBucketName, String indexName) {
    this.client = client;
    this.region = region;
    this.vectorBucketName = vectorBucketName;
    this.indexName = indexName;
    this.indexLabel =
        "Vector index '%s' in vector bucket '%s' in region '%s'"
            .formatted(indexName, vectorBucketName, region);
  }

  @Override
  protected String name() {
    return "Amazon S3 Vectors";
  }

  @Override
  public void checkSegments(List<TextSegment> segments) {
    for (int i = 0; i < segments.size(); i++) {
      int size = metadataSize(segments.get(i).text());
      if (size > MAX_METADATA_BYTES) {
        throw new ConnectorException(
            ("Chunk %d of the document needs %d bytes of metadata, but Amazon S3 Vectors allows at"
                    + " most %d bytes per vector. Use the Recursive document splitter with a lower"
                    + " Max chars.")
                .formatted(i + 1, size, MAX_METADATA_BYTES));
      }
    }
  }

  /** Size of {"_page_content":"text"} as UTF-8 JSON, an upper bound of what S3 Vectors counts. */
  static int metadataSize(String text) {
    int size = TEXT_METADATA_KEY.length() + 7;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '"' || c == '\\' || c == '\n' || c == '\r' || c == '\t' || c == '\b' || c == '\f') {
        size += 2;
      } else if (c < 0x20) {
        size += 6;
      } else if (c < 0x80) {
        size += 1;
      } else if (c < 0x800) {
        size += 2;
      } else if (Character.isHighSurrogate(c)) {
        size += 4;
        i++;
      } else {
        size += 3;
      }
    }
    return size;
  }

  @Override
  protected EmbeddingStore<TextSegment> openForWrite(int dimension) {
    var index = findIndex().orElseGet(() -> createIndex(dimension));
    checkDimension(index, dimension);
    var metadata = index.metadataConfiguration();
    if (metadata == null || !metadata.nonFilterableMetadataKeys().contains(TEXT_METADATA_KEY)) {
      throw new ConnectorException(
          ("%s must declare '%s' as a non-filterable metadata key, because the chunk text is"
                  + " stored there. Use a new index name to let the connector create it.")
              .formatted(indexLabel, TEXT_METADATA_KEY));
    }
    return libraryStore(index);
  }

  @Override
  protected EmbeddingStore<TextSegment> openForRead(int dimension) {
    var index = findIndex().orElseThrow(() -> new ConnectorException(indexLabel + " not found"));
    checkDimension(index, dimension);
    return libraryStore(index);
  }

  private void checkDimension(Index index, int dimension) {
    if (index.dimension() != dimension) {
      throw new ConnectorException(
          "%s has %d dimensions, but the embedding model produces %d"
              .formatted(indexLabel, index.dimension(), dimension));
    }
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
        "%s was not available within %d seconds"
            .formatted(indexLabel, INDEX_READY_TIMEOUT.toSeconds()));
  }
}
