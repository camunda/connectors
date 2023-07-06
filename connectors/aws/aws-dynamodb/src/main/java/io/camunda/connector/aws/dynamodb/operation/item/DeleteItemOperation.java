/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.KeyAttribute;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.aws.dynamodb.model.DeleteItem;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DeleteItemOperation implements AwsDynamoDbOperation {

  private final DeleteItem deleteItemModel;
  private final ObjectMapper objectMapper;

  public DeleteItemOperation(final DeleteItem deleteItemModel) {
    this.deleteItemModel = deleteItemModel;
    this.objectMapper = ObjectMapperSupplier.getMapperInstance();
  }

  @Override
  public Object invoke(final DynamoDB dynamoDb) {
    return dynamoDb.getTable(deleteItemModel.getTableName()).deleteItem(createKeyAttributes());
  }

  private KeyAttribute[] createKeyAttributes() {
    List<KeyAttribute> keyAttributeList = new ArrayList<>();
    objectMapper
        .convertValue(
            deleteItemModel.getPrimaryKeyComponents(),
            new TypeReference<HashMap<String, Object>>() {})
        .forEach((key, value) -> keyAttributeList.add(new KeyAttribute(key, value)));
    return keyAttributeList.toArray(KeyAttribute[]::new);
  }
}
