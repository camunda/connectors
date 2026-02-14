/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import io.camunda.connector.aws.dynamodb.AwsDynamoDbAttributeValueMapper;
import io.camunda.connector.aws.dynamodb.model.GetItem;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

public class GetItemOperation implements AwsDynamoDbOperation {

  private final GetItem getItemModel;

  public GetItemOperation(final GetItem getItemModel) {
    this.getItemModel = getItemModel;
  }

  @Override
  public Object invoke(final DynamoDbClient dynamoDB) {
    var response =
        dynamoDB.getItem(
            GetItemRequest.builder()
                .tableName(getItemModel.tableName())
                .key(
                    AwsDynamoDbAttributeValueMapper.toAttributeValueMap(
                        getItemModel.primaryKeyComponents()))
                .build());
    return Optional.ofNullable(response)
        .filter(GetItemResponse::hasItem)
        .map(GetItemResponse::item)
        .filter(item -> !item.isEmpty())
        .map(AwsDynamoDbAttributeValueMapper::toSimpleMap)
        .orElse(null);
  }
}
