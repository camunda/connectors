/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import com.amazonaws.services.dynamodbv2.document.AttributeUpdate;
import com.amazonaws.services.dynamodbv2.document.KeyAttribute;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.amazonaws.services.dynamodbv2.document.UpdateItemOutcome;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.UpdateItem;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

class UpdateItemOperationTest extends BaseDynamoDbOperationTest {
  private UpdateItemOperation updateItemOperation;
  private UpdateItem updateItem;
  @Mock private UpdateItemOutcome updateItemOutcome;
  @Captor private ArgumentCaptor<PrimaryKey> primaryKeyArgumentCaptor;
  @Captor private ArgumentCaptor<AttributeUpdate> attributeUpdateArgumentCaptor;
  private KeyAttribute keyAttribute;
  private AttributeUpdate attributeUpdate;

  @BeforeEach
  public void setUp() {

    keyAttribute = new KeyAttribute("id", "123");
    attributeUpdate = new AttributeUpdate("name").addElements("John Doe");

    Map<String, Object> primaryKey = Map.of(keyAttribute.getName(), keyAttribute.getValue());
    Map<String, Object> attributeUpdates = Map.of(attributeUpdate.getAttributeName(), "John Doe");

    updateItem = new UpdateItem();
    updateItem.setTableName(TestDynamoDBData.ActualValue.TABLE_NAME);
    updateItem.setAttributeAction("PUT");
    updateItem.setKeyAttributes(attributeUpdates);
    updateItem.setPrimaryKeyComponents(primaryKey);

    updateItemOperation = new UpdateItemOperation(updateItem);
  }
}
