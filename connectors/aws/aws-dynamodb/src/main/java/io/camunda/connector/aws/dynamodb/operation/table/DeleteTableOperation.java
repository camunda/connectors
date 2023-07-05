/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import io.camunda.connector.aws.dynamodb.model.AwsDynamoDbResult;
import io.camunda.connector.aws.dynamodb.model.DeleteTable;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;

public class DeleteTableOperation implements AwsDynamoDbOperation {
  private final DeleteTable deleteTableModel;

  public DeleteTableOperation(final DeleteTable deleteTableModel) {
    this.deleteTableModel = deleteTableModel;
  }

  @Override
  public Object invoke(final DynamoDB dynamoDB) {
    var table = dynamoDB.getTable(deleteTableModel.getTableName());
    table.delete();
    try {
      table.waitForDelete();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return new AwsDynamoDbResult("delete Table [" + deleteTableModel.getTableName() + "]", "OK");
  }
}
