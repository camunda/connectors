/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.ScanOutcome;
import io.camunda.connector.aws.dynamodb.model.AwsDynamoDbResult;
import io.camunda.connector.aws.dynamodb.model.ScanTable;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import java.util.ArrayList;

public class ScanTableOperation implements AwsDynamoDbOperation {

  private final ScanTable scanTableModel;

  public ScanTableOperation(final ScanTable scanTableModel) {
    this.scanTableModel = scanTableModel;
  }

  @Override
  public Object invoke(final DynamoDB dynamoDB) {

    final ItemCollection<ScanOutcome> scan =
        dynamoDB
            .getTable(scanTableModel.getTableName())
            .scan(
                scanTableModel.getFilterExpression(),
                scanTableModel.getProjectionExpression(),
                scanTableModel.getExpressionAttributeNames(),
                scanTableModel.getExpressionAttributeValues());

    final var items = new ArrayList<>();
    for (final Item item : scan) {
      items.add(item.asMap());
    }

    return new AwsDynamoDbResult("scanTable", "OK", items.isEmpty() ? null : items);
  }
}
