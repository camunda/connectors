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

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AwsDynamoDbResult;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import io.camunda.connector.aws.dynamodb.model.DeleteTable;
import org.junit.jupiter.api.Test;

class DeleteTableOperationTest extends BaseDynamoDbOperationTest {

  @Test
  public void invoke_shouldDeleteDynamoDbTableAndReturnStatusOk() throws InterruptedException {
    // Given
    DeleteTable deleteTable = new DeleteTable(TestDynamoDBData.ActualValue.TABLE_NAME);
    DeleteTableOperation operation = new DeleteTableOperation(deleteTable);
    // When
    Object invoke = operation.invoke(dynamoDB);
    // Then
    verify(table, times(1)).delete();
    verify(table, times(1)).waitForDelete();

    assertThat(invoke).isNotNull();
    AwsDynamoDbResult result = (AwsDynamoDbResult) invoke;
    assertThat(result.getAction())
        .isEqualTo("delete Table [" + TestDynamoDBData.ActualValue.TABLE_NAME + "]");
    assertThat(result.getStatus()).isEqualTo("OK");
  }

  /**
   * Golden-JSON shape test: pins the exact JSON the v1 deleteTable operation writes to process
   * variables today, so the AWS SDK v2 migration must reproduce it unchanged (migration contract
   * for #7973). {@link AwsDynamoDbResult} is a connector-owned envelope (not a raw AWS SDK type),
   * but deleteTable never sets its {@code response} field -- pinning that it serializes as an
   * explicit JSON null, not an absent key.
   */
  @Test
  public void deleteTable_serializesToDocumentedV1JsonShape() throws Exception {
    DeleteTable deleteTable = new DeleteTable(TestDynamoDBData.ActualValue.TABLE_NAME);
    DeleteTableOperation operation = new DeleteTableOperation(deleteTable);

    Object result = operation.invoke(dynamoDB);

    JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(result));
    String expectedJson =
        """
        {
          "action": "delete Table [my_table]",
          "status": "OK",
          "response": null
        }
        """;
    JsonNode expected = objectMapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);
    // Deliberately no exact writeValueAsString() pin: AwsDynamoDbResult is a plain JavaBean with
    // no @JsonPropertyOrder; see AddItemOperationTest for why we don't rely on Jackson's
    // reflection-based property order even for our own unannotated types.
  }

  @Test
  public void replaceSecrets_shouldReplaceSecrets() {
    // Given
    String input =
        """
                {
                  "type": "deleteTable",
                  "tableName": "{{secrets.TABLE_NAME_KEY}}"
                }
                """;
    OutboundConnectorContext context = getContextWithSecrets(input);
    AwsInput request = context.bindVariables(AwsInput.class);
    // Then
    assertThat(request).isInstanceOf(DeleteTable.class);
    DeleteTable castedRequest = (DeleteTable) request;
    assertThat(castedRequest.tableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
  }
}
