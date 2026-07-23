/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import io.camunda.connector.aws.dynamodb.model.CreateTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

class CreateTableOperationTest extends BaseDynamoDbOperationTest {

  private CreateTable createTable;

  @Captor private ArgumentCaptor<CreateTableRequest> requestArgumentCaptor;

  @BeforeEach
  public void init() throws InterruptedException {
    createTable =
        new CreateTable(
            TestDynamoDBData.ActualValue.TABLE_NAME,
            TestDynamoDBData.ActualValue.PARTITION_KEY,
            TestDynamoDBData.ActualValue.PARTITION_KEY_ROLE_HASH,
            TestDynamoDBData.ActualValue.PARTITION_KEY_TYPE_NUMBER,
            TestDynamoDBData.ActualValue.SORT_KEY,
            TestDynamoDBData.ActualValue.SORT_KEY_ROLE_RANGE,
            TestDynamoDBData.ActualValue.SORT_KEY_TYPE_STRING,
            TestDynamoDBData.ActualValue.READ_CAPACITY,
            TestDynamoDBData.ActualValue.WRITE_CAPACITY,
            BillingMode.PROVISIONED.name(),
            true);
    when(dynamoDB.createTable(requestArgumentCaptor.capture())).thenReturn(table);
    when(table.waitForActive())
        .thenReturn(new TableDescription().withTableName(TestDynamoDBData.ActualValue.TABLE_NAME));
  }

  @Test
  public void invoke_shouldCreateTableWithPartitionKeyAndAllOptionalKeys()
      throws InterruptedException {
    // Given
    createTable =
        new CreateTable(
            TestDynamoDBData.ActualValue.TABLE_NAME,
            TestDynamoDBData.ActualValue.PARTITION_KEY,
            TestDynamoDBData.ActualValue.PARTITION_KEY_ROLE_HASH,
            TestDynamoDBData.ActualValue.PARTITION_KEY_TYPE_NUMBER,
            null,
            null,
            null,
            null,
            null,
            BillingMode.PROVISIONED.name(),
            true);
    CreateTableOperation operation = new CreateTableOperation(createTable);
    // When
    Object invoke = operation.invoke(dynamoDB);
    // Then
    verify(table, times(1)).waitForActive();

    assertThat(invoke).isNotNull();

    CreateTableRequest value = requestArgumentCaptor.getValue();

    assertThat(value.getTableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
    assertThat(value.getKeySchema().get(0).getAttributeName())
        .isEqualTo(TestDynamoDBData.ActualValue.PARTITION_KEY);
    assertThat(value.getKeySchema().get(0).getKeyType())
        .isEqualTo(TestDynamoDBData.ActualValue.PARTITION_KEY_ROLE_HASH);
    assertThat(value.getDeletionProtectionEnabled()).isTrue();
    assertThat(value.getBillingMode()).isEqualTo(BillingMode.PROVISIONED.name());
  }

  @Test
  public void invoke_shouldCreateTableWithOutOptionalProperties() throws InterruptedException {
    // Given
    CreateTableOperation operation = new CreateTableOperation(createTable);
    // When
    Object invoke = operation.invoke(dynamoDB);
    // Then
    verify(table, times(1)).waitForActive();

    assertThat(invoke).isNotNull();

    CreateTableRequest value = requestArgumentCaptor.getValue();

    assertThat(value.getTableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
    assertThat(value.getKeySchema().get(0).getAttributeName())
        .isEqualTo(TestDynamoDBData.ActualValue.PARTITION_KEY);
    assertThat(value.getKeySchema().get(0).getKeyType())
        .isEqualTo(TestDynamoDBData.ActualValue.PARTITION_KEY_ROLE_HASH);
    assertThat(value.getKeySchema().get(1).getAttributeName())
        .isEqualTo(TestDynamoDBData.ActualValue.SORT_KEY);
    assertThat(value.getKeySchema().get(1).getKeyType())
        .isEqualTo(TestDynamoDBData.ActualValue.SORT_KEY_ROLE_RANGE);
    assertThat(value.getDeletionProtectionEnabled()).isTrue();
    assertThat(value.getBillingMode()).isEqualTo(BillingMode.PROVISIONED.name());
    assertThat(value.getProvisionedThroughput().getReadCapacityUnits())
        .isEqualTo(TestDynamoDBData.ActualValue.READ_CAPACITY);
    assertThat(value.getProvisionedThroughput().getWriteCapacityUnits())
        .isEqualTo(TestDynamoDBData.ActualValue.WRITE_CAPACITY);
  }

  /**
   * Golden-JSON shape test: pins the exact JSON the v1 createTable operation writes to process
   * variables today, so the AWS SDK v2 migration must reproduce it unchanged (migration contract
   * for #7973). {@link TableDescription} is a large, mostly-null v1 SDK POJO -- pins that shape,
   * including the ISO-8601 creationDateTime string (WRITE_DATES_AS_TIMESTAMPS is disabled).
   */
  @Test
  public void createTable_serializesToDocumentedV1JsonShape() throws Exception {
    // Given a live CreateTable call that waits for the table to become active
    TableDescription realDescription =
        buildRealisticTableDescription(TestDynamoDBData.ActualValue.TABLE_NAME);
    when(dynamoDB.createTable(requestArgumentCaptor.capture())).thenReturn(table);
    when(table.waitForActive()).thenReturn(realDescription);
    CreateTableOperation operation = new CreateTableOperation(createTable);

    // When
    Object result = operation.invoke(dynamoDB);

    // Then
    JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(result));
    String expectedJson =
        """
        {
          "attributeDefinitions": [
            { "attributeName": "ID", "attributeType": "N" },
            { "attributeName": "sortKey", "attributeType": "S" }
          ],
          "tableName": "my_table",
          "keySchema": [
            { "attributeName": "ID", "keyType": "HASH" },
            { "attributeName": "sortKey", "keyType": "RANGE" }
          ],
          "tableStatus": "ACTIVE",
          "creationDateTime": "2023-11-14T22:13:20.000+00:00",
          "provisionedThroughput": {
            "lastIncreaseDateTime": null,
            "lastDecreaseDateTime": null,
            "numberOfDecreasesToday": null,
            "readCapacityUnits": 4,
            "writeCapacityUnits": 5
          },
          "tableSizeBytes": 0,
          "itemCount": 0,
          "tableArn": "arn:aws:dynamodb:us-east-1:123456789012:table/my_table",
          "tableId": null,
          "billingModeSummary": {
            "billingMode": "PROVISIONED",
            "lastUpdateToPayPerRequestDateTime": null
          },
          "localSecondaryIndexes": null,
          "globalSecondaryIndexes": null,
          "streamSpecification": null,
          "latestStreamLabel": null,
          "latestStreamArn": null,
          "globalTableVersion": null,
          "replicas": null,
          "restoreSummary": null,
          "archivalSummary": null,
          "tableClassSummary": null,
          "deletionProtectionEnabled": null,
          "onDemandThroughput": null,
          "ssedescription": null
        }
        """;
    JsonNode expected = objectMapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);
    // Deliberately no exact writeValueAsString() pin: TableDescription is a plain, unannotated
    // AWS SDK v1 JavaBean with ~25 properties and no @JsonPropertyOrder; see
    // AddItemOperationTest for why we don't rely on Jackson's reflection-based property order for
    // such types. Tree equality above pins the shape (keys, nesting, values, and the many
    // explicit nulls) reliably instead.
  }

  @Test
  public void replaceSecrets_shouldReplaceSecrets() {
    // Given
    String input =
        """
                {
                  "type": "createTable",
                  "partitionKey": "{{secrets.PARTITION_KEY}}",
                  "partitionKeyRole": "{{secrets.PARTITION_KEY}}",
                  "partitionKeyType": "{{secrets.PARTITION_KEY}}",
                  "sortKey": "{{secrets.SORT_KEY}}",
                  "tableName": "{{secrets.TABLE_NAME_KEY}}",
                  "billingModeStr": "PAY_PER_REQUEST",
                  "readCapacityUnits":"1",
                  "writeCapacityUnits":"1"
                }
                """;
    OutboundConnectorContext context = getContextWithSecrets(input);
    AwsInput request = context.bindVariables(AwsInput.class);
    // Then
    assertThat(request).isInstanceOf(CreateTable.class);
    CreateTable castedRequest = (CreateTable) request;
    assertThat(castedRequest.tableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
    assertThat(castedRequest.partitionKey()).isEqualTo(TestDynamoDBData.ActualValue.PARTITION_KEY);
    assertThat(castedRequest.sortKey()).isEqualTo(TestDynamoDBData.ActualValue.SORT_KEY);
  }
}
