/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.aws.dynamodb.model.UpdateItemWithExpression;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class UpdateItemWithExpressionOperation implements AwsDynamoDbOperation {
  private final UpdateItemWithExpression updateItemModel;
  private final ObjectMapper objectMapper;

  public UpdateItemWithExpressionOperation(final UpdateItemWithExpression updateItemModel) {
    this.updateItemModel = updateItemModel;
    this.objectMapper = ObjectMapperSupplier.getMapperInstance();
  }

  @Override
  public Object invoke(final DynamoDB dynamoDB) {
    Table table = dynamoDB.getTable(updateItemModel.tableName());

    ValueMap valueMap = new ValueMap();
    updateItemModel
        .expressionAttributeValues()
        .forEach((key, valueEntry) -> transformToValueMap(key, valueEntry, valueMap));

    UpdateItemSpec updateItemSpec =
        new UpdateItemSpec()
            .withPrimaryKey(buildPrimaryKey(updateItemModel.primaryKeyComponents()))
            .withUpdateExpression(updateItemModel.updateExpression())
            .withNameMap(updateItemModel.expressionAttributeNames())
            .withValueMap(valueMap)
            .withReturnValues(updateItemModel.returnValues());

    return table.updateItem(updateItemSpec);
  }

  private PrimaryKey buildPrimaryKey(Map<String, Object> primaryKeyMap) {
    PrimaryKey primaryKey = new PrimaryKey();
    primaryKeyMap.forEach(primaryKey::addComponent);
    return primaryKey;
  }

  private void transformToValueMap(
      final String key, final Map<String, Object> valueEntry, final ValueMap valueMap) {
    valueEntry.forEach(
        (type, value) -> {
          switch (type) {
            case "S" -> valueMap.withString(key, objectMapper.convertValue(value, String.class));
            case "N" -> valueMap.withNumber(key, objectMapper.convertValue(value, Number.class));
            case "B" -> valueMap.withBinary(
                key, Base64.getDecoder().decode(objectMapper.convertValue(value, String.class)));
            case "BOOL" -> valueMap.withBoolean(
                key, objectMapper.convertValue(value, Boolean.class));
            case "NULL" -> valueMap.withNull(key);
            case "M" -> {
              ValueMap nestedValueMap = new ValueMap();
              Map<String, Object> mapValue =
                  objectMapper.convertValue(value, new TypeReference<>() {});
              mapValue.forEach(
                  (nestedKey, nestedValue) ->
                      transformToValueMap(
                          nestedKey,
                          objectMapper.convertValue(nestedValue, new TypeReference<>() {}),
                          nestedValueMap));
              valueMap.withMap(key, nestedValueMap);
            }
            case "L" -> {
              // temporary method for list need to add handling nested items
              valueMap.withList(key, objectMapper.convertValue(value, List.class));
            }
            case "SS" -> valueMap.withStringSet(
                key, objectMapper.convertValue(value, new TypeReference<Set<String>>() {}));
            case "NS" -> valueMap.withNumberSet(
                key, objectMapper.convertValue(value, new TypeReference<Set<BigDecimal>>() {}));
            case "BS" -> {
              Set<byte[]> decodedBinarySet =
                  objectMapper.convertValue(value, new TypeReference<Set<String>>() {}).stream()
                      .map(Base64.getDecoder()::decode)
                      .collect(Collectors.toSet());
              valueMap.withBinarySet(key, decodedBinarySet);
            }
            default -> throw new IllegalStateException("Unexpected type: " + type);
          }
        });
  }
}
