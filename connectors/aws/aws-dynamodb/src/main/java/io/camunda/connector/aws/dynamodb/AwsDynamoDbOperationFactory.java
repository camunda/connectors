/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import io.camunda.connector.aws.dynamodb.model.AddItem;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import io.camunda.connector.aws.dynamodb.model.CreateTable;
import io.camunda.connector.aws.dynamodb.model.DeleteItem;
import io.camunda.connector.aws.dynamodb.model.DeleteTable;
import io.camunda.connector.aws.dynamodb.model.DescribeTable;
import io.camunda.connector.aws.dynamodb.model.GetItem;
import io.camunda.connector.aws.dynamodb.model.ScanTable;
import io.camunda.connector.aws.dynamodb.model.UpdateItem;
import io.camunda.connector.aws.dynamodb.model.UpdateItemWithExpression;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import io.camunda.connector.aws.dynamodb.operation.item.AddItemOperation;
import io.camunda.connector.aws.dynamodb.operation.item.DeleteItemOperation;
import io.camunda.connector.aws.dynamodb.operation.item.GetItemOperation;
import io.camunda.connector.aws.dynamodb.operation.item.UpdateItemOperation;
import io.camunda.connector.aws.dynamodb.operation.item.UpdateItemWithExpressionOperation;
import io.camunda.connector.aws.dynamodb.operation.table.CreateTableOperation;
import io.camunda.connector.aws.dynamodb.operation.table.DeleteTableOperation;
import io.camunda.connector.aws.dynamodb.operation.table.DescribeTableOperation;
import io.camunda.connector.aws.dynamodb.operation.table.ScanTableOperation;

public class AwsDynamoDbOperationFactory {
  private static final AwsDynamoDbOperationFactory instance = new AwsDynamoDbOperationFactory();

  private AwsDynamoDbOperationFactory() {}

  public static AwsDynamoDbOperationFactory getInstance() {
    return instance;
  }

  public AwsDynamoDbOperation createOperation(AwsInput input) {
    return switch (input) {
      case AddItem addItem -> new AddItemOperation(addItem);
      case CreateTable createTable -> new CreateTableOperation(createTable);
      case DeleteItem deleteItem -> new DeleteItemOperation(deleteItem);
      case DeleteTable deleteTable -> new DeleteTableOperation(deleteTable);
      case DescribeTable describeTable -> new DescribeTableOperation(describeTable);
      case GetItem getItem -> new GetItemOperation(getItem);
      case ScanTable scanTable -> new ScanTableOperation(scanTable);
      case UpdateItem updateItem -> new UpdateItemOperation(updateItem);
      case UpdateItemWithExpression
      updateItemWithExpression -> new UpdateItemWithExpressionOperation(updateItemWithExpression);
      default -> throw new UnsupportedOperationException(
          "Unsupported operation: [" + input.getClass().getSimpleName() + "]");
    };
  }
}
