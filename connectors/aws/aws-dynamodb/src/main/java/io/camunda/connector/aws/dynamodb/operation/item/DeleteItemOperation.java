/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.KeyAttribute;
import com.google.gson.Gson;
import io.camunda.connector.aws.dynamodb.GsonDynamoDbComponentSupplier;
import io.camunda.connector.aws.dynamodb.model.item.DeleteItem;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DeleteItemOperation implements AwsDynamoDbOperation {

  private final DeleteItem deleteItemModel;
  private final Gson gson;

  public DeleteItemOperation(final DeleteItem deleteItemModel) {
    this.deleteItemModel = deleteItemModel;
    this.gson = GsonDynamoDbComponentSupplier.gsonInstance();
  }

  @Override
  public Object invoke(final DynamoDB dynamoDb) {
    return dynamoDb.getTable(deleteItemModel.getTableName()).deleteItem(createKeyAttributes());
  }

  private KeyAttribute[] createKeyAttributes() {
    List<KeyAttribute> keyAttributeList = new ArrayList<>();
    gson.toJsonTree(deleteItemModel.getPrimaryKeyComponents())
        .getAsJsonObject()
        .entrySet()
        .forEach(
            (entry) -> {
              Object attributeValue =
                  Optional.ofNullable(entry.getValue())
                      .map(obj -> gson.fromJson(obj, Object.class))
                      .orElse(null);
              KeyAttribute keyAttribute = new KeyAttribute(entry.getKey(), attributeValue);
              keyAttributeList.add(keyAttribute);
            });
    return keyAttributeList.toArray(KeyAttribute[]::new);
  }
}
