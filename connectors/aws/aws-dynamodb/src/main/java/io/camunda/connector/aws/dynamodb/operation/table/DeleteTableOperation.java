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
import java.time.Duration;
import software.amazon.awssdk.core.waiters.WaiterOverrideConfiguration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;

public class DeleteTableOperation implements AwsDynamoDbOperation {

  // See CreateTableOperation: caps the total wait near v1's 25 attempts x 5s (125s) default.
  private static final WaiterOverrideConfiguration WAITER_OVERRIDE_CONFIGURATION =
      WaiterOverrideConfiguration.builder()
          .maxAttempts(25)
          .waitTimeout(Duration.ofSeconds(125))
          .build();

  private final DeleteTable deleteTableModel;

  public DeleteTableOperation(final DeleteTable deleteTableModel) {
    this.deleteTableModel = deleteTableModel;
  }

  @Override
  public Object invoke(final DynamoDbClient client) {
    client.deleteTable(
        DeleteTableRequest.builder().tableName(deleteTableModel.tableName()).build());
    client
        .waiter()
        .waitUntilTableNotExists(
            DescribeTableRequest.builder().tableName(deleteTableModel.tableName()).build(),
            WAITER_OVERRIDE_CONFIGURATION);
    return new AwsDynamoDbResult("delete Table [" + deleteTableModel.tableName() + "]", "OK");
  }
}
