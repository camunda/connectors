/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AwsDynamoDbResult;
import io.camunda.connector.aws.dynamodb.model.ScanTable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

class ScanTableOperationTest extends BaseDynamoDbOperationTest {

  @Captor private ArgumentCaptor<ScanRequest> requestCaptor;
  private ScanTableOperation scanTableOperation;
  private ScanTable scanTable;

  @SuppressWarnings("unchecked")
  private static void assertThatResultIsOk(final AwsDynamoDbResult result) {
    assertThat(result.getAction()).isEqualTo("scanTable");
    assertThat(result.getStatus()).isEqualTo("OK");
    final List<Map<String, Object>> items = (List<Map<String, Object>>) result.getResponse();
    assertThat(items.size()).isEqualTo(2);
    assertThat(items.get(0).get("id")).isEqualTo("123");
    assertThat(items.get(0).get("name")).isEqualTo("John");
    assertThat(items.get(1).get("id")).isEqualTo("456");
    assertThat(items.get(1).get("name")).isEqualTo("Jane");
  }

  @BeforeEach
  public void setUp() {
    scanTable =
        new ScanTable(
            TestDynamoDBData.ActualValue.TABLE_NAME,
            TestDynamoDBData.ActualValue.FILTER_EXPRESSION,
            null,
            TestDynamoDBData.ActualValue.EXPRESSION_ATTRIBUTE_NAMES,
            TestDynamoDBData.ActualValue.EXPRESSION_ATTRIBUTE_VALUES);
  }

  @Test
  public void invoke_shouldScanTableWithoutFilter() {
    // Given
    scanTable = new ScanTable(TestDynamoDBData.ActualValue.TABLE_NAME, null, null, null, null);
    List<Map<String, AttributeValue>> items =
        List.of(
            Map.of(
                "id", AttributeValue.builder().s("123").build(),
                "name", AttributeValue.builder().s("John").build()),
            Map.of(
                "id", AttributeValue.builder().s("456").build(),
                "name", AttributeValue.builder().s("Jane").build()));
    when(dynamoDB.scan(requestCaptor.capture()))
        .thenReturn(ScanResponse.builder().items(items).build());
    scanTableOperation = new ScanTableOperation(scanTable);
    // When
    final AwsDynamoDbResult result = (AwsDynamoDbResult) scanTableOperation.invoke(dynamoDB);
    // Then
    assertThatResultIsOk(result);
    assertThat(requestCaptor.getValue().tableName())
        .isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
  }
}
