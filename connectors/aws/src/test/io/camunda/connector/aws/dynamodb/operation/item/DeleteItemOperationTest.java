/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import com.amazonaws.services.dynamodbv2.document.DeleteItemOutcome;
import com.amazonaws.services.dynamodbv2.document.KeyAttribute;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.model.item.DeleteItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;


class DeleteItemOperationTest extends BaseDynamoDbOperationTest {

    private DeleteItemOperation deleteItemOperation;
    @Mock
    private DeleteItemOutcome deleteItemOutcome;
    @Captor
    private ArgumentCaptor<KeyAttribute> keyAttributeArgumentCaptor;

    @BeforeEach
    public void setup() {
        Map<String, Object> primaryKeyComponents = new HashMap<>();
        primaryKeyComponents.put("id", "1234");
        DeleteItem deleteItem = new DeleteItem();
        deleteItem.setTableName(TestData.Table.NAME);
        deleteItem.setPrimaryKeyComponents(primaryKeyComponents);
        this.deleteItemOperation = new DeleteItemOperation(deleteItem);
    }

    @Test
    public void testInvoke() {
        //Given
        when(table.deleteItem(keyAttributeArgumentCaptor.capture())).thenReturn(deleteItemOutcome);
        //When
        Object result = this.deleteItemOperation.invoke(dynamoDB);
        //Then
        assertThat(result).isEqualTo(deleteItemOutcome);
        KeyAttribute keyAttribute = keyAttributeArgumentCaptor.getValue();
        assertThat(keyAttribute.getName()).isEqualTo("id");
        assertThat(keyAttribute.getValue()).isEqualTo("1234");

    }

}