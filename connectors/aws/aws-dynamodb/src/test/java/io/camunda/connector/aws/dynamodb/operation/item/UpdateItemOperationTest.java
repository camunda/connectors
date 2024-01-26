/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.amazonaws.services.dynamodbv2.document.AttributeUpdate;
import com.amazonaws.services.dynamodbv2.document.KeyAttribute;
import com.amazonaws.services.dynamodbv2.document.UpdateItemOutcome;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import io.camunda.connector.aws.dynamodb.model.UpdateItem;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

class UpdateItemOperationTest extends BaseDynamoDbOperationTest {
  private UpdateItemOperation updateItemOperation;
  @Mock private UpdateItemOutcome updateItemOutcome;
  @Captor private ArgumentCaptor<UpdateItemSpec> updateItemSpecArgumentCaptor;
  private KeyAttribute keyAttribute;

  @BeforeEach
  public void setUp() {

    keyAttribute = new KeyAttribute("id", "123");
    AttributeUpdate attributeUpdate = new AttributeUpdate("name").addElements("John Doe");

    Map<String, Object> primaryKey = Map.of(keyAttribute.getName(), keyAttribute.getValue());
    Map<String, Object> attributeUpdates = Map.of(attributeUpdate.getAttributeName(), "John Doe");

    UpdateItem updateItem =
        new UpdateItem(
            TestDynamoDBData.ActualValue.TABLE_NAME, primaryKey, attributeUpdates, "PUT");
    updateItemOperation = new UpdateItemOperation(updateItem);
  }

  @Test
  public void testInvoke() {
    // Given
    when(table.updateItem(updateItemSpecArgumentCaptor.capture())).thenReturn(updateItemOutcome);
    // When
    Object result = updateItemOperation.invoke(dynamoDB);
    // Then
    assertThat(result).isInstanceOf(UpdateItemOutcome.class);
    assertThat(((UpdateItemOutcome) result).getItem()).isEqualTo(updateItemOutcome.getItem());
    UpdateItemSpec value = updateItemSpecArgumentCaptor.getValue();
    assertThat(value.getKeyComponents()).contains(keyAttribute);
    assertThat(value.getValueMap()).isEqualTo(Map.of(":name", "John Doe"));
  }

  @Test
  public void replaceSecrets_shouldReplaceSecrets() throws JsonProcessingException {
    // Given
    String input =
        """
                     {
                     "type": "updateItem",
                     "tableName": "secrets.TABLE_NAME_KEY",
                     "primaryKeyComponents":{"id":"secrets.KEY_ATTRIBUTE_VALUE"},
                     "keyAttributes":{"keyAttribute":"secrets.KEY_ATTRIBUTE_VALUE"},
                     "attributeAction":"PUT"
                     }""";
    OutboundConnectorContext context = getContextWithSecrets(input);
    // When
    AwsInput request = context.bindVariables(AwsInput.class);
    // Then
    assertThat(request).isInstanceOf(UpdateItem.class);
    UpdateItem castedRequest = (UpdateItem) request;
    assertThat(castedRequest.tableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
    assertThat(castedRequest.primaryKeyComponents())
        .isEqualTo(objectMapper.readValue("{\"id\":\"1234\"}", Object.class));
    assertThat(castedRequest.keyAttributes())
        .isEqualTo(objectMapper.readValue("{\"keyAttribute\":\"1234\"}", Object.class));
  }
}
