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
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.item.GetItem;
import io.camunda.connector.aws.model.AwsInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetItemOperationTest extends BaseDynamoDbOperationTest {

    private GetItemOperation getItemOperation;
    @Captor private ArgumentCaptor<PrimaryKey> keyAttributesCaptor;

    @BeforeEach
    public void setup() {
        GetItem getItem = new GetItem();
        getItem.setTableName(TestDynamoDBData.ActualValue.TABLE_NAME);
        getItem.setPrimaryKeyComponents(Map.of("id", "1", "type", "user"));
        getItemOperation = new GetItemOperation(getItem);
    }

    @SuppressWarnings("unchecked")
    @Test
    void invoke_shouldReturnItemAttributes_whenItemExists() {
        // Given
        Item mockItem = Item.fromMap(Map.of("id", "1", "type", "user", "name", "Alice"));
        when(table.getItem(keyAttributesCaptor.capture())).thenReturn(mockItem);

        // When
        Iterable<Map.Entry<String, Object>> result = (Iterable<Map.Entry<String, Object>>) getItemOperation.invoke(dynamoDB);

        // Then
        verify(dynamoDB, times(1)).getTable(TestDynamoDBData.ActualValue.TABLE_NAME);
        verify(table, times(1)).getItem(any(PrimaryKey.class));
        ArrayList<KeyAttribute> keyAttributes = new ArrayList<>(keyAttributesCaptor.getValue().getComponents());
        assertThat(keyAttributes)
                .asList()
                .contains(new KeyAttribute("id", "1"), new KeyAttribute("type", "user"));
        assertThat(result).containsExactlyElementsOf(mockItem.attributes());
    }

    @SuppressWarnings("unchecked")
    @Test
    void invoke_shouldReturnNull_whenItemDoesNotExist() {
        // Given
        when(table.getItem(any(KeyAttribute.class), any(KeyAttribute.class))).thenReturn(null);

        // When
        Map<String, Object> result = (Map<String, Object>) getItemOperation.invoke(dynamoDB);

        // Then
        verify(dynamoDB, times(1)).getTable(TestDynamoDBData.ActualValue.TABLE_NAME);
        verify(table, times(1)).getItem(any(PrimaryKey.class));
        assertThat(result).isNull();
    }

    @Test
    public void replaceSecrets_shouldReplaceSecrets() {
        // Given
        String input = """
                     {
                     "type": "getItem",
                     "tableName": "secrets.TABLE_NAME_KEY",
                     "primaryKeyComponents":{"id":"secrets.KEY_ATTRIBUTE_VALUE"}
                     }""";
        OutboundConnectorContext context = getContextWithSecrets();
        AwsInput request = GSON.fromJson(input, AwsInput.class);
        // When
        context.replaceSecrets(request);
        // Then
        assertThat(request).isInstanceOf(GetItem.class);
        GetItem castedRequest = (GetItem) request;
        assertThat(castedRequest.getTableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
        assertThat(castedRequest.getPrimaryKeyComponents()).isEqualTo( GSON.fromJson("{\"id\":\"1234\"}", Object.class));
    }

}