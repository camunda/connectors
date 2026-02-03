/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.table;

import io.camunda.connector.aws.dynamodb.model.CreateTable;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.document.DynamoDb;
import software.amazon.awssdk.services.dynamodb.model.*;

public class CreateTableOperation implements AwsDynamoDbOperation {

  private final CreateTable createTableModel;

  public CreateTableOperation(final CreateTable createTableModel) {
    this.createTableModel = createTableModel;
  }

  public TableDescription invoke(final DynamoDbClient dynamoDB) {
    return dynamoDB.createTable(buildCreateTableRequest()).tableDescription();
  }

  private CreateTableRequest buildCreateTableRequest() {
    List<KeySchemaElement> keySchemaElements = buildKeySchemaElements();
    List<AttributeDefinition> attributeDefinitions = buildAttributeDefinitions();

    CreateTableRequest request =
        CreateTableRequest.builder()
            .tableName(createTableModel.tableName())
            .keySchema(keySchemaElements)
            .attributeDefinitions(attributeDefinitions)
            .deletionProtectionEnabled(createTableModel.deletionProtection())
            .build();

    BillingMode billingMode =
        Optional.ofNullable(createTableModel.billingModeStr())
            .map(BillingMode::valueOf)
            .orElse(BillingMode.PROVISIONED);

    request.billingMode(billingMode);

    if (BillingMode.PROVISIONED == billingMode) {
      request.provisionedThroughput(
          ProvisionedThroughput.builder()
              .readCapacityUnits(createTableModel.readCapacityUnits())
              .writeCapacityUnits(createTableModel.writeCapacityUnits())
              .build());
    }

    return request;
  }

  private List<KeySchemaElement> buildKeySchemaElements() {
    List<KeySchemaElement> keySchemaElements = new ArrayList<>();
    keySchemaElements.add(
        new KeySchemaElement(createTableModel.partitionKey(), createTableModel.partitionKeyRole()));
    if (Objects.nonNull(createTableModel.sortKey()) && !createTableModel.sortKey().isBlank()) {
      keySchemaElements.add(
          new KeySchemaElement(createTableModel.sortKey(), createTableModel.sortKeyRole()));
    }
    return keySchemaElements;
  }

  private List<AttributeDefinition> buildAttributeDefinitions() {
    List<AttributeDefinition> attributeDefinitions = new ArrayList<>();
    attributeDefinitions.add(
        new AttributeDefinition(
            createTableModel.partitionKey(), createTableModel.partitionKeyType()));
    if (Objects.nonNull(createTableModel.sortKey()) && !createTableModel.sortKey().isBlank()) {
      attributeDefinitions.add(
          new AttributeDefinition(createTableModel.sortKey(), createTableModel.sortKeyType()));
    }
    return attributeDefinitions;
  }
}
