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
import com.amazonaws.services.dynamodbv2.model.AttributeAction;
import com.google.gson.Gson;
import io.camunda.connector.aws.dynamodb.GsonDynamoDbComponentSupplier;
import io.camunda.connector.aws.dynamodb.model.item.UpdateItem;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class UpdateItemOperation implements AwsDynamoDbOperation {
  private final UpdateItem updateItemModel;
  private final Gson gson;

  public UpdateItemOperation(final UpdateItem updateItemModel) {
    this.updateItemModel = updateItemModel;
    this.gson = GsonDynamoDbComponentSupplier.gsonInstance();
  }

  @Override
  public Object invoke(final DynamoDB dynamoDB) {

    PrimaryKey primaryKey = new PrimaryKey();

    gson.toJsonTree(updateItemModel.getPrimaryKeyComponents())
        .getAsJsonObject()
        .entrySet()
        .forEach(
            entry -> {
              Object componentValue =
                  Optional.ofNullable(entry.getValue())
                      .map(obj -> gson.fromJson(obj, Object.class))
                      .orElse(null);
              primaryKey.addComponent(entry.getKey(), componentValue);
            });

    return dynamoDB
        .getTable(updateItemModel.getTableName())
        .updateItem(primaryKey, getAttributeUpdatesArray());
  }

  private AttributeUpdate[] getAttributeUpdatesArray() {
    List<AttributeUpdate> attributeUpdates = new ArrayList<>();

    gson.toJsonTree(updateItemModel.getKeyAttributes())
        .getAsJsonObject()
        .entrySet()
        .forEach(
            entry -> {
              var attributeValue =
                  Optional.ofNullable(entry.getValue())
                      .map(obj -> gson.fromJson(obj, Object.class))
                      .orElse(null);
              AttributeUpdate attributeUpdate;
              AttributeAction attributeAction =
                  AttributeAction.fromValue(
                      updateItemModel.getAttributeAction().toUpperCase(Locale.ROOT));

              attributeUpdate =
                  switch (attributeAction) {
                    case PUT -> new AttributeUpdate(entry.getKey()).put(attributeValue);
                    case DELETE -> new AttributeUpdate(entry.getKey()).delete();
                    default -> throw new IllegalArgumentException(
                        "Unsupported action [" + attributeAction + "]");
                  };

              attributeUpdates.add(attributeUpdate);
            });
    return attributeUpdates.toArray(AttributeUpdate[]::new);
  }
}
