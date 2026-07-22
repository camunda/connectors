/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.aws.ObjectMapperSupplier;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.IndexStatus;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.LocalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputDescription;
import software.amazon.awssdk.services.dynamodb.model.ReplicaDescription;
import software.amazon.awssdk.services.dynamodb.model.ReplicaStatus;
import software.amazon.awssdk.services.dynamodb.model.SSEDescription;
import software.amazon.awssdk.services.dynamodb.model.SSEStatus;
import software.amazon.awssdk.services.dynamodb.model.SSEType;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

/**
 * Focused coverage for the hand-written {@link TableDescriptionResult} compatibility bridge's
 * local/global-secondary-index mapping. The create/describe golden-JSON fixtures both leave these
 * collections null (the tables this connector creates have no indexes), so a mistake in {@code
 * LocalSecondaryIndexDescriptionResult}/{@code GlobalSecondaryIndexDescriptionResult} -- or in the
 * nested {@code KeySchemaElementResult}/{@code ProjectionResult}/{@code
 * ProvisionedThroughputResult} mappers -- would ship while every other test stays green. This test
 * describes a table that some other application configured with indexes and pins their serialized
 * shape.
 */
class TableDescriptionResultTest {

  private static final ObjectMapper objectMapper = ObjectMapperSupplier.getMapperInstance();

  private static final String TABLE_ARN = "arn:aws:dynamodb:us-east-1:123456789012:table/my_table";

  @Test
  void from_mapsLocalSecondaryIndexes_toDocumentedSerializedShape() throws Exception {
    LocalSecondaryIndexDescription lsi =
        LocalSecondaryIndexDescription.builder()
            .indexName("lsi-1")
            .keySchema(
                KeySchemaElement.builder().attributeName("ID").keyType(KeyType.HASH).build(),
                KeySchemaElement.builder()
                    .attributeName("createdAt")
                    .keyType(KeyType.RANGE)
                    .build())
            .projection(
                Projection.builder()
                    .projectionType(ProjectionType.INCLUDE)
                    .nonKeyAttributes("attrA", "attrB")
                    .build())
            .indexSizeBytes(100L)
            .itemCount(5L)
            .indexArn(TABLE_ARN + "/index/lsi-1")
            .build();
    TableDescription table =
        TableDescription.builder().tableName("my_table").localSecondaryIndexes(lsi).build();

    TableDescriptionResult result = TableDescriptionResult.from(table);

    assertThat(result.localSecondaryIndexes()).hasSize(1);
    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(result));
    String expectedJson =
        """
        [
          {
            "indexName": "lsi-1",
            "keySchema": [
              { "attributeName": "ID", "keyType": "HASH" },
              { "attributeName": "createdAt", "keyType": "RANGE" }
            ],
            "projection": {
              "projectionType": "INCLUDE",
              "nonKeyAttributes": ["attrA", "attrB"]
            },
            "indexSizeBytes": 100,
            "itemCount": 5,
            "indexArn": "arn:aws:dynamodb:us-east-1:123456789012:table/my_table/index/lsi-1"
          }
        ]
        """;
    assertThat(json.get("localSecondaryIndexes")).isEqualTo(objectMapper.readTree(expectedJson));
  }

  @Test
  void from_mapsGlobalSecondaryIndexes_toDocumentedSerializedShape() throws Exception {
    GlobalSecondaryIndexDescription gsi =
        GlobalSecondaryIndexDescription.builder()
            .indexName("gsi-1")
            .keySchema(
                KeySchemaElement.builder().attributeName("gsiKey").keyType(KeyType.HASH).build())
            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
            .indexStatus(IndexStatus.ACTIVE)
            .backfilling(false)
            .provisionedThroughput(
                ProvisionedThroughputDescription.builder()
                    .readCapacityUnits(1L)
                    .writeCapacityUnits(2L)
                    .build())
            .indexSizeBytes(200L)
            .itemCount(10L)
            .indexArn(TABLE_ARN + "/index/gsi-1")
            .build();
    TableDescription table =
        TableDescription.builder().tableName("my_table").globalSecondaryIndexes(gsi).build();

    TableDescriptionResult result = TableDescriptionResult.from(table);

    assertThat(result.globalSecondaryIndexes()).hasSize(1);
    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(result));
    String expectedJson =
        """
        [
          {
            "indexName": "gsi-1",
            "keySchema": [ { "attributeName": "gsiKey", "keyType": "HASH" } ],
            "projection": { "projectionType": "ALL", "nonKeyAttributes": null },
            "indexStatus": "ACTIVE",
            "backfilling": false,
            "provisionedThroughput": {
              "lastIncreaseDateTime": null,
              "lastDecreaseDateTime": null,
              "numberOfDecreasesToday": null,
              "readCapacityUnits": 1,
              "writeCapacityUnits": 2
            },
            "indexSizeBytes": 200,
            "itemCount": 10,
            "indexArn": "arn:aws:dynamodb:us-east-1:123456789012:table/my_table/index/gsi-1",
            "onDemandThroughput": null,
            "warmThroughput": null
          }
        ]
        """;
    assertThat(json.get("globalSecondaryIndexes")).isEqualTo(objectMapper.readTree(expectedJson));
  }

  @Test
  void from_mapsSseDescription_toDocumentedLegacyManglingSerializedShape() throws Exception {
    SSEDescription sse =
        SSEDescription.builder()
            .status(SSEStatus.ENABLED)
            .sseType(SSEType.KMS)
            .kmsMasterKeyArn("arn:aws:kms:us-east-1:123456789012:key/my-key")
            .build();
    TableDescription table =
        TableDescription.builder().tableName("my_table").sseDescription(sse).build();

    TableDescriptionResult result = TableDescriptionResult.from(table);

    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(result));
    // v1's Jackson bean introspection mangles a getter with 2+ leading capitals (e.g.
    // getSSEType(), getKMSMasterKeyArn()) by lower-casing every leading capital run, producing
    // "ssetype"/"kmsmasterKeyArn" rather than the intuitive camelCase "sseType"/"kmsMasterKeyArn"
    // - the same quirk already pinned for the top-level "ssedescription" key.
    String expectedJson =
        """
        {
          "status": "ENABLED",
          "ssetype": "KMS",
          "kmsmasterKeyArn": "arn:aws:kms:us-east-1:123456789012:key/my-key",
          "inaccessibleEncryptionDateTime": null
        }
        """;
    assertThat(json.get("ssedescription")).isEqualTo(objectMapper.readTree(expectedJson));
  }

  @Test
  void from_mapsReplicas_toDocumentedLegacyManglingSerializedShape() throws Exception {
    ReplicaDescription replica =
        ReplicaDescription.builder()
            .regionName("eu-central-1")
            .replicaStatus(ReplicaStatus.ACTIVE)
            .kmsMasterKeyId("arn:aws:kms:eu-central-1:123456789012:key/replica-key")
            .build();
    TableDescription table =
        TableDescription.builder().tableName("my_table").replicas(replica).build();

    TableDescriptionResult result = TableDescriptionResult.from(table);

    assertThat(result.replicas()).hasSize(1);
    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(result));
    // Same legacy-mangling quirk as SSEDescription: v1's getKMSMasterKeyId() serializes as
    // "kmsmasterKeyId", not "kmsMasterKeyId".
    String expectedJson =
        """
        [
          {
            "regionName": "eu-central-1",
            "replicaStatus": "ACTIVE",
            "replicaArn": null,
            "replicaStatusDescription": null,
            "replicaStatusPercentProgress": null,
            "kmsmasterKeyId": "arn:aws:kms:eu-central-1:123456789012:key/replica-key",
            "provisionedThroughputOverride": null,
            "onDemandThroughputOverride": null,
            "warmThroughput": null,
            "globalSecondaryIndexes": null,
            "replicaInaccessibleDateTime": null,
            "replicaTableClassSummary": null
          }
        ]
        """;
    assertThat(json.get("replicas")).isEqualTo(objectMapper.readTree(expectedJson));
  }
}
