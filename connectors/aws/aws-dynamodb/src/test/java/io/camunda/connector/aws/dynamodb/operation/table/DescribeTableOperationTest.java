/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.fasterxml.jackson.databind.JsonNode;
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
    DescribeTable describeTable = new DescribeTable(TestDynamoDBData.ActualValue.TABLE_NAME);
    DescribeTableOperation operation = new DescribeTableOperation(describeTable);
    // When
    Object invoke = operation.invoke(dynamoDB);
    // Then
    assertThat(invoke).isNotNull();
    TableDescription result = (TableDescription) invoke;
    assertThat(result.getTableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
  }

  /**
   * Golden-JSON shape test: pins the exact JSON the v1 describeTable operation writes to process
   * variables today, so the AWS SDK v2 migration must reproduce it unchanged (migration contract
   * for #7973). Shares the same {@link TableDescription} shape as createTable (see
   * CreateTableOperationTest) -- both operations return the identical v1 type.
   */
  @Test
  public void describeTable_serializesToDocumentedV1JsonShape() throws Exception {
    TableDescription realDescription =
        buildRealisticTableDescription(TestDynamoDBData.ActualValue.TABLE_NAME);
    when(table.describe()).thenReturn(realDescription);
    DescribeTable describeTable = new DescribeTable(TestDynamoDBData.ActualValue.TABLE_NAME);
    DescribeTableOperation operation = new DescribeTableOperation(describeTable);

    Object result = operation.invoke(dynamoDB);

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
    // Deliberately no exact writeValueAsString() pin: see CreateTableOperationTest for why.
  }

  @Test
  public void replaceSecrets_shouldReplaceSecrets() {
    // Given
    String input =
        """
                {
                  "type": "describeTable",
                  "tableName": "{{secrets.TABLE_NAME_KEY}}"
                }
                """;
    OutboundConnectorContext context = getContextWithSecrets(input);
    AwsInput request = context.bindVariables(AwsInput.class);
    // Then
    assertThat(request).isInstanceOf(DescribeTable.class);
    DescribeTable castedRequest = (DescribeTable) request;
    assertThat(castedRequest.tableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
  }
}
