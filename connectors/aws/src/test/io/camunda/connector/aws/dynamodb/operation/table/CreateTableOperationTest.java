/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.table.CreateTable;
import io.camunda.connector.aws.model.AwsInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateTableOperationTest extends BaseDynamoDbOperationTest {

    private CreateTable createTable;

    @Captor
    private ArgumentCaptor<CreateTableRequest> requestArgumentCaptor;

    @BeforeEach
    public void init() throws InterruptedException {
        createTable = new CreateTable();
        createTable.setTableName(TestDynamoDBData.ActualValue.TABLE_NAME);
        //Partition key
        createTable.setPartitionKey(TestDynamoDBData.ActualValue.PARTITION_KEY);
        createTable.setPartitionKeyRole(TestDynamoDBData.ActualValue.PARTITION_KEY_ROLE_HASH);
        createTable.setPartitionKeyType(TestDynamoDBData.ActualValue.PARTITION_KEY_TYPE_NUMBER);
        //Sort key
        createTable.setSortKey(TestDynamoDBData.ActualValue.SORT_KEY);
        createTable.setSortKeyRole(TestDynamoDBData.ActualValue.SORT_KEY_ROLE_RANGE);
        createTable.setSortKeyType(TestDynamoDBData.ActualValue.SORT_KEY_TYPE_STRING);
        createTable.setDeletionProtection(true);
        createTable.setBillingModeStr(BillingMode.PROVISIONED.name());
        createTable.setReadCapacityUnits(TestDynamoDBData.ActualValue.READ_CAPACITY);
        createTable.setWriteCapacityUnits(TestDynamoDBData.ActualValue.WRITE_CAPACITY);

        when(dynamoDB.createTable(requestArgumentCaptor.capture())).thenReturn(table);
        when(table.waitForActive()).thenReturn(new TableDescription().withTableName(TestDynamoDBData.ActualValue.TABLE_NAME));


    }

    @Test
    public void invoke_shouldCreateTableWithPartitionKeyAndAllOptionalKeys() throws InterruptedException {
        //Given
        createTable.setSortKey(null);
        createTable.setSortKeyRole(null);
        createTable.setSortKeyType(null);
        createTable.setBillingModeStr(BillingMode.PROVISIONED.name());
        createTable.setReadCapacityUnits(null);
        createTable.setWriteCapacityUnits(null);

        CreateTableOperation operation = new CreateTableOperation(createTable);
        //When
        Object invoke = operation.invoke(dynamoDB);
        //Then
        verify(table, times(1)).waitForActive();

        assertThat(invoke).isNotNull();

        CreateTableRequest value = requestArgumentCaptor.getValue();

        assertThat(value.getTableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
        assertThat(value.getKeySchema().get(0).getAttributeName()).isEqualTo(TestDynamoDBData.ActualValue.PARTITION_KEY);
        assertThat(value.getKeySchema().get(0).getKeyType()).isEqualTo(TestDynamoDBData.ActualValue.PARTITION_KEY_ROLE_HASH);
        assertThat(value.getDeletionProtectionEnabled()).isTrue();
        assertThat(value.getBillingMode()).isEqualTo(BillingMode.PROVISIONED.name());
    }


    @Test
    public void invoke_shouldCreateTableWithOutOptionalProperties() throws InterruptedException {
        //Given
        CreateTableOperation operation = new CreateTableOperation(createTable);
        //When
        Object invoke = operation.invoke(dynamoDB);
        //Then
        verify(table, times(1)).waitForActive();

        assertThat(invoke).isNotNull();

        CreateTableRequest value = requestArgumentCaptor.getValue();

        assertThat(value.getTableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
        assertThat(value.getKeySchema().get(0).getAttributeName()).isEqualTo(TestDynamoDBData.ActualValue.PARTITION_KEY);
        assertThat(value.getKeySchema().get(0).getKeyType()).isEqualTo(TestDynamoDBData.ActualValue.PARTITION_KEY_ROLE_HASH);
        assertThat(value.getKeySchema().get(1).getAttributeName()).isEqualTo(TestDynamoDBData.ActualValue.SORT_KEY);
        assertThat(value.getKeySchema().get(1).getKeyType()).isEqualTo(TestDynamoDBData.ActualValue.SORT_KEY_ROLE_RANGE);
        assertThat(value.getDeletionProtectionEnabled()).isTrue();
        assertThat(value.getBillingMode()).isEqualTo(BillingMode.PROVISIONED.name());
        assertThat(value.getProvisionedThroughput().getReadCapacityUnits()).isEqualTo(TestDynamoDBData.ActualValue.READ_CAPACITY);
        assertThat(value.getProvisionedThroughput().getWriteCapacityUnits()).isEqualTo(TestDynamoDBData.ActualValue.WRITE_CAPACITY);
    }

    @Test
    public void replaceSecrets_shouldReplaceSecrets() {
        // Given
        String input = """
                {
                  "type": "createTable",
                  "partitionKey": "secrets.PARTITION_KEY",
                  "sortKey": "secrets.SORT_KEY",
                  "tableName": "secrets.TABLE_NAME_KEY"
                }
                """;
        OutboundConnectorContext context = getContextWithSecrets();
        AwsInput request = GSON.fromJson(input, AwsInput.class);
        // When
        context.replaceSecrets(request);
        // Then
        assertThat(request).isInstanceOf(CreateTable.class);
        CreateTable castedRequest = (CreateTable) request;
        assertThat(castedRequest.getTableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
        assertThat(castedRequest.getPartitionKey()).isEqualTo(TestDynamoDBData.ActualValue.PARTITION_KEY);
        assertThat(castedRequest.getSortKey()).isEqualTo(TestDynamoDBData.ActualValue.SORT_KEY);
    }

}