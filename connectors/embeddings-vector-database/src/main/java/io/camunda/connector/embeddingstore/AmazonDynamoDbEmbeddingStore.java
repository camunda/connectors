/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import dev.langchain4j.community.store.embedding.dynamodb.DynamoDbEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
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
import software.amazon.awssdk.services.dynamodb.model.VectorIndex;
import software.amazon.awssdk.services.dynamodb.model.VectorIndexDescription;

/**
 * Amazon DynamoDB store. An existing table is used with its own partition key, vector attribute and
 * distance function; a missing table is created with the LangChain4j defaults.
 */
class AmazonDynamoDbEmbeddingStore extends AwsVectorEmbeddingStore {

  static final String TEXT_ATTRIBUTE = DynamoDbEmbeddingStore.DEFAULT_TEXT_METADATA_KEY;
  private static final Duration TABLE_READY_TIMEOUT = Duration.ofSeconds(120);

  private final DynamoDbClient client;
  private final String tableName;
  private final String indexName;
  private final String tableLabel;
  private final String indexLabel;

  AmazonDynamoDbEmbeddingStore(
      DynamoDbClient client, String region, String tableName, String indexName) {
    this.client = client;
    this.tableName = tableName;
    this.indexName = indexName;
    this.tableLabel = "Table '%s' in region '%s'".formatted(tableName, region);
    this.indexLabel =
        "Vector index '%s' on table '%s' in region '%s'".formatted(indexName, tableName, region);
  }

  @Override
  protected String name() {
    return "Amazon DynamoDB";
  }

  @Override
  protected EmbeddingStore<TextSegment> openForWrite(int dimension) {
    var table = describeTable().orElse(null);
    if (table == null) {
      createTable(dimension);
    }
    if (table == null || table.tableStatus() != TableStatus.ACTIVE) {
      table = waitUntilActive();
    }
    var vectorIndex = vectorIndex(table, dimension);
    return libraryStore(table, vectorIndex);
  }

  @Override
  protected EmbeddingStore<TextSegment> openForRead(int dimension) {
    var table =
        describeTable().orElseThrow(() -> new ConnectorException(tableLabel + " not found"));
    var vectorIndex = vectorIndex(table, dimension);
    if (vectorIndex.indexStatus() != IndexStatus.ACTIVE) {
      throw new ConnectorException(
          "%s is not ready yet (status %s)"
              .formatted(indexLabel, vectorIndex.indexStatusAsString()));
    }
    return libraryStore(table, vectorIndex);
  }

  private VectorIndexDescription vectorIndex(TableDescription table, int dimension) {
    var vectorIndex =
        table.vectorIndexes().stream()
            .filter(index -> indexName.equals(index.indexName()))
            .findFirst()
            .orElseThrow(() ->
                new ConnectorException(
                    "%s has no vector index named '%s'".formatted(tableLabel, indexName)));
    if (vectorIndex.dimensions() != dimension) {
      throw new ConnectorException(
          "%s has %d dimensions, but the embedding model produces %d"
              .formatted(indexLabel, vectorIndex.dimensions(), dimension));
    }
    var projection = vectorIndex.projection();
    boolean projectsText =
        projection != null
            && (projection.projectionType() == ProjectionType.ALL
                || (projection.projectionType() == ProjectionType.INCLUDE
                    && projection.nonKeyAttributes().contains(TEXT_ATTRIBUTE)));
    if (!projectsText) {
      throw new ConnectorException(
          "%s must project the '%s' attribute, which holds the chunk text"
              .formatted(indexLabel, TEXT_ATTRIBUTE));
    }
    return vectorIndex;
  }

  private EmbeddingStore<TextSegment> libraryStore(
      TableDescription table, VectorIndexDescription vectorIndex) {
    if (table.keySchema().size() != 1) {
      throw new ConnectorException(tableLabel + " must have a partition key and no sort key");
    }
    var keyAttribute = table.keySchema().getFirst().attributeName();
    boolean stringKey =
        table.attributeDefinitions().stream()
            .anyMatch(definition ->
                keyAttribute.equals(definition.attributeName())
                    && definition.attributeType() == ScalarAttributeType.S);
    if (!stringKey) {
      throw new ConnectorException(
          "%s must have a string partition key, but '%s' is not a string"
              .formatted(tableLabel, keyAttribute));
    }
    return DynamoDbEmbeddingStore.builder()
        .dynamoDbClient(client)
        .tableName(tableName)
        .indexName(indexName)
        .keyAttribute(keyAttribute)
        .vectorAttribute(vectorIndex.vectorAttribute().attributeName())
        .distanceFunction(vectorIndex.distanceFunction())
        .createTableIfNotExists(false)
        .build();
  }

  private Optional<TableDescription> describeTable() {
    try {
      return Optional.of(
          client
              .describeTable(DescribeTableRequest.builder().tableName(tableName).build())
              .table());
    } catch (ResourceNotFoundException e) {
      return Optional.empty();
    }
  }

  private void createTable(int dimension) {
    var keyAttribute = DynamoDbEmbeddingStore.DEFAULT_KEY_ATTRIBUTE;
    try {
      client.createTable(
          CreateTableRequest.builder()
              .tableName(tableName)
              .billingMode(BillingMode.PAY_PER_REQUEST)
              .attributeDefinitions(
                  AttributeDefinition.builder()
                      .attributeName(keyAttribute)
                      .attributeType(ScalarAttributeType.S)
                      .build())
              .keySchema(
                  KeySchemaElement.builder()
                      .attributeName(keyAttribute)
                      .keyType(KeyType.HASH)
                      .build())
              .vectorIndexes(
                  VectorIndex.builder()
                      .indexName(indexName)
                      .vectorAttribute(
                          VectorAttributeDefinition.builder()
                              .attributeName(DynamoDbEmbeddingStore.DEFAULT_VECTOR_ATTRIBUTE)
                              .build())
                      .dimensions((long) dimension)
                      .distanceFunction(VectorDistanceFunction.COSINE)
                      .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                      .build())
              .build());
    } catch (ResourceInUseException e) {
      // created concurrently by another job
    }
  }

  private TableDescription waitUntilActive() {
    return AwsWait.until(
        () -> describeTable().filter(table -> table.tableStatus() == TableStatus.ACTIVE),
        TABLE_READY_TIMEOUT,
        "%s was not active within %d seconds"
            .formatted(tableLabel, TABLE_READY_TIMEOUT.toSeconds()));
  }
}
