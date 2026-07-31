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
import io.camunda.connector.aws.dynamodb.model.GetItem;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import io.camunda.connector.aws.dynamodb.util.AttributeValueConverter;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

public class GetItemOperation implements AwsDynamoDbOperation {

  private final GetItem getItemModel;
  private final ObjectMapper objectMapper;

  public GetItemOperation(final GetItem getItemModel) {
    this.getItemModel = getItemModel;
    this.objectMapper = ObjectMapperSupplier.getMapperInstance();
  }

  /**
   * Returns the item as an array of single-key objects (one per attribute, in the item's own field
   * order), matching v1's {@code Item#attributes()} quirk exactly -- or JSON {@code null} when the
   * item does not exist. AWS SDK v2's {@link GetItemResponse#item()} returns an empty map (never
   * {@code null}) when there is no match, so {@link GetItemResponse#hasItem()} must be checked
   * explicitly to reproduce today's null-on-miss behavior instead of serializing an empty array.
   */
  @Override
  public Object invoke(final DynamoDbClient client) {
    Map<String, Object> key =
        objectMapper.convertValue(
            getItemModel.primaryKeyComponents(), new TypeReference<Map<String, Object>>() {});
    GetItemResponse response =
        client.getItem(
            GetItemRequest.builder()
                .tableName(getItemModel.tableName())
                .key(AttributeValueConverter.toAttributeValueMap(key))
                .build());
    if (!response.hasItem() || response.item().isEmpty()) {
      return null;
    }
    return AttributeValueConverter.toSingleKeyEntries(response.item());
  }
}
