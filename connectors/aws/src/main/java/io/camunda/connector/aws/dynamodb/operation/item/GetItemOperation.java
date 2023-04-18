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
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.camunda.connector.aws.dynamodb.GsonDynamoDbComponentSupplier;
import io.camunda.connector.aws.dynamodb.model.item.GetItem;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import java.util.Optional;

public class GetItemOperation implements AwsDynamoDbOperation {

  private final GetItem getItemModel;
  private final Gson gson;

  public GetItemOperation(final GetItem getItemModel) {
    this.getItemModel = getItemModel;
    this.gson = GsonDynamoDbComponentSupplier.gsonInstance();
  }

  @Override
  public Object invoke(final DynamoDB dynamoDB) {
    return Optional.ofNullable(
            dynamoDB.getTable(getItemModel.getTableName()).getItem(createPrimaryKey()))
        .map(Item::attributes)
        .orElse(null);
  }

  private PrimaryKey createPrimaryKey() {
    JsonObject primaryKeyJson =
        gson.toJsonTree(getItemModel.getPrimaryKeyComponents()).getAsJsonObject();

    PrimaryKey primaryKey = new PrimaryKey();
    primaryKeyJson
        .entrySet()
        .forEach(
            entry -> {
              Object value =
                  Optional.ofNullable(entry.getValue())
                      .map(jsonElement -> gson.fromJson(jsonElement, Object.class))
                      .orElse(null);
              primaryKey.addComponent(entry.getKey(), value);
            });
    return primaryKey;
  }
}
