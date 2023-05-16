/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import io.camunda.connector.aws.dynamodb.model.table.DescribeTable;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;

public class DescribeTableOperation implements AwsDynamoDbOperation {
  private final DescribeTable describeTableModel;

  public DescribeTableOperation(final DescribeTable describeTableModel) {
    this.describeTableModel = describeTableModel;
  }

  @Override
  public Object invoke(final DynamoDB dynamoDB) {
    return dynamoDB.getTable(describeTableModel.getTableName()).describe();
  }
}
