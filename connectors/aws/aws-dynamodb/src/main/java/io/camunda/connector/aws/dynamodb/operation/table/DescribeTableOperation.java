/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import io.camunda.connector.aws.dynamodb.model.DescribeTable;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

public class DescribeTableOperation implements AwsDynamoDbOperation {
  private final DescribeTable describeTableModel;

  public DescribeTableOperation(final DescribeTable describeTableModel) {
    this.describeTableModel = describeTableModel;
  }

  @Override
  public TableDescription invoke(final DynamoDbClient dynamoDB) {
    return dynamoDB
        .describeTable(
            DescribeTableRequest.builder().tableName(describeTableModel.tableName()).build())
        .table();
  }
}
