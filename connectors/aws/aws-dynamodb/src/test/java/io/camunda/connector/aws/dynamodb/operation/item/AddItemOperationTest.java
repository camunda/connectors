/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AddItem;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

class AddItemOperationTest extends BaseDynamoDbOperationTest {
  private AddItem addItemModel;
  @Captor private ArgumentCaptor<PutItemRequest> requestCaptor;

  @BeforeEach
  public void setUp() {
    addItemModel =
        new AddItem(
            TestDynamoDBData.ActualValue.TABLE_NAME,
            Map.of("id", TestDynamoDBData.ActualValue.KEY_ATTRIBUTE_VALUE));
  }

  @Test
  public void testInvoke() throws JsonProcessingException {
    AddItemOperation addItemOperation = new AddItemOperation(addItemModel);
    PutItemResponse response = PutItemResponse.builder().build();
    org.mockito.Mockito.when(dynamoDB.putItem(requestCaptor.capture())).thenReturn(response);

    PutItemResponse result = addItemOperation.invoke(dynamoDB);
    verify(dynamoDB).putItem(requestCaptor.getValue());
    assertThat(result).isEqualTo(response);

    PutItemRequest request = requestCaptor.getValue();
    assertThat(request.tableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
    AttributeValue id = request.item().get("id");
    assertThat(id.s()).isEqualTo(TestDynamoDBData.ActualValue.KEY_ATTRIBUTE_VALUE);
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
