/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.aws.dynamodb.model.GetItem;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import java.util.HashMap;
import java.util.Optional;

public class GetItemOperation implements AwsDynamoDbOperation {

  private final GetItem getItemModel;
  private final ObjectMapper objectMapper;

  public GetItemOperation(final GetItem getItemModel) {
    this.getItemModel = getItemModel;
    this.objectMapper = ObjectMapperSupplier.getMapperInstance();
  }

  @Override
  public Object invoke(final DynamoDB dynamoDB) {
    return Optional.ofNullable(
            dynamoDB.getTable(getItemModel.getTableName()).getItem(createPrimaryKey()))
        .map(Item::attributes)
        .orElse(null);
  }

  private PrimaryKey createPrimaryKey() {
    PrimaryKey primaryKey = new PrimaryKey();
    objectMapper
        .convertValue(
            getItemModel.getPrimaryKeyComponents(), new TypeReference<HashMap<String, Object>>() {})
        .forEach(primaryKey::addComponent);
    return primaryKey;
  }
}
