/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import com.amazonaws.services.dynamodbv2.document.AttributeUpdate;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import io.camunda.connector.aws.dynamodb.model.UpdateItem;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import java.util.List;
import java.util.Map;

public class UpdateItemOperation implements AwsDynamoDbOperation {

  private final UpdateItem updateItemModel;

  public UpdateItemOperation(final UpdateItem updateItemModel) {
    this.updateItemModel = updateItemModel;
  }

  @Override
  public Object invoke(final DynamoDB dynamoDB) {

    List<AttributeUpdate> attributeUpdates =
        updateItemModel.keyAttributes().entrySet().stream()
            .map(
                entry ->
                    createAttributeUpdate(
                        entry.getKey(), entry.getValue(), updateItemModel.attributeAction()))
            .toList();

    return dynamoDB
        .getTable(updateItemModel.tableName())
        .updateItem(
            buildPrimaryKey(updateItemModel.primaryKeyComponents()),
            attributeUpdates.toArray(AttributeUpdate[]::new));
  }

  private AttributeUpdate createAttributeUpdate(
      final String key, final Object value, final String action) {
    AttributeUpdate update = new AttributeUpdate(key);
    return switch (action.toLowerCase()) {
      case "put" -> update.put(value);
      case "delete" -> update.delete();
      default -> throw new IllegalArgumentException("Unsupported attribute action: " + action);
    };
  }

  private PrimaryKey buildPrimaryKey(Map<String, Object> primaryKeyMap) {
    PrimaryKey primaryKey = new PrimaryKey();
    primaryKeyMap.forEach(primaryKey::addComponent);
    return primaryKey;
  }
}
