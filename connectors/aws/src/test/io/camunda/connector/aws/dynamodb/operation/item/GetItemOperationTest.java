/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.KeyAttribute;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.model.item.GetItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetItemOperationTest extends BaseDynamoDbOperationTest {

    private GetItemOperation getItemOperation;
    @Captor
    private ArgumentCaptor<KeyAttribute[]> keyAttributesCaptor;

    @BeforeEach
    public void setup() {
        GetItem getItem = new GetItem();
        getItem.setTableName(TestData.Table.NAME);
        getItem.setPrimaryKeyComponents(Map.of("id", "1", "type", "user"));
        getItemOperation = new GetItemOperation(getItem);
    }

    @SuppressWarnings("unchecked")
    @Test
    void invoke_shouldReturnItemAttributes_whenItemExists() {
        // Given
        Item mockItem = Item.fromMap(Map.of("id", "1", "type", "user", "name", "Alice"));
        when(table.getItem(keyAttributesCaptor.capture())).thenReturn(mockItem);
        mockItem.attributes();

        // When
        Iterable<Map.Entry<String, Object>> result = (Iterable<Map.Entry<String, Object>>) getItemOperation.invoke(dynamoDB);

        // Then
        verify(dynamoDB, times(1)).getTable(TestData.Table.NAME);
        verify(table, times(1)).getItem(any(PrimaryKey.class));
        List<KeyAttribute> keyAttributeList = List.of(keyAttributesCaptor.getValue());
        assertThat(keyAttributeList)
                .asList()
                .contains(new KeyAttribute("id", "1"), new KeyAttribute("type", "user"));
        assertThat(result).isEqualTo(mockItem.attributes());
    }

    @SuppressWarnings("unchecked")
    @Test
    void invoke_shouldReturnNull_whenItemDoesNotExist() {
        // Given
        when(table.getItem(any(KeyAttribute.class), any(KeyAttribute.class))).thenReturn(null);

        // When
        Map<String, Object> result = (Map<String, Object>) getItemOperation.invoke(dynamoDB);

        // Then
        verify(dynamoDB, times(1)).getTable(TestData.Table.NAME);
        verify(table, times(1)).getItem(any(PrimaryKey.class));
        assertThat(result).isNull();
    }


}