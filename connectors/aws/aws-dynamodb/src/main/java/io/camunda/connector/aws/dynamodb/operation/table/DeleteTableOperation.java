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
import software.amazon.awssdk.retries.api.BackoffStrategy;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;

public class DeleteTableOperation implements AwsDynamoDbOperation {

  // See CreateTableOperation: the v2 DynamoDbWaiter's TableNotExists default is a fixed 20s poll
  // delay, so we must set the 5s backoff explicitly (not just maxAttempts) to reproduce v1's
  // 25x5s (~125s) behavior; maxAttempts is the sole binding constraint.
  private static final WaiterOverrideConfiguration WAITER_OVERRIDE_CONFIGURATION =
      WaiterOverrideConfiguration.builder()
          .maxAttempts(25)
          .backoffStrategyV2(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofSeconds(5)))
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
