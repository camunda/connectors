/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import io.camunda.connector.aws.dynamodb.AwsDynamoDbAttributeValueMapper;
import io.camunda.connector.aws.dynamodb.model.UpdateItem;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import java.util.Map;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeAction;
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

public class UpdateItemOperation implements AwsDynamoDbOperation {

  private final UpdateItem updateItemModel;

  public UpdateItemOperation(final UpdateItem updateItemModel) {
    this.updateItemModel = updateItemModel;
  }

  @Override
  public UpdateItemResponse invoke(final DynamoDbClient dynamoDB) {
    Map<String, AttributeValueUpdate> attributeUpdates =
        updateItemModel.keyAttributes().entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry ->
                        AttributeValueUpdate.builder()
                            .action(resolveAction(updateItemModel.attributeAction()))
                            .value(
                                AwsDynamoDbAttributeValueMapper.toAttributeValue(entry.getValue()))
                            .build()));

    return dynamoDB.updateItem(
        UpdateItemRequest.builder()
            .tableName(updateItemModel.tableName())
            .key(
                AwsDynamoDbAttributeValueMapper.toAttributeValueMap(
                    updateItemModel.primaryKeyComponents()))
            .attributeUpdates(attributeUpdates)
            .build());
  }

  private AttributeAction resolveAction(final String action) {
    return switch (action.toLowerCase()) {
      case "put" -> AttributeAction.PUT;
      case "delete" -> AttributeAction.DELETE;
      default -> throw new IllegalArgumentException("Unsupported attribute action: " + action);
    };
  }
}
