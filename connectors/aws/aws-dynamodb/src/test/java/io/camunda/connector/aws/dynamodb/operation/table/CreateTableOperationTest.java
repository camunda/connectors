/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import io.camunda.connector.aws.dynamodb.model.CreateTable;
import io.camunda.connector.aws.dynamodb.model.TableDescriptionResult;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import software.amazon.awssdk.core.internal.waiters.ResponseOrException;
import software.amazon.awssdk.core.waiters.WaiterOverrideConfiguration;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.CreateTableResponse;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;

class CreateTableOperationTest extends BaseDynamoDbOperationTest {

  private CreateTable createTable;

  @Mock private DynamoDbWaiter waiter;
  @Captor private ArgumentCaptor<CreateTableRequest> requestArgumentCaptor;

  @BeforeEach
  public void init() {
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
    when(dynamoDbClient.createTable(requestArgumentCaptor.capture()))
        .thenReturn(CreateTableResponse.builder().build());
    when(dynamoDbClient.waiter()).thenReturn(waiter);
    stubWaiterSuccess(
        TableDescription.builder().tableName(TestDynamoDBData.ActualValue.TABLE_NAME).build());
  }

  @SuppressWarnings("unchecked")
  private void stubWaiterSuccess(final TableDescription tableDescription) {
    DescribeTableResponse describeResponse =
        DescribeTableResponse.builder().table(tableDescription).build();
    WaiterResponse<DescribeTableResponse> waiterResponse = mock(WaiterResponse.class);
    when(waiterResponse.matched()).thenReturn(ResponseOrException.response(describeResponse));
    when(waiter.waitUntilTableExists(
            any(DescribeTableRequest.class), any(WaiterOverrideConfiguration.class)))
        .thenReturn(waiterResponse);
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
    Object invoke = operation.invoke(dynamoDbClient);
    // Then
    verify(waiter, times(1))
        .waitUntilTableExists(
            any(DescribeTableRequest.class), any(WaiterOverrideConfiguration.class));

    assertThat(invoke).isNotNull();

    CreateTableRequest value = requestArgumentCaptor.getValue();

    assertThat(value.tableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
    assertThat(value.keySchema().get(0).attributeName())
        .isEqualTo(TestDynamoDBData.ActualValue.PARTITION_KEY);
    assertThat(value.keySchema().get(0).keyTypeAsString())
        .isEqualTo(TestDynamoDBData.ActualValue.PARTITION_KEY_ROLE_HASH);
    assertThat(value.deletionProtectionEnabled()).isTrue();
    assertThat(value.billingModeAsString()).isEqualTo(BillingMode.PROVISIONED.name());
  }

  /**
   * Regression guard for the waiter backoff: the v2 DynamoDbWaiter's TableExists default poll delay
   * is a fixed 20s backoff, and its override merge reads only the caller's {@code
   * backoffStrategyV2}, so {@code maxAttempts} alone would leave 20s spacing (only ~7 checks). The
   * operation must set an explicit 5s fixed backoff to reproduce v1's 25 x 5s behavior, with {@code
   * maxAttempts} as the sole binding constraint (no wall-clock cap).
   */
  @Test
  public void invoke_appliesFixedFiveSecondWaiterBackoff() {
    CreateTableOperation operation = new CreateTableOperation(createTable);

    operation.invoke(dynamoDbClient);

    ArgumentCaptor<WaiterOverrideConfiguration> configCaptor =
        ArgumentCaptor.forClass(WaiterOverrideConfiguration.class);
    verify(waiter).waitUntilTableExists(any(DescribeTableRequest.class), configCaptor.capture());
    WaiterOverrideConfiguration config = configCaptor.getValue();

    assertThat(config.maxAttempts()).contains(25);
    assertThat(config.waitTimeout()).isEmpty();
    assertThat(config.backoffStrategyV2()).isPresent();
    assertThat(config.backoffStrategyV2().get().computeDelay(1)).isEqualTo(Duration.ofSeconds(5));
    assertThat(config.backoffStrategyV2().get().computeDelay(10)).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  public void invoke_shouldCreateTableWithOutOptionalProperties() {
    // Given
    CreateTableOperation operation = new CreateTableOperation(createTable);
    // When
    Object invoke = operation.invoke(dynamoDbClient);
    // Then
    verify(waiter, times(1))
        .waitUntilTableExists(
            any(DescribeTableRequest.class), any(WaiterOverrideConfiguration.class));

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
    assertThat(value.billingModeAsString()).isEqualTo(BillingMode.PROVISIONED.name());
    assertThat(value.provisionedThroughput().readCapacityUnits())
        .isEqualTo(TestDynamoDBData.ActualValue.READ_CAPACITY);
    assertThat(value.provisionedThroughput().writeCapacityUnits())
        .isEqualTo(TestDynamoDBData.ActualValue.WRITE_CAPACITY);
  }

  /**
   * Golden-JSON shape test: pins the exact JSON the v1 createTable operation writes to process
   * variables today, so the AWS SDK v2 migration must reproduce it unchanged (migration contract
   * for #7973). {@link TableDescription} is a large, mostly-null v2 SDK type -- pins that shape,
   * including the ISO-8601 creationDateTime string (WRITE_DATES_AS_TIMESTAMPS is disabled).
   */
  @Test
  public void createTable_serializesToDocumentedV1JsonShape() throws Exception {
    // Given a live CreateTable call that waits for the table to become active
    TableDescription realDescription =
        buildRealisticTableDescription(TestDynamoDBData.ActualValue.TABLE_NAME);
    stubWaiterSuccess(realDescription);
    CreateTableOperation operation = new CreateTableOperation(createTable);

    // When
    Object result = operation.invoke(dynamoDbClient);

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
    assertThat(result).isInstanceOf(TableDescriptionResult.class);
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
