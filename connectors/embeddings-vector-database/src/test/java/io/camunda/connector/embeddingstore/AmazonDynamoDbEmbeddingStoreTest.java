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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import io.camunda.connector.api.error.ConnectorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.IndexStatus;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.dynamodb.model.VectorAttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.VectorDistanceFunction;
import software.amazon.awssdk.services.dynamodb.model.VectorIndexDescription;

class AmazonDynamoDbEmbeddingStoreTest {

  private static final String TABLE = "Table 'table' in region 'eu-central-1'";
  private static final String INDEX =
      "Vector index 'index' on table 'table' in region 'eu-central-1'";

  private final DynamoDbClient client = mock(DynamoDbClient.class);
  private final AmazonDynamoDbEmbeddingStore store =
      new AmazonDynamoDbEmbeddingStore(client, "eu-central-1", "table", "index");

  @BeforeEach
  void acceptWrites() {
    when(client.batchWriteItem(any(BatchWriteItemRequest.class)))
        .thenReturn(BatchWriteItemResponse.builder().build());
  }

  @Test
  void searchFailsWhenTableIsMissing() {
    when(client.describeTable(any(DescribeTableRequest.class))).thenThrow(notFound());

    assertThatThrownBy(() -> store.search(searchRequest(5)))
        .isInstanceOf(ConnectorException.class)
        .hasMessage(TABLE + " not found");
  }

  @Test
  void searchFailsWhenTheIndexHasOtherDimensions() {
    when(client.describeTable(any(DescribeTableRequest.class)))
        .thenReturn(described(table("id", vectorIndex("index", ProjectionType.ALL, "embedding"))));

    assertThatThrownBy(() ->
            store.search(
                EmbeddingSearchRequest.builder()
                    .queryEmbedding(Embedding.from(new float[] {1, 2}))
                    .maxResults(5)
                    .build()))
        .isInstanceOf(ConnectorException.class)
        .hasMessage(INDEX + " has 3 dimensions, but the embedding model produces 2");
  }

  @Test
  void searchFailsWhenTableHasNoSuchVectorIndex() {
    when(client.describeTable(any(DescribeTableRequest.class)))
        .thenReturn(described(table("id", vectorIndex("other", ProjectionType.ALL, "embedding"))));

    assertThatThrownBy(() -> store.search(searchRequest(5)))
        .isInstanceOf(ConnectorException.class)
        .hasMessage(TABLE + " has no vector index named 'index'");
  }

  @Test
  void searchFailsWhenVectorIndexDoesNotProjectTheText() {
    when(client.describeTable(any(DescribeTableRequest.class)))
        .thenReturn(
            described(table("id", vectorIndex("index", ProjectionType.KEYS_ONLY, "embedding"))));

    assertThatThrownBy(() -> store.search(searchRequest(5)))
        .isInstanceOf(ConnectorException.class)
        .hasMessage(
            INDEX + " must project the '_page_content' attribute, which holds the chunk text");
  }

  @Test
  void searchRejectsMoreThanOneHundredResults() {
    assertThatThrownBy(() -> store.search(searchRequest(101)))
        .isInstanceOf(ConnectorException.class)
        .hasMessage(
            "Amazon DynamoDB returns at most 100 results per query, but 101 were requested");
    verifyNoInteractions(client);
  }

  @Test
  void writeCreatesMissingTable() {
    when(client.describeTable(any(DescribeTableRequest.class)))
        .thenThrow(notFound())
        .thenReturn(described(table("id", vectorIndex("index", ProjectionType.ALL, "embedding"))));

    store.add(Embedding.from(new float[] {1, 2, 3}), TextSegment.from("chunk"));

    var create = ArgumentCaptor.forClass(CreateTableRequest.class);
    verify(client).createTable(create.capture());
    assertThat(create.getValue().billingMode()).isEqualTo(BillingMode.PAY_PER_REQUEST);
    assertThat(create.getValue().vectorIndexes())
        .singleElement()
        .satisfies(index -> {
          assertThat(index.indexName()).isEqualTo("index");
          assertThat(index.dimensions()).isEqualTo(3L);
          assertThat(index.distanceFunction()).isEqualTo(VectorDistanceFunction.COSINE);
          assertThat(index.projection().projectionType()).isEqualTo(ProjectionType.ALL);
        });
    verify(client).batchWriteItem(any(BatchWriteItemRequest.class));
  }

  @Test
  void writeContinuesWhenTheTableWasCreatedConcurrently() {
    when(client.describeTable(any(DescribeTableRequest.class)))
        .thenThrow(notFound())
        .thenReturn(described(table("id", vectorIndex("index", ProjectionType.ALL, "embedding"))));
    when(client.createTable(any(CreateTableRequest.class)))
        .thenThrow(ResourceInUseException.builder().message("in use").build());

    store.add(Embedding.from(new float[] {1, 2, 3}), TextSegment.from("chunk"));

    verify(client).batchWriteItem(any(BatchWriteItemRequest.class));
  }

  @Test
  void writeUsesTheKeyAndVectorAttributesOfAnExistingTable() {
    when(client.describeTable(any(DescribeTableRequest.class)))
        .thenReturn(described(table("pk", vectorIndex("index", ProjectionType.ALL, "vector"))));

    store.add(
        Embedding.from(new float[] {1, 2, 3}),
        TextSegment.from("chunk", Metadata.from("author", "someone")));

    var write = ArgumentCaptor.forClass(BatchWriteItemRequest.class);
    verify(client).batchWriteItem(write.capture());
    assertThat(write.getValue().requestItems().get("table"))
        .singleElement()
        .satisfies(request ->
            assertThat(request.putRequest().item())
                .containsOnlyKeys("pk", "vector", AmazonDynamoDbEmbeddingStore.TEXT_ATTRIBUTE));
  }

  @Test
  void writeFailsWhenTheTableHasASortKey() {
    var table =
        table("id", vectorIndex("index", ProjectionType.ALL, "embedding")).toBuilder()
            .keySchema(
                KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build(),
                KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
            .build();
    when(client.describeTable(any(DescribeTableRequest.class))).thenReturn(described(table));

    assertThatThrownBy(
            () -> store.add(Embedding.from(new float[] {1, 2, 3}), TextSegment.from("chunk")))
        .isInstanceOf(ConnectorException.class)
        .hasMessage(TABLE + " must have a partition key and no sort key");
  }

  private static EmbeddingSearchRequest searchRequest(int maxResults) {
    return EmbeddingSearchRequest.builder()
        .queryEmbedding(Embedding.from(new float[] {1, 2, 3}))
        .maxResults(maxResults)
        .build();
  }

  private static VectorIndexDescription vectorIndex(
      String name, ProjectionType projection, String vectorAttribute) {
    return VectorIndexDescription.builder()
        .indexName(name)
        .dimensions(3L)
        .distanceFunction(VectorDistanceFunction.COSINE)
        .indexStatus(IndexStatus.ACTIVE)
        .vectorAttribute(VectorAttributeDefinition.builder().attributeName(vectorAttribute).build())
        .projection(Projection.builder().projectionType(projection).build())
        .build();
  }

  private static TableDescription table(String keyAttribute, VectorIndexDescription vectorIndex) {
    return TableDescription.builder()
        .tableName("table")
        .tableStatus(TableStatus.ACTIVE)
        .keySchema(
            KeySchemaElement.builder().attributeName(keyAttribute).keyType(KeyType.HASH).build())
        .attributeDefinitions(
            AttributeDefinition.builder()
                .attributeName(keyAttribute)
                .attributeType(ScalarAttributeType.S)
                .build())
        .vectorIndexes(vectorIndex)
        .build();
  }

  private static DescribeTableResponse described(TableDescription table) {
    return DescribeTableResponse.builder().table(table).build();
  }

  private static ResourceNotFoundException notFound() {
    return ResourceNotFoundException.builder().message("not found").build();
  }
}
