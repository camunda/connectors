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
import com.amazonaws.services.dynamodbv2.document.UpdateItemOutcome;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.aws.dynamodb.model.UpdateItem;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import java.util.Map;

@Deprecated
public class UpdateItemOperation implements AwsDynamoDbOperation {
  private static final String ACTION_PUT = "put";
  private static final String ACTION_DELETE = "delete";
  private static final String UPDATE_EXPRESSION_SET = "set ";
  private static final String UPDATE_EXPRESSION_REMOVE = "remove ";

  private final UpdateItem updateItemModel;
  private final ObjectMapper objectMapper;

  public UpdateItemOperation(final UpdateItem updateItemModel) {
    this.updateItemModel = updateItemModel;
    this.objectMapper = ObjectMapperSupplier.getMapperInstance();
  }

  @Override
  public Object invoke(final DynamoDB dynamoDB) {
    Map<String, Object> primaryKeyMap =
        objectMapper.convertValue(updateItemModel.primaryKeyComponents(), new TypeReference<>() {});
    Map<String, Object> attributesMap =
        objectMapper.convertValue(updateItemModel.keyAttributes(), new TypeReference<>() {});

    String tableName = updateItemModel.tableName();
    String action = updateItemModel.attributeAction();

    return updateItem(dynamoDB, primaryKeyMap, attributesMap, action, tableName);
  }

  private UpdateItemOutcome updateItem(
      DynamoDB dynamoDB,
      Map<String, Object> primaryKeyMap,
      Map<String, Object> attributesMap,
      String action,
      String tableName) {
    try {
      Table table = dynamoDB.getTable(tableName);
      PrimaryKey primaryKey = buildPrimaryKey(primaryKeyMap);

      UpdateItemSpec updateItemSpec = buildUpdateItemSpec(primaryKey, attributesMap, action);
      return table.updateItem(updateItemSpec);
    } catch (Exception e) {
      throw new ConnectorException("Error in updateItem operation: " + e.getMessage(), e);
    }
  }

  private PrimaryKey buildPrimaryKey(Map<String, Object> primaryKeyMap) {
    PrimaryKey primaryKey = new PrimaryKey();
    primaryKeyMap.forEach(primaryKey::addComponent);
    return primaryKey;
  }

  private UpdateItemSpec buildUpdateItemSpec(
      PrimaryKey primaryKey, Map<String, Object> attributesMap, String action) {
    UpdateItemSpec updateItemSpec = new UpdateItemSpec().withPrimaryKey(primaryKey);
    ValueMap valueMap = new ValueMap();
    StringBuilder updateExpression = new StringBuilder();

    if (ACTION_PUT.equalsIgnoreCase(action)) {
      buildPutUpdateExpression(attributesMap, updateExpression, valueMap);
    } else if (ACTION_DELETE.equalsIgnoreCase(action)) {
      buildDeleteUpdateExpression(attributesMap, updateExpression);
    } else {
      throw new ConnectorException("Invalid action: " + action);
    }

    updateItemSpec
        .withUpdateExpression(updateExpression.toString())
        .withReturnValues(ReturnValue.ALL_NEW);
    if (!valueMap.isEmpty()) {
      updateItemSpec.withValueMap(valueMap);
    }
    return updateItemSpec;
  }

  private void buildPutUpdateExpression(
      Map<String, Object> attributesMap, StringBuilder updateExpression, ValueMap valueMap) {
    updateExpression.append(UPDATE_EXPRESSION_SET);
    attributesMap.forEach(
        (k, v) -> {
          String attrKey = ":" + k;
          updateExpression.append(k).append(" = ").append(attrKey).append(", ");
          valueMap.withString(attrKey, String.valueOf(v));
        });
    trimTrailingComma(updateExpression);
  }

  private void buildDeleteUpdateExpression(
      Map<String, Object> attributesMap, StringBuilder updateExpression) {
    updateExpression.append(UPDATE_EXPRESSION_REMOVE);
    attributesMap.keySet().forEach(k -> updateExpression.append(k).append(", "));
    trimTrailingComma(updateExpression);
  }

  private void trimTrailingComma(StringBuilder updateExpression) {
    if (!updateExpression.isEmpty()) {
      updateExpression.setLength(updateExpression.length() - 2); // Remove last comma and space
    }
  }
}
