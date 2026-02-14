/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import io.camunda.connector.aws.dynamodb.model.AwsDynamoDbResult;
import io.camunda.connector.aws.dynamodb.model.DeleteTable;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableResponse;

public class DeleteTableOperation implements AwsDynamoDbOperation {
  private final DeleteTable deleteTableModel;

  public DeleteTableOperation(final DeleteTable deleteTableModel) {
    this.deleteTableModel = deleteTableModel;
  }

  @Override
  public Object invoke(final DynamoDbClient dynamoDB) {
    DeleteTableResponse response =
        dynamoDB.deleteTable(
            DeleteTableRequest.builder().tableName(deleteTableModel.tableName()).build());
    return new AwsDynamoDbResult(
        "delete Table [" + deleteTableModel.tableName() + "]", "OK", response.tableDescription());
  }
}
