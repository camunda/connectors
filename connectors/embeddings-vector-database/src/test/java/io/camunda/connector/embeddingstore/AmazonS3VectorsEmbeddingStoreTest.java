/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.ConflictException;
import software.amazon.awssdk.services.s3vectors.model.CreateIndexRequest;
import software.amazon.awssdk.services.s3vectors.model.DataType;
import software.amazon.awssdk.services.s3vectors.model.DistanceMetric;
import software.amazon.awssdk.services.s3vectors.model.GetIndexRequest;
import software.amazon.awssdk.services.s3vectors.model.GetIndexResponse;
import software.amazon.awssdk.services.s3vectors.model.Index;
import software.amazon.awssdk.services.s3vectors.model.MetadataConfiguration;
import software.amazon.awssdk.services.s3vectors.model.NotFoundException;
import software.amazon.awssdk.services.s3vectors.model.PutVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryOutputVector;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsResponse;

class AmazonS3VectorsEmbeddingStoreTest {

  private static final String TEXT = AmazonS3VectorsEmbeddingStore.TEXT_METADATA_KEY;

  private final S3VectorsClient client = mock(S3VectorsClient.class);
  private final AmazonS3VectorsEmbeddingStore store =
      new AmazonS3VectorsEmbeddingStore(client, "eu-central-1", "bucket", "index");

  @Test
  void searchFailsWhenIndexIsMissing() {
    when(client.getIndex(any(GetIndexRequest.class))).thenThrow(notFound());

    assertThatThrownBy(() -> store.search(searchRequest(5)))
        .isInstanceOf(ConnectorException.class)
        .hasMessage(
            "Vector index 'index' not found in vector bucket 'bucket' in region 'eu-central-1'");
  }

  @Test
  void searchRejectsMoreThanOneHundredResults() {
    assertThatThrownBy(() -> store.search(searchRequest(101)))
        .isInstanceOf(ConnectorException.class)
        .hasMessage(
            "Amazon S3 Vectors returns at most 100 results per query, but 101 were requested");
    verifyNoInteractions(client);
  }

  @Test
  void searchScoresWithTheDistanceMetricOfTheIndex() {
    when(client.getIndex(any(GetIndexRequest.class)))
        .thenReturn(indexResponse(index(3, DistanceMetric.EUCLIDEAN, TEXT)));
    when(client.queryVectors(any(QueryVectorsRequest.class)))
        .thenReturn(
            QueryVectorsResponse.builder()
                .vectors(
                    QueryOutputVector.builder()
                        .key("id")
                        .distance(0.5f)
                        .metadata(Document.fromMap(Map.of(TEXT, Document.fromString("chunk"))))
                        .build())
                .build());

    var matches = store.search(searchRequest(5)).matches();

    assertThat(matches)
        .singleElement()
        .satisfies(match -> {
          assertThat(match.score()).isEqualTo(1.0 / 1.5);
          assertThat(match.embedded().text()).isEqualTo("chunk");
        });
  }

  @Test
  void writeCreatesMissingIndexAndStoresOnlyTheText() {
    when(client.getIndex(any(GetIndexRequest.class)))
        .thenThrow(notFound())
        .thenReturn(indexResponse(index(3, DistanceMetric.COSINE, TEXT)));

    store.addAll(
        List.of("id"),
        List.of(Embedding.from(new float[] {1, 2, 3})),
        List.of(TextSegment.from("chunk", Metadata.from("author", "someone"))));

    var create = ArgumentCaptor.forClass(CreateIndexRequest.class);
    verify(client).createIndex(create.capture());
    assertThat(create.getValue().dimension()).isEqualTo(3);
    assertThat(create.getValue().distanceMetric()).isEqualTo(DistanceMetric.COSINE);
    assertThat(create.getValue().dataType()).isEqualTo(DataType.FLOAT32);
    assertThat(create.getValue().metadataConfiguration().nonFilterableMetadataKeys())
        .containsExactly(TEXT);

    var put = ArgumentCaptor.forClass(PutVectorsRequest.class);
    verify(client).putVectors(put.capture());
    assertThat(put.getValue().vectors())
        .singleElement()
        .satisfies(vector -> assertThat(vector.metadata().asMap()).containsOnlyKeys(TEXT));
  }

  @Test
  void writeContinuesWhenTheIndexWasCreatedConcurrently() {
    when(client.getIndex(any(GetIndexRequest.class)))
        .thenThrow(notFound())
        .thenReturn(indexResponse(index(3, DistanceMetric.COSINE, TEXT)));
    when(client.createIndex(any(CreateIndexRequest.class)))
        .thenThrow(ConflictException.builder().message("exists").build());

    store.add(Embedding.from(new float[] {1, 2, 3}), TextSegment.from("chunk"));

    verify(client).putVectors(any(PutVectorsRequest.class));
  }

  @Test
  void writeChecksTheIndexOnlyOnce() {
    when(client.getIndex(any(GetIndexRequest.class)))
        .thenReturn(indexResponse(index(3, DistanceMetric.COSINE, TEXT)));

    store.add(Embedding.from(new float[] {1, 2, 3}), TextSegment.from("one"));
    store.add(Embedding.from(new float[] {1, 2, 3}), TextSegment.from("two"));

    verify(client, times(1)).getIndex(any(GetIndexRequest.class));
    verify(client, never()).createIndex(any(CreateIndexRequest.class));
    verify(client, times(2)).putVectors(any(PutVectorsRequest.class));
  }

  @Test
  void writeFailsWhenTheIndexHasOtherDimensions() {
    when(client.getIndex(any(GetIndexRequest.class)))
        .thenReturn(indexResponse(index(1024, DistanceMetric.COSINE, TEXT)));

    assertThatThrownBy(
            () -> store.add(Embedding.from(new float[] {1, 2, 3}), TextSegment.from("chunk")))
        .isInstanceOf(ConnectorException.class)
        .hasMessage("Vector index 'index' has 1024 dimensions, but the embedding model produces 3");
  }

  @Test
  void writeFailsWhenTheIndexKeepsTheTextFilterable() {
    when(client.getIndex(any(GetIndexRequest.class)))
        .thenReturn(indexResponse(index(3, DistanceMetric.COSINE)));

    assertThatThrownBy(
            () -> store.add(Embedding.from(new float[] {1, 2, 3}), TextSegment.from("chunk")))
        .isInstanceOf(ConnectorException.class)
        .hasMessageStartingWith(
            "Vector index 'index' must declare '_page_content' as a non-filterable metadata key");
  }

  private static EmbeddingSearchRequest searchRequest(int maxResults) {
    return EmbeddingSearchRequest.builder()
        .queryEmbedding(Embedding.from(new float[] {1, 2, 3}))
        .maxResults(maxResults)
        .build();
  }

  private static Index index(int dimension, DistanceMetric metric, String... nonFilterableKeys) {
    return Index.builder()
        .indexName("index")
        .dimension(dimension)
        .distanceMetric(metric)
        .metadataConfiguration(
            MetadataConfiguration.builder().nonFilterableMetadataKeys(nonFilterableKeys).build())
        .build();
  }

  private static GetIndexResponse indexResponse(Index index) {
    return GetIndexResponse.builder().index(index).build();
  }

  private static NotFoundException notFound() {
    return NotFoundException.builder().message("not found").build();
  }
}
