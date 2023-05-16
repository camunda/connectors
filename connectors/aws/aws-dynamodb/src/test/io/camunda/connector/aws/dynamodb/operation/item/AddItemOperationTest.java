/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.item.AddItem;
import io.camunda.connector.aws.model.AwsInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AddItemOperationTest extends BaseDynamoDbOperationTest {
    @Mock
    private AddItem addItemModel;
    @Mock
    private Item item;
    @Mock
    private PutItemOutcome putItemOutcome;

    @BeforeEach
    public void setUp() {
        when(addItemModel.getTableName()).thenReturn(TestDynamoDBData.ActualValue.TABLE_NAME);
        when(addItemModel.getItem()).thenReturn(item);
        when(dynamoDB.getTable(addItemModel.getTableName())).thenReturn(table);
        when(table.putItem(any(Item.class))).thenReturn(putItemOutcome);
    }

    @Test
    public void testInvoke() {
        AddItemOperation addItemOperation = new AddItemOperation(addItemModel);
        PutItemOutcome result = addItemOperation.invoke(dynamoDB);
        verify(table).putItem(any(Item.class));
        assertThat(result).isEqualTo(putItemOutcome);
    }

    @Test
    public void replaceSecrets_shouldReplaceSecrets() {
        // Given
        String input = """
                     {
                     "type": "addItem",
                     "tableName": "secrets.TABLE_NAME_KEY",
                     "item":{"item key":"secrets.ITEM_VALUE"}
                     }""";
        OutboundConnectorContext context = getContextWithSecrets();
        AwsInput request = GSON.fromJson(input, AwsInput.class);
        // When
        context.replaceSecrets(request);
        // Then
        assertThat(request).isInstanceOf(AddItem.class);
        AddItem addItem = (AddItem) request;
        assertThat(addItem.getTableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
        assertThat(addItem.getItem()).isEqualTo( GSON.fromJson("{\"item key\"=\"item value\"}", Object.class));
    }
}