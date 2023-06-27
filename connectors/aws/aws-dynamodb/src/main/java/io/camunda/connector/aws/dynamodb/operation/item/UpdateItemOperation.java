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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.aws.dynamodb.model.item.UpdateItem;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class UpdateItemOperation implements AwsDynamoDbOperation {
  private final UpdateItem updateItemModel;
  private final ObjectMapper objectMapper;

  public UpdateItemOperation(final UpdateItem updateItemModel) {
    this.updateItemModel = updateItemModel;
    this.objectMapper = ObjectMapperSupplier.getMapperInstance();
  }

  @Override
  public Object invoke(final DynamoDB dynamoDB) {

    PrimaryKey primaryKey = new PrimaryKey();
    objectMapper
        .convertValue(
            updateItemModel.getKeyAttributes(), new TypeReference<HashMap<String, Object>>() {})
        .forEach(primaryKey::addComponent);

    return dynamoDB
        .getTable(updateItemModel.getTableName())
        .updateItem(primaryKey, getAttributeUpdatesArray());
  }

  private AttributeUpdate[] getAttributeUpdatesArray() {
    List<AttributeUpdate> attributeUpdates = new ArrayList<>();
    objectMapper
        .convertValue(
            updateItemModel.getKeyAttributes(), new TypeReference<HashMap<String, Object>>() {})
        .forEach(
            (key, value) -> {
              AttributeUpdate attributeUpdate;
              AttributeAction attributeAction =
                  AttributeAction.fromValue(
                      updateItemModel.getAttributeAction().toUpperCase(Locale.ROOT));
              attributeUpdate =
                  switch (attributeAction) {
                    case PUT -> new AttributeUpdate(key).put(value);
                    case DELETE -> new AttributeUpdate(key).delete();
                    default -> throw new IllegalArgumentException(
                        "Unsupported action [" + attributeAction + "]");
                  };

              attributeUpdates.add(attributeUpdate);
            });
    return attributeUpdates.toArray(AttributeUpdate[]::new);
  }
}
