/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.model.item.AddItem;
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
        when(addItemModel.getTableName()).thenReturn(TestData.Table.NAME);
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
}