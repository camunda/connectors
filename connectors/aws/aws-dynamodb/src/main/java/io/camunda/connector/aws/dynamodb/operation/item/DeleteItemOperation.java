/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.aws.dynamodb.model.DeleteItem;
import io.camunda.connector.aws.dynamodb.model.DeleteItemResult;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import io.camunda.connector.aws.dynamodb.util.AttributeValueConverter;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;

public class DeleteItemOperation implements AwsDynamoDbOperation {

  private final DeleteItem deleteItemModel;
  private final ObjectMapper objectMapper;

  public DeleteItemOperation(final DeleteItem deleteItemModel) {
    this.deleteItemModel = deleteItemModel;
    this.objectMapper = ObjectMapperSupplier.getMapperInstance();
  }

  @Override
  public DeleteItemResult invoke(final DynamoDbClient client) {
    Map<String, Object> key =
        objectMapper.convertValue(
            deleteItemModel.primaryKeyComponents(), new TypeReference<Map<String, Object>>() {});
    DeleteItemResponse response =
        client.deleteItem(
            DeleteItemRequest.builder()
                .tableName(deleteItemModel.tableName())
                .key(AttributeValueConverter.toAttributeValueMap(key))
                .build());
    return DeleteItemResult.from(response);
  }
}
