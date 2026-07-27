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
import io.camunda.connector.aws.dynamodb.model.AddItem;
import io.camunda.connector.aws.dynamodb.model.AddItemResult;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import io.camunda.connector.aws.dynamodb.util.AttributeValueConverter;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

public class AddItemOperation implements AwsDynamoDbOperation {

  private final AddItem addItemModel;
  private final ObjectMapper objectMapper;

  public AddItemOperation(final AddItem addItemModel) {
    this.addItemModel = addItemModel;
    this.objectMapper = ObjectMapperSupplier.getMapperInstance();
  }

  @Override
  public AddItemResult invoke(final DynamoDbClient client) {
    Map<String, Object> item =
        objectMapper.convertValue(addItemModel.item(), new TypeReference<Map<String, Object>>() {});
    PutItemResponse response =
        client.putItem(
            PutItemRequest.builder()
                .tableName(addItemModel.tableName())
                .item(AttributeValueConverter.toAttributeValueMap(item))
                .build());
    return AddItemResult.from(response);
  }
}
