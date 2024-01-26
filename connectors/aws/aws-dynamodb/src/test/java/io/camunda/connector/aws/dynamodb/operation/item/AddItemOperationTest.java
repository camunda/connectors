/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AddItem;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class AddItemOperationTest extends BaseDynamoDbOperationTest {
  @Mock private AddItem addItemModel;
  @Mock private Item item;
  @Mock private PutItemOutcome putItemOutcome;

  @BeforeEach
  public void setUp() {
    when(addItemModel.tableName()).thenReturn(TestDynamoDBData.ActualValue.TABLE_NAME);
    when(addItemModel.item()).thenReturn(item);
    when(dynamoDB.getTable(addItemModel.tableName())).thenReturn(table);
    when(table.putItem(any(Item.class))).thenReturn(putItemOutcome);
  }

  @Test
  public void testInvoke() throws JsonProcessingException {
    AddItemOperation addItemOperation = new AddItemOperation(addItemModel);
    PutItemOutcome result = addItemOperation.invoke(dynamoDB);
    verify(table).putItem(any(Item.class));
    assertThat(result).isEqualTo(putItemOutcome);
  }

  @Test
  public void replaceSecrets_shouldReplaceSecrets() throws JsonProcessingException {
    // Given
    String input =
        """
                     {
                     "type": "addItem",
                     "tableName": "secrets.TABLE_NAME_KEY",
                     "item":{"item key":"secrets.ITEM_VALUE"}
                     }""";
    OutboundConnectorContext context = getContextWithSecrets(input);
    // When
    AwsInput request = context.bindVariables(AwsInput.class);
    // Then
    assertThat(request).isInstanceOf(AddItem.class);
    AddItem addItem = (AddItem) request;
    assertThat(addItem.tableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
    assertThat(addItem.item())
        .isEqualTo(objectMapper.readValue("{\"item key\":\"item value\"}", Object.class));
  }
}
