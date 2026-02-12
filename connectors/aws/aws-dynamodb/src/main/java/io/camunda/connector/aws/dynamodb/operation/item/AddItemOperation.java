/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import io.camunda.connector.aws.dynamodb.AwsDynamoDbAttributeValueMapper;
import io.camunda.connector.aws.dynamodb.model.AddItem;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

public class AddItemOperation implements AwsDynamoDbOperation {

  private final AddItem addItemModel;

  public AddItemOperation(final AddItem addItemModel) {
    this.addItemModel = addItemModel;
  }

  public PutItemResponse invoke(final DynamoDbClient dynamoDB) {
    return dynamoDB.putItem(
        PutItemRequest.builder()
            .tableName(addItemModel.tableName())
            .item(AwsDynamoDbAttributeValueMapper.toAttributeValueMap(addItemModel.item()))
            .build());
  }
}
