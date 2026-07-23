/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import io.camunda.connector.aws.dynamodb.model.AwsDynamoDbResult;
import io.camunda.connector.aws.dynamodb.model.ScanTable;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import io.camunda.connector.aws.dynamodb.util.AttributeValueConverter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

public class ScanTableOperation implements AwsDynamoDbOperation {

  private final ScanTable scanTableModel;

  public ScanTableOperation(final ScanTable scanTableModel) {
    this.scanTableModel = scanTableModel;
  }

  /**
   * Uses {@link DynamoDbClient#scanPaginator(ScanRequest)} (not a bare {@code scan()} call, which
   * truncates at DynamoDB's 1MB-per-response page limit) so that, like v1's {@code
   * ItemCollection}-backed iterator, every matching item is collected regardless of table size.
   */
  @Override
  public Object invoke(final DynamoDbClient client) {
    ScanRequest request =
        ScanRequest.builder()
            .tableName(scanTableModel.tableName())
            .filterExpression(scanTableModel.filterExpression())
            .projectionExpression(scanTableModel.projectionExpression())
            .expressionAttributeNames(scanTableModel.expressionAttributeNames())
            .expressionAttributeValues(buildExpressionAttributeValues())
            .build();

    List<Map<String, Object>> items = new ArrayList<>();
    for (Map<String, AttributeValue> item : client.scanPaginator(request).items()) {
      items.add(AttributeValueConverter.toPlainMap(item));
    }

    return new AwsDynamoDbResult("scanTable", "OK", items.isEmpty() ? null : items);
  }

  private Map<String, AttributeValue> buildExpressionAttributeValues() {
    Map<String, Object> values = scanTableModel.expressionAttributeValues();
    return values == null ? null : AttributeValueConverter.toAttributeValueMap(values);
  }
}
