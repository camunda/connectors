/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import io.camunda.connector.aws.dynamodb.model.GetItem;
import io.camunda.connector.aws.dynamodb.util.AttributeValueConverter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

class GetItemOperationTest extends BaseDynamoDbOperationTest {

  private GetItemOperation getItemOperation;

  @Test
  void invoke_shouldReturnItemAttributes_whenItemExists() {
    // Given
    GetItem getItem =
        new GetItem(TestDynamoDBData.ActualValue.TABLE_NAME, Map.of("id", "1", "type", "user"));
    getItemOperation = new GetItemOperation(getItem);

    Map<String, AttributeValue> itemAttributes = new LinkedHashMap<>();
    itemAttributes.put("id", AttributeValue.fromS("1"));
    itemAttributes.put("type", AttributeValue.fromS("user"));
    itemAttributes.put("name", AttributeValue.fromS("Alice"));
    GetItemResponse response = GetItemResponse.builder().item(itemAttributes).build();
    ArgumentCaptor<GetItemRequest> requestCaptor = ArgumentCaptor.forClass(GetItemRequest.class);
    when(dynamoDbClient.getItem(requestCaptor.capture())).thenReturn(response);

    // When
    Object result = getItemOperation.invoke(dynamoDbClient);

    // Then
    assertThat(requestCaptor.getValue().key())
        .isEqualTo(Map.of("id", AttributeValue.fromS("1"), "type", AttributeValue.fromS("user")));
    assertThat(result).isEqualTo(AttributeValueConverter.toSingleKeyEntries(itemAttributes));
  }

  @Test
  void invoke_shouldReturnNull_whenItemDoesNotExist() {
    // Given
    GetItem getItem = new GetItem(TestDynamoDBData.ActualValue.TABLE_NAME, Map.of("id", "1"));
    getItemOperation = new GetItemOperation(getItem);
    when(dynamoDbClient.getItem(any(GetItemRequest.class)))
        .thenReturn(GetItemResponse.builder().build());

    // When
    Object result = getItemOperation.invoke(dynamoDbClient);

    // Then
    assertThat(result).isNull();
  }

  /**
   * Golden-JSON shape test: pins the exact JSON the v1 getItem operation writes to process
   * variables today, so the AWS SDK v2 migration must reproduce it unchanged (migration contract
   * for #7973). Pins the "array of single-key objects" quirk.
   */
  @Test
  void getItem_serializesToDocumentedV1JsonShape_whenItemExists() throws Exception {
    // Given a realistic item with multiple attributes, in a defined order
    Map<String, AttributeValue> itemAttributes = new LinkedHashMap<>();
    itemAttributes.put("id", AttributeValue.fromS("1"));
    itemAttributes.put("name", AttributeValue.fromS("Alice"));
    itemAttributes.put("age", AttributeValue.fromN("30"));
    GetItem getItem = new GetItem(TestDynamoDBData.ActualValue.TABLE_NAME, Map.of("id", "1"));
    GetItemOperation operation = new GetItemOperation(getItem);
    when(dynamoDbClient.getItem(any(GetItemRequest.class)))
        .thenReturn(GetItemResponse.builder().item(itemAttributes).build());

    // When
    Object result = operation.invoke(dynamoDbClient);

    // Then: an array of single-key objects, one per attribute, in the item's own field order --
    // not a single merged {"id":"1","name":"Alice","age":30} object.
    // Note: built via readTree(writeValueAsString(...)), NOT valueToTree() -- valueToTree()
    // builds its JsonNode tree through JsonNodeFactory#numberNode(BigDecimal), which strips
    // trailing zeroes (e.g. 30 -> 3E+1), unlike the actual wire format the runtime writes to
    // process variables. Round-tripping through the real serialized string avoids that artifact.
    JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(result));
    String expectedJson =
        """
        [ { "id": "1" }, { "name": "Alice" }, { "age": 30 } ]
        """;
    JsonNode expected = objectMapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);
    // This shape derives from a List and Jackson's built-in Map.Entry-like serialization (not
    // bean reflection), so the serialized order is fully deterministic and safe to pin verbatim.
    assertThat(objectMapper.writeValueAsString(result))
        .isEqualTo(objectMapper.writeValueAsString(expected));
  }

  /**
   * Golden-JSON shape test companion: when the item does not exist, the operation returns {@code
   * null} -- which the production mapper serializes as the JSON literal {@code null} -- not an
   * empty array. AWS SDK v2's {@code GetItemResponse#item()} returns an empty map (never {@code
   * null}) when there is no match, so {@code GetItemOperation} must check {@code hasItem()}
   * explicitly to reproduce this.
   */
  @Test
  void getItem_serializesToDocumentedV1JsonShape_whenItemDoesNotExist() throws Exception {
    GetItem getItem = new GetItem(TestDynamoDBData.ActualValue.TABLE_NAME, Map.of("id", "1"));
    GetItemOperation operation = new GetItemOperation(getItem);
    when(dynamoDbClient.getItem(any(GetItemRequest.class)))
        .thenReturn(GetItemResponse.builder().build());

    Object result = operation.invoke(dynamoDbClient);

    assertThat(result).isNull();
    assertThat(objectMapper.writeValueAsString(result)).isEqualTo("null");
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
