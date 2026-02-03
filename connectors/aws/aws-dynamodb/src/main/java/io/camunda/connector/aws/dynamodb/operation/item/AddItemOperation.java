/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.aws.dynamodb.model.AddItem;
import io.camunda.connector.aws.dynamodb.operation.AwsDynamoDbOperation;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.document.DynamoDb;
import software.amazon.awssdk.services.dynamodb.document.Item;
import software.amazon.awssdk.services.dynamodb.document.PutItemOutcome;
import software.amazon.awssdk.services.dynamodb.document.Table;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

public class AddItemOperation implements AwsDynamoDbOperation {

  private final AddItem addItemModel;

  public AddItemOperation(final AddItem addItemModel) {
    this.addItemModel = addItemModel;
  }

  public PutItemResponse invoke(final DynamoDbClient dynamoDB) throws JsonProcessingException {
    Map<String, AttributeValue> itemStr =
        ObjectMapperSupplier.getMapperInstance().writeValueAsString(addItemModel.item());
    Item item = Item.fromJSON(itemStr);

    final Table table = dynamoDB.getTable(addItemModel.tableName());
    dynamoDB.putItem(
        builder ->
            builder
                .tableName(addItemModel.tableName())
                .item(addItemModel.item())
    )
    return table.putItem(item);
  }
}
