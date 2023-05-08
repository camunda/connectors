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
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.item.UpdateItem;
import io.camunda.connector.aws.model.AwsInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class UpdateItemOperationTest extends BaseDynamoDbOperationTest {
    private UpdateItemOperation updateItemOperation;
    private UpdateItem updateItem;
    @Mock
    private UpdateItemOutcome updateItemOutcome;
    @Captor
    private ArgumentCaptor<PrimaryKey> primaryKeyArgumentCaptor;
    @Captor
    private ArgumentCaptor<AttributeUpdate> attributeUpdateArgumentCaptor;
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

    @Test
    public void testInvoke() {
        // Given
        when(table.updateItem(
                primaryKeyArgumentCaptor.capture(),
                attributeUpdateArgumentCaptor.capture()
        )).thenReturn(updateItemOutcome);
        // When
        Object result = updateItemOperation.invoke(dynamoDB);
        // Then
        assertThat(result).isInstanceOf(UpdateItemOutcome.class);
        assertThat(((UpdateItemOutcome) result).getItem()).isEqualTo(updateItemOutcome.getItem());

        assertThat(primaryKeyArgumentCaptor.getValue().getComponents()).contains(keyAttribute);
        assertThat(attributeUpdateArgumentCaptor.getValue().getAttributeName()).isEqualTo(attributeUpdate.getAttributeName());
    }

    @Test
    public void invoke_shouldThrowExceptionWhenUpdateActionIsInvalid() {
        //Given
        updateItem.setAttributeAction("ADD");
        //When and Then
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> updateItemOperation.invoke(dynamoDB),
                        "IllegalArgumentException was expected");
        assertThat(thrown.getMessage()).contains("Unsupported action [ADD]");

    }

    @Test
    public void replaceSecrets_shouldReplaceSecrets() {
        // Given
        String input = """
                     {
                     "type": "updateItem",
                     "tableName": "secrets.TABLE_NAME_KEY",
                     "primaryKeyComponents":{"id":"secrets.KEY_ATTRIBUTE_VALUE"},
                     "keyAttributes":{"keyAttribute":"secrets.KEY_ATTRIBUTE_VALUE"}
                     }""";
        OutboundConnectorContext context = getContextWithSecrets();
        AwsInput request = GSON.fromJson(input, AwsInput.class);
        // When
        context.replaceSecrets(request);
        // Then
        assertThat(request).isInstanceOf(UpdateItem.class);
        UpdateItem castedRequest = (UpdateItem) request;
        assertThat(castedRequest.getTableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
        assertThat(castedRequest.getPrimaryKeyComponents()).isEqualTo( GSON.fromJson("{\"id\":\"1234\"}", Object.class));
        assertThat(castedRequest.getKeyAttributes()).isEqualTo( GSON.fromJson("{\"keyAttribute\":\"1234\"}", Object.class));
    }
}