/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.ScanOutcome;
import com.amazonaws.services.dynamodbv2.document.internal.IteratorSupport;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AwsDynamoDbResult;
import io.camunda.connector.aws.dynamodb.model.ScanTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ScanTableOperationTest extends BaseDynamoDbOperationTest {

  @Mock private ItemCollection<ScanOutcome> itemCollection;
  @Mock private IteratorSupport<Item, ScanOutcome> iterator;
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

    List<Item> itemList = new ArrayList<>();
    itemList.add(
        new Item().withPrimaryKey("id", "123").withString("name", "John").withNumber("age", 30));
    itemList.add(
        new Item().withPrimaryKey("id", "456").withString("name", "Jane").withNumber("age", 35));
    when(iterator.hasNext()).thenReturn(true, true, false);
    when(iterator.next()).thenReturn(itemList.get(0), itemList.get(1));
    when(itemCollection.iterator()).thenReturn(iterator);

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
    when(dynamoDB.getTable(TestDynamoDBData.ActualValue.TABLE_NAME).scan(null, null, null, null))
        .thenReturn(itemCollection);
    scanTableOperation = new ScanTableOperation(scanTable);
    // When
    final AwsDynamoDbResult result = (AwsDynamoDbResult) scanTableOperation.invoke(dynamoDB);
    // Then
    assertThatResultIsOk(result);
  }
}
