/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.ScanOutcome;
import com.amazonaws.services.dynamodbv2.document.internal.IteratorSupport;
import com.fasterxml.jackson.databind.JsonNode;
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

  /**
   * Golden-JSON shape test: pins the exact JSON the v1 scanTable operation writes to process
   * variables today, so the AWS SDK v2 migration must reproduce it unchanged (migration contract
   * for #7973). Pins that matched items serialize as a list of plain maps (via {@code
   * Item#asMap()}, unlike getItem's array-of-single-key-objects shape) with BigDecimal-backed
   * numbers appearing as plain JSON numbers.
   */
  @Test
  public void scanTable_serializesToDocumentedV1JsonShape_withMatches() throws Exception {
    scanTable =
        new ScanTable(
            TestDynamoDBData.ActualValue.TABLE_NAME,
            TestDynamoDBData.ActualValue.FILTER_EXPRESSION,
            null,
            TestDynamoDBData.ActualValue.EXPRESSION_ATTRIBUTE_NAMES,
            TestDynamoDBData.ActualValue.EXPRESSION_ATTRIBUTE_VALUES);
    when(dynamoDB
            .getTable(TestDynamoDBData.ActualValue.TABLE_NAME)
            .scan(
                TestDynamoDBData.ActualValue.FILTER_EXPRESSION,
                null,
                TestDynamoDBData.ActualValue.EXPRESSION_ATTRIBUTE_NAMES,
                TestDynamoDBData.ActualValue.EXPRESSION_ATTRIBUTE_VALUES))
        .thenReturn(itemCollection);
    scanTableOperation = new ScanTableOperation(scanTable);

    Object result = scanTableOperation.invoke(dynamoDB);

    // Built via readTree(writeValueAsString(...)), not valueToTree(): see AddItemOperationTest.
    JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(result));
    String expectedJson =
        """
        {
          "action": "scanTable",
          "status": "OK",
          "response": [
            { "id": "123", "name": "John", "age": 30 },
            { "id": "456", "name": "Jane", "age": 35 }
          ]
        }
        """;
    JsonNode expected = objectMapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);
    // Deliberately no exact writeValueAsString() pin: see AddItemOperationTest for why we don't
    // rely on Jackson's reflection-based property order even for our own AwsDynamoDbResult type.
  }

  /**
   * Golden-JSON shape test companion: pins quirk (f) -- a scan with zero matching items yields
   * {@code response: null}, not an empty array, because {@code ScanTableOperation} only assigns the
   * collected list when it is non-empty.
   */
  @Test
  public void scanTable_serializesToDocumentedV1JsonShape_withZeroMatches() throws Exception {
    @SuppressWarnings("unchecked")
    ItemCollection<ScanOutcome> emptyItemCollection = mock(ItemCollection.class);
    @SuppressWarnings("unchecked")
    IteratorSupport<Item, ScanOutcome> emptyIterator = mock(IteratorSupport.class);
    when(emptyIterator.hasNext()).thenReturn(false);
    when(emptyItemCollection.iterator()).thenReturn(emptyIterator);

    ScanTable emptyScanTable =
        new ScanTable(TestDynamoDBData.ActualValue.TABLE_NAME, null, null, null, null);
    when(dynamoDB.getTable(TestDynamoDBData.ActualValue.TABLE_NAME).scan(null, null, null, null))
        .thenReturn(emptyItemCollection);
    ScanTableOperation operation = new ScanTableOperation(emptyScanTable);

    Object result = operation.invoke(dynamoDB);

    JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(result));
    String expectedJson =
        """
        {
          "action": "scanTable",
          "status": "OK",
          "response": null
        }
        """;
    JsonNode expected = objectMapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);
  }
}
