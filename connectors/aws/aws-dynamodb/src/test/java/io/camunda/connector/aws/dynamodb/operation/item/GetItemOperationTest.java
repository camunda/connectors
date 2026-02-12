/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import io.camunda.connector.aws.dynamodb.model.GetItem;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

class GetItemOperationTest extends BaseDynamoDbOperationTest {

  private GetItemOperation getItemOperation;
  @Captor private ArgumentCaptor<GetItemRequest> requestCaptor;

  @BeforeEach
  public void setup() {
    GetItem getItem =
        new GetItem(TestDynamoDBData.ActualValue.TABLE_NAME, Map.of("id", "1", "type", "user"));
    getItemOperation = new GetItemOperation(getItem);
  }

  @SuppressWarnings("unchecked")
  @Test
  void invoke_shouldReturnItemAttributes_whenItemExists() {
    // Given
    Map<String, AttributeValue> item =
        Map.of(
            "id", AttributeValue.builder().s("1").build(),
            "type", AttributeValue.builder().s("user").build(),
            "name", AttributeValue.builder().s("Alice").build());
    when(dynamoDB.getItem(requestCaptor.capture()))
        .thenReturn(GetItemResponse.builder().item(item).build());

    // When
    Map<String, Object> result = (Map<String, Object>) getItemOperation.invoke(dynamoDB);

    // Then
    verify(dynamoDB, times(1)).getItem(any(GetItemRequest.class));
    ArrayList<Map.Entry<String, AttributeValue>> keyAttributes =
        new ArrayList<>(requestCaptor.getValue().key().entrySet());
    assertThat(keyAttributes).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("id", "type");
    assertThat(result)
        .containsEntry("id", "1")
        .containsEntry("type", "user")
        .containsEntry("name", "Alice");
  }

  @SuppressWarnings("unchecked")
  @Test
  void invoke_shouldReturnNull_whenItemDoesNotExist() {
    // Given
    when(dynamoDB.getItem(requestCaptor.capture())).thenReturn(GetItemResponse.builder().build());

    // When
    Map<String, Object> result = (Map<String, Object>) getItemOperation.invoke(dynamoDB);

    // Then
    verify(dynamoDB, times(1)).getItem(any(GetItemRequest.class));
    assertThat(result).isNull();
  }

  @Test
  public void replaceSecrets_shouldReplaceSecrets() throws JsonProcessingException {
    // Given
    String input =
        """
                     {
                     "type": "getItem",
                     "tableName": "{{secrets.TABLE_NAME_KEY}}",
                     "primaryKeyComponents":{"id":"{{secrets.KEY_ATTRIBUTE_VALUE}}"}
                     }""";
    OutboundConnectorContext context = getContextWithSecrets(input);
    AwsInput request = context.bindVariables(AwsInput.class);
    // Then
    assertThat(request).isInstanceOf(GetItem.class);
    GetItem castedRequest = (GetItem) request;
    assertThat(castedRequest.tableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
    assertThat(castedRequest.primaryKeyComponents())
        .isEqualTo(objectMapper.readValue("{\"id\":\"1234\"}", Map.class));
  }
}
