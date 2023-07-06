/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import io.camunda.connector.aws.dynamodb.model.DescribeTable;
import org.junit.jupiter.api.Test;

class DescribeTableOperationTest extends BaseDynamoDbOperationTest {

  @Test
  public void invoke_shouldReturnDescribeTableResult() {
    // Given
    DescribeTable describeTable = new DescribeTable();
    describeTable.setTableName(TestDynamoDBData.ActualValue.TABLE_NAME);
    DescribeTableOperation operation = new DescribeTableOperation(describeTable);
    // When
    Object invoke = operation.invoke(dynamoDB);
    // Then
    assertThat(invoke).isNotNull();
    TableDescription result = (TableDescription) invoke;
    assertThat(result.getTableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
  }

  @Test
  public void replaceSecrets_shouldReplaceSecrets() throws JsonProcessingException {
    // Given
    String input =
        """
                {
                  "type": "describeTable",
                  "tableName": "secrets.TABLE_NAME_KEY"
                }
                """;
    OutboundConnectorContext context = getContextWithSecrets(input);
    AwsInput request = context.bindVariables(AwsInput.class);
    // Then
    assertThat(request).isInstanceOf(DescribeTable.class);
    DescribeTable castedRequest = (DescribeTable) request;
    assertThat(castedRequest.getTableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
  }
}
