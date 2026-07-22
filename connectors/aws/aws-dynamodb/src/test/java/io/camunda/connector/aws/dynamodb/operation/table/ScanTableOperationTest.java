/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AwsDynamoDbResult;
import io.camunda.connector.aws.dynamodb.model.ScanTable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.paginators.ScanIterable;

class ScanTableOperationTest extends BaseDynamoDbOperationTest {

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

  private static ScanResponse twoItemScanResponse() {
    Map<String, AttributeValue> item1 =
        Map.of(
            "id", AttributeValue.fromS("123"),
            "name", AttributeValue.fromS("John"),
            "age", AttributeValue.fromN("30"));
    Map<String, AttributeValue> item2 =
        Map.of(
            "id", AttributeValue.fromS("456"),
            "name", AttributeValue.fromS("Jane"),
            "age", AttributeValue.fromN("35"));
    return ScanResponse.builder().items(List.of(item1, item2)).build();
  }

  @BeforeEach
  public void setUp() {
    // scanPaginator() is a default method that pages through client.scan(...) internally; letting
    // the real ScanIterable run against the mocked client's scan() keeps pagination behavior
    // real while still controlling exactly what the (mocked) service returns.
    when(dynamoDbClient.scanPaginator(any(ScanRequest.class)))
        .thenAnswer(invocation -> new ScanIterable(dynamoDbClient, invocation.getArgument(0)));

    scanTable =
        new ScanTable(
            TestDynamoDBData.ActualValue.TABLE_NAME,
            TestDynamoDBData.ActualValue.FILTER_EXPRESSION,
            null,
            TestDynamoDBData.ActualValue.EXPRESSION_ATTRIBUTE_NAMES,
            TestDynamoDBData.ActualValue.EXPRESSION_ATTRIBUTE_VALUES);
  }

  /**
   * Pagination test: proves {@code scanPaginator} walks past the first page. Page 1 returns an item
   * plus a continuation key ({@code lastEvaluatedKey}); {@link ScanIterable} must then issue a
   * second scan whose {@code exclusiveStartKey} echoes that key, and page 2's item must also be
   * collected. The single-response tests can't catch a regression to first-page-only behavior --
   * this one can, because it asserts both the resumed request and the merged two-page result.
   */
  @Test
  public void invoke_collectsItemsAcrossPages() {
    scanTable = new ScanTable(TestDynamoDBData.ActualValue.TABLE_NAME, null, null, null, null);
    scanTableOperation = new ScanTableOperation(scanTable);

    Map<String, AttributeValue> continuationKey = Map.of("id", AttributeValue.fromS("123"));
    ScanResponse page1 =
        ScanResponse.builder()
            .items(
                List.of(
                    Map.of(
                        "id", AttributeValue.fromS("123"),
                        "name", AttributeValue.fromS("John"))))
            .lastEvaluatedKey(continuationKey)
            .build();
    ScanResponse page2 =
        ScanResponse.builder()
            .items(
                List.of(
                    Map.of(
                        "id", AttributeValue.fromS("456"),
                        "name", AttributeValue.fromS("Jane"))))
            .build(); // no lastEvaluatedKey -> paginator stops after this page

    ArgumentCaptor<ScanRequest> requestCaptor = ArgumentCaptor.forClass(ScanRequest.class);
    when(dynamoDbClient.scan(requestCaptor.capture()))
        .thenAnswer(
            invocation -> {
              ScanRequest req = invocation.getArgument(0);
              return req.exclusiveStartKey() == null || req.exclusiveStartKey().isEmpty()
                  ? page1
                  : page2;
            });

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items =
        (List<Map<String, Object>>)
            ((AwsDynamoDbResult) scanTableOperation.invoke(dynamoDbClient)).getResponse();

    // Both pages' items are collected, in page order.
    assertThat(items).hasSize(2);
    assertThat(items.get(0).get("id")).isEqualTo("123");
    assertThat(items.get(1).get("id")).isEqualTo("456");

    // A second page was actually requested, resuming from page 1's continuation key.
    List<ScanRequest> requests = requestCaptor.getAllValues();
    assertThat(requests).hasSize(2);
    assertThat(requests.get(0).exclusiveStartKey()).isNullOrEmpty();
    assertThat(requests.get(1).exclusiveStartKey()).isEqualTo(continuationKey);
  }

  @Test
  public void invoke_shouldScanTableWithoutFilter() {
    // Given
    scanTable = new ScanTable(TestDynamoDBData.ActualValue.TABLE_NAME, null, null, null, null);
    when(dynamoDbClient.scan(any(ScanRequest.class))).thenReturn(twoItemScanResponse());
    scanTableOperation = new ScanTableOperation(scanTable);
    // When
    final AwsDynamoDbResult result = (AwsDynamoDbResult) scanTableOperation.invoke(dynamoDbClient);
    // Then
    assertThatResultIsOk(result);
  }

  /**
   * Golden-JSON shape test: pins the exact JSON the v1 scanTable operation writes to process
   * variables today, so the AWS SDK v2 migration must reproduce it unchanged (migration contract
   * for #7973). Pins that matched items serialize as a list of plain merged maps, unlike getItem's
   * array-of-single-key-objects shape, with numbers appearing as plain JSON numbers.
   */
  @Test
  public void scanTable_serializesToDocumentedV1JsonShape_withMatches() throws Exception {
    ArgumentCaptor<ScanRequest> requestCaptor = ArgumentCaptor.forClass(ScanRequest.class);
    when(dynamoDbClient.scanPaginator(requestCaptor.capture()))
        .thenAnswer(invocation -> new ScanIterable(dynamoDbClient, invocation.getArgument(0)));
    when(dynamoDbClient.scan(any(ScanRequest.class))).thenReturn(twoItemScanResponse());
    scanTableOperation = new ScanTableOperation(scanTable);

    Object result = scanTableOperation.invoke(dynamoDbClient);

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

    ScanRequest request = requestCaptor.getValue();
    assertThat(request.tableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
    assertThat(request.filterExpression())
        .isEqualTo(TestDynamoDBData.ActualValue.FILTER_EXPRESSION);
    assertThat(request.expressionAttributeNames())
        .isEqualTo(TestDynamoDBData.ActualValue.EXPRESSION_ATTRIBUTE_NAMES);
  }

  /**
   * Golden-JSON shape test companion: pins quirk (f) -- a scan with zero matching items yields
   * {@code response: null}, not an empty array, because {@code ScanTableOperation} only assigns the
   * collected list when it is non-empty.
   */
  @Test
  public void scanTable_serializesToDocumentedV1JsonShape_withZeroMatches() throws Exception {
    ScanTable emptyScanTable =
        new ScanTable(TestDynamoDBData.ActualValue.TABLE_NAME, null, null, null, null);
    when(dynamoDbClient.scan(any(ScanRequest.class)))
        .thenReturn(ScanResponse.builder().items(List.of()).build());
    ScanTableOperation operation = new ScanTableOperation(emptyScanTable);

    Object result = operation.invoke(dynamoDbClient);

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
