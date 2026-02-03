/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import io.camunda.connector.aws.dynamodb.model.CreateTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.CreateTableResponse;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

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
    when(dynamoDB.createTable(requestArgumentCaptor.capture()))
        .thenReturn(
            CreateTableResponse.builder()
                .tableDescription(
                    TableDescription.builder()
                        .tableName(TestDynamoDBData.ActualValue.TABLE_NAME)
                        .build())
                .build());
  }

  @Test
  public void invoke_shouldCreateTableWithPartitionKeyAndAllOptionalKeys() {
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
    assertThat(invoke).isNotNull();

    CreateTableRequest value = requestArgumentCaptor.getValue();

    assertThat(value.tableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
    assertThat(value.keySchema().get(0).attributeName())
        .isEqualTo(TestDynamoDBData.ActualValue.PARTITION_KEY);
    assertThat(value.keySchema().get(0).keyTypeAsString())
        .isEqualTo(TestDynamoDBData.ActualValue.PARTITION_KEY_ROLE_HASH);
    assertThat(value.deletionProtectionEnabled()).isTrue();
    assertThat(value.billingMode()).isEqualTo(BillingMode.PROVISIONED);
  }

  @Test
  public void invoke_shouldCreateTableWithOutOptionalProperties() {
    // Given
    CreateTableOperation operation = new CreateTableOperation(createTable);
    // When
    Object invoke = operation.invoke(dynamoDB);
    // Then
    assertThat(invoke).isNotNull();

    CreateTableRequest value = requestArgumentCaptor.getValue();

    assertThat(value.tableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
    assertThat(value.keySchema().get(0).attributeName())
        .isEqualTo(TestDynamoDBData.ActualValue.PARTITION_KEY);
    assertThat(value.keySchema().get(0).keyTypeAsString())
        .isEqualTo(TestDynamoDBData.ActualValue.PARTITION_KEY_ROLE_HASH);
    assertThat(value.keySchema().get(1).attributeName())
        .isEqualTo(TestDynamoDBData.ActualValue.SORT_KEY);
    assertThat(value.keySchema().get(1).keyTypeAsString())
        .isEqualTo(TestDynamoDBData.ActualValue.SORT_KEY_ROLE_RANGE);
    assertThat(value.deletionProtectionEnabled()).isTrue();
    assertThat(value.billingMode()).isEqualTo(BillingMode.PROVISIONED);
    assertThat(value.provisionedThroughput().readCapacityUnits())
        .isEqualTo(TestDynamoDBData.ActualValue.READ_CAPACITY);
    assertThat(value.provisionedThroughput().writeCapacityUnits())
        .isEqualTo(TestDynamoDBData.ActualValue.WRITE_CAPACITY);
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
