/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import com.amazonaws.services.dynamodbv2.model.TableDescription;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.table.DescribeTable;
import io.camunda.connector.aws.model.AwsInput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DescribeTableOperationTest extends BaseDynamoDbOperationTest {

    @Test
    public void invoke_shouldReturnDescribeTableResult() {
        //Given
        DescribeTable describeTable = new DescribeTable();
        describeTable.setTableName(TestDynamoDBData.ActualValue.TABLE_NAME);
        DescribeTableOperation operation = new DescribeTableOperation(describeTable);
        //When
        Object invoke = operation.invoke(dynamoDB);
        //Then
        assertThat(invoke).isNotNull();
        TableDescription result = (TableDescription) invoke;
        assertThat(result.getTableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
    }

    @Test
    public void replaceSecrets_shouldReplaceSecrets() {
        // Given
        String input = """
                {
                  "type": "describeTable",
                  "tableName": "secrets.TABLE_NAME_KEY"
                }
                """;
        OutboundConnectorContext context = getContextWithSecrets();
        AwsInput request = GSON.fromJson(input, AwsInput.class);
        // When
        context.replaceSecrets(request);
        // Then
        assertThat(request).isInstanceOf(DescribeTable.class);
        DescribeTable castedRequest = (DescribeTable) request;
        assertThat(castedRequest.getTableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
    }

}