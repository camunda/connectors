/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import io.camunda.connector.aws.dynamodb.model.CreateTable;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class CreateTableOperation implements AwsDynamoDbOperation {

  private final CreateTable createTableModel;

  public CreateTableOperation(final CreateTable createTableModel) {
    this.createTableModel = createTableModel;
  }

  public TableDescription invoke(final DynamoDB dynamoDB) {
    try {
      return dynamoDB.createTable(buildCreateTableRequest()).waitForActive();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private CreateTableRequest buildCreateTableRequest() {
    List<KeySchemaElement> keySchemaElements = buildKeySchemaElements();
    List<AttributeDefinition> attributeDefinitions = buildAttributeDefinitions();

    CreateTableRequest request =
        new CreateTableRequest()
            .withTableName(createTableModel.getTableName())
            .withKeySchema(keySchemaElements)
            .withAttributeDefinitions(attributeDefinitions)
            .withDeletionProtectionEnabled(createTableModel.isDeletionProtection());

    BillingMode billingMode =
        Optional.ofNullable(createTableModel.getBillingModeStr())
            .map(BillingMode::valueOf)
            .orElse(BillingMode.PROVISIONED);

    request.withBillingMode(billingMode);

    if (BillingMode.PROVISIONED == billingMode) {
      request.withProvisionedThroughput(
          new ProvisionedThroughput()
              .withReadCapacityUnits(createTableModel.getReadCapacityUnits())
              .withWriteCapacityUnits(createTableModel.getWriteCapacityUnits()));
    }

    return request;
  }

  private List<KeySchemaElement> buildKeySchemaElements() {
    List<KeySchemaElement> keySchemaElements = new ArrayList<>();
    keySchemaElements.add(
        new KeySchemaElement(
            createTableModel.getPartitionKey(), createTableModel.getPartitionKeyRole()));
    if (Objects.nonNull(createTableModel.getSortKey())
        && !createTableModel.getSortKey().isBlank()) {
      keySchemaElements.add(
          new KeySchemaElement(createTableModel.getSortKey(), createTableModel.getSortKeyRole()));
    }
    return keySchemaElements;
  }

  private List<AttributeDefinition> buildAttributeDefinitions() {
    List<AttributeDefinition> attributeDefinitions = new ArrayList<>();
    attributeDefinitions.add(
        new AttributeDefinition(
            createTableModel.getPartitionKey(), createTableModel.getPartitionKeyType()));
    if (Objects.nonNull(createTableModel.getSortKey())
        && !createTableModel.getSortKey().isBlank()) {
      attributeDefinitions.add(
          new AttributeDefinition(
              createTableModel.getSortKey(), createTableModel.getSortKeyType()));
    }
    return attributeDefinitions;
  }
}
