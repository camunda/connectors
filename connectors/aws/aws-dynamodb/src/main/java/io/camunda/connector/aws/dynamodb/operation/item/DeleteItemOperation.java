/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import io.camunda.connector.aws.dynamodb.AwsDynamoDbAttributeValueMapper;
import io.camunda.connector.aws.dynamodb.model.DeleteItem;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;

public class DeleteItemOperation implements AwsDynamoDbOperation {

  private final DeleteItem deleteItemModel;

  public DeleteItemOperation(final DeleteItem deleteItemModel) {
    this.deleteItemModel = deleteItemModel;
  }

  @Override
  public DeleteItemResponse invoke(final DynamoDbClient dynamoDb) {
    return dynamoDb.deleteItem(
        DeleteItemRequest.builder()
            .tableName(deleteItemModel.tableName())
            .key(
                AwsDynamoDbAttributeValueMapper.toAttributeValueMap(
                    deleteItemModel.primaryKeyComponents()))
            .build());
  }
}
