/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import io.camunda.connector.aws.dynamodb.AwsDynamoDbAttributeValueMapper;
import io.camunda.connector.aws.dynamodb.model.AwsDynamoDbResult;
import io.camunda.connector.aws.dynamodb.model.ScanTable;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import java.util.List;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

public class ScanTableOperation implements AwsDynamoDbOperation {

  private final ScanTable scanTableModel;

  public ScanTableOperation(final ScanTable scanTableModel) {
    this.scanTableModel = scanTableModel;
  }

  @Override
  public Object invoke(final DynamoDbClient dynamoDB) {
    ScanRequest.Builder request = ScanRequest.builder().tableName(scanTableModel.tableName());
    if (scanTableModel.filterExpression() != null) {
      request.filterExpression(scanTableModel.filterExpression());
    }
    if (scanTableModel.projectionExpression() != null) {
      request.projectionExpression(scanTableModel.projectionExpression());
    }
    if (scanTableModel.expressionAttributeNames() != null) {
      request.expressionAttributeNames(scanTableModel.expressionAttributeNames());
    }
    if (scanTableModel.expressionAttributeValues() != null) {
      request.expressionAttributeValues(
          AwsDynamoDbAttributeValueMapper.toAttributeValueMap(
              scanTableModel.expressionAttributeValues()));
    }

    List<java.util.Map<String, Object>> items =
        dynamoDB.scan(request.build()).items().stream()
            .map(AwsDynamoDbAttributeValueMapper::toSimpleMap)
            .toList();

    return new AwsDynamoDbResult("scanTable", "OK", items.isEmpty() ? null : items);
  }
}
