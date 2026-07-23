/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import io.camunda.connector.aws.dynamodb.model.UpdateItem;
import io.camunda.connector.aws.dynamodb.model.UpdateItemResult;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import io.camunda.connector.aws.dynamodb.util.AttributeValueConverter;
import java.util.LinkedHashMap;
import java.util.Map;
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
  public UpdateItemResult invoke(final DynamoDbClient client) {
    Map<String, AttributeValueUpdate> attributeUpdates = new LinkedHashMap<>();
    updateItemModel
        .keyAttributes()
        .forEach(
            (key, value) ->
                attributeUpdates.put(
                    key, createAttributeUpdate(value, updateItemModel.attributeAction())));

    UpdateItemResponse response =
        client.updateItem(
            UpdateItemRequest.builder()
                .tableName(updateItemModel.tableName())
                .key(
                    AttributeValueConverter.toAttributeValueMap(
                        updateItemModel.primaryKeyComponents()))
                .attributeUpdates(attributeUpdates)
                .build());
    return UpdateItemResult.from(response);
  }

  private AttributeValueUpdate createAttributeUpdate(final Object value, final String action) {
    return switch (action.toLowerCase()) {
      case "put" ->
          AttributeValueUpdate.builder()
              .value(AttributeValueConverter.toAttributeValue(value))
              .action(AttributeAction.PUT)
              .build();
      case "delete" -> AttributeValueUpdate.builder().action(AttributeAction.DELETE).build();
      default -> throw new IllegalArgumentException("Unsupported attribute action: " + action);
    };
  }
}
