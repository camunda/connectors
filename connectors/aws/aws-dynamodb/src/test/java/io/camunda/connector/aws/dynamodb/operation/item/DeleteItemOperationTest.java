/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.amazonaws.services.dynamodbv2.document.DeleteItemOutcome;
import com.amazonaws.services.dynamodbv2.document.KeyAttribute;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import io.camunda.connector.aws.dynamodb.model.DeleteItem;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

class DeleteItemOperationTest extends BaseDynamoDbOperationTest {

  private DeleteItemOperation deleteItemOperation;
  @Mock private DeleteItemOutcome deleteItemOutcome;
  @Captor private ArgumentCaptor<KeyAttribute> keyAttributeArgumentCaptor;

  @BeforeEach
  public void setup() {
    Map<String, Object> primaryKeyComponents = new HashMap<>();
    primaryKeyComponents.put("id", "1234");
    DeleteItem deleteItem =
        new DeleteItem(TestDynamoDBData.ActualValue.TABLE_NAME, primaryKeyComponents);
    this.deleteItemOperation = new DeleteItemOperation(deleteItem);
  }

  @Test
  public void testInvoke() {
    // Given
    when(table.deleteItem(keyAttributeArgumentCaptor.capture())).thenReturn(deleteItemOutcome);
    // When
    Object result = this.deleteItemOperation.invoke(dynamoDB);
    // Then
    assertThat(result).isEqualTo(deleteItemOutcome);
    KeyAttribute keyAttribute = keyAttributeArgumentCaptor.getValue();
    assertThat(keyAttribute.getName()).isEqualTo("id");
    assertThat(keyAttribute.getValue()).isEqualTo("1234");
  }

  @Test
  public void replaceSecrets_shouldReplaceSecrets() throws JsonProcessingException {
    // Given
    String input =
        """
                     {
                     "type": "deleteItem",
                     "tableName": "{{secrets.TABLE_NAME_KEY}}",
                     "primaryKeyComponents":{"id":"{{secrets.KEY_ATTRIBUTE_VALUE}}"}
                     }""";
    OutboundConnectorContext context = getContextWithSecrets(input);
    var request = context.bindVariables(AwsInput.class);
    // Then
    assertThat(request).isInstanceOf(DeleteItem.class);
    DeleteItem castedRequest = (DeleteItem) request;
    assertThat(castedRequest.tableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
    assertThat(castedRequest.primaryKeyComponents())
        .isEqualTo(objectMapper.readValue("{\"id\":\"1234\"}", Map.class));
  }
}
