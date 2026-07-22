/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import io.camunda.connector.aws.dynamodb.model.CreateTable;
import io.camunda.connector.aws.dynamodb.model.TableDescriptionResult;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.core.waiters.WaiterOverrideConfiguration;
import software.amazon.awssdk.retries.api.BackoffStrategy;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;

public class CreateTableOperation implements AwsDynamoDbOperation {

  // v1's Table#waitForActive() defaulted to 25 attempts x 5s poll delay (~125s total). v2's
  // generated DynamoDbWaiter defaults the TableExists poll delay to a *fixed 20s* backoff (see
  // DefaultDynamoDbWaiter#tableExistsWaiterConfig), and its override merge reads only the caller's
  // backoffStrategyV2 -- so setting maxAttempts alone leaves the 20s spacing in place, and a
  // wait-timeout cap would then permit only ~7 checks. We must set the 5s backoff explicitly to
  // reproduce v1's 25x5s behavior; maxAttempts is the sole binding constraint (v1 had no separate
  // wall-clock cap), bounding the wait to ~120s.
  private static final WaiterOverrideConfiguration WAITER_OVERRIDE_CONFIGURATION =
      WaiterOverrideConfiguration.builder()
          .maxAttempts(25)
          .backoffStrategyV2(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofSeconds(5)))
          .build();

  private final CreateTable createTableModel;

  public CreateTableOperation(final CreateTable createTableModel) {
    this.createTableModel = createTableModel;
  }

  @Override
  public TableDescriptionResult invoke(final DynamoDbClient client) {
    client.createTable(buildCreateTableRequest());
    var response =
        client
            .waiter()
            .waitUntilTableExists(
                DescribeTableRequest.builder().tableName(createTableModel.tableName()).build(),
                WAITER_OVERRIDE_CONFIGURATION)
            .matched()
            .response()
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Timed out waiting for table ["
                            + createTableModel.tableName()
                            + "] to become ACTIVE"));
    return TableDescriptionResult.from(response.table());
  }

  private CreateTableRequest buildCreateTableRequest() {
    List<KeySchemaElement> keySchemaElements = buildKeySchemaElements();
    List<AttributeDefinition> attributeDefinitions = buildAttributeDefinitions();

    CreateTableRequest.Builder builder =
        CreateTableRequest.builder()
            .tableName(createTableModel.tableName())
            .keySchema(keySchemaElements)
            .attributeDefinitions(attributeDefinitions)
            .deletionProtectionEnabled(createTableModel.deletionProtection());

    BillingMode billingMode =
        Optional.ofNullable(createTableModel.billingModeStr())
            .map(BillingMode::fromValue)
            .orElse(BillingMode.PROVISIONED);
    builder.billingMode(billingMode);

    if (BillingMode.PROVISIONED == billingMode) {
      builder.provisionedThroughput(
          ProvisionedThroughput.builder()
              .readCapacityUnits(createTableModel.readCapacityUnits())
              .writeCapacityUnits(createTableModel.writeCapacityUnits())
              .build());
    }

    return builder.build();
  }

  private List<KeySchemaElement> buildKeySchemaElements() {
    List<KeySchemaElement> keySchemaElements = new ArrayList<>();
    keySchemaElements.add(
        KeySchemaElement.builder()
            .attributeName(createTableModel.partitionKey())
            .keyType(createTableModel.partitionKeyRole())
            .build());
    if (Objects.nonNull(createTableModel.sortKey()) && !createTableModel.sortKey().isBlank()) {
      keySchemaElements.add(
          KeySchemaElement.builder()
              .attributeName(createTableModel.sortKey())
              .keyType(createTableModel.sortKeyRole())
              .build());
    }
    return keySchemaElements;
  }

  private List<AttributeDefinition> buildAttributeDefinitions() {
    List<AttributeDefinition> attributeDefinitions = new ArrayList<>();
    attributeDefinitions.add(
        AttributeDefinition.builder()
            .attributeName(createTableModel.partitionKey())
            .attributeType(createTableModel.partitionKeyType())
            .build());
    if (Objects.nonNull(createTableModel.sortKey()) && !createTableModel.sortKey().isBlank()) {
      attributeDefinitions.add(
          AttributeDefinition.builder()
              .attributeName(createTableModel.sortKey())
              .attributeType(createTableModel.sortKeyType())
              .build());
    }
    return attributeDefinitions;
  }
}
