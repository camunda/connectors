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

import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.KeyAttribute;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import io.camunda.connector.aws.dynamodb.model.GetItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

class GetItemOperationTest extends BaseDynamoDbOperationTest {

  private GetItemOperation getItemOperation;
  @Captor private ArgumentCaptor<PrimaryKey> keyAttributesCaptor;

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
    Item mockItem = Item.fromMap(Map.of("id", "1", "type", "user", "name", "Alice"));
    when(table.getItem(keyAttributesCaptor.capture())).thenReturn(mockItem);

    // When
    Iterable<Map.Entry<String, Object>> result =
        (Iterable<Map.Entry<String, Object>>) getItemOperation.invoke(dynamoDB);

    // Then
    verify(dynamoDB, times(1)).getTable(TestDynamoDBData.ActualValue.TABLE_NAME);
    verify(table, times(1)).getItem(any(PrimaryKey.class));
    ArrayList<KeyAttribute> keyAttributes =
        new ArrayList<>(keyAttributesCaptor.getValue().getComponents());
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

  /**
   * Golden-JSON shape test: pins the exact JSON the v1 getItem operation writes to process
   * variables today, so the AWS SDK v2 migration must reproduce it unchanged (migration contract
   * for #7973). Pins the "array of single-key objects" quirk: {@code Item#attributes()} returns an
   * {@code Iterable<Map.Entry<String,Object>>}, and Jackson's built-in Map.Entry serializer writes
   * each entry as its own single-key JSON object -- NOT a single merged JSON object.
   */
  @Test
  void getItem_serializesToDocumentedV1JsonShape_whenItemExists() throws Exception {
    // Given a realistic item with multiple attributes, in a defined order
    Map<String, Object> itemAttributes = new LinkedHashMap<>();
    itemAttributes.put("id", "1");
    itemAttributes.put("name", "Alice");
    itemAttributes.put("age", 30);
    Item mockItem = Item.fromMap(itemAttributes);
    GetItem getItem = new GetItem(TestDynamoDBData.ActualValue.TABLE_NAME, Map.of("id", "1"));
    GetItemOperation operation = new GetItemOperation(getItem);
    when(table.getItem(any(PrimaryKey.class))).thenReturn(mockItem);

    // When
    Object result = operation.invoke(dynamoDB);

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
    // This shape derives from a List and Jackson's built-in Map.Entry serializer (not bean
    // reflection), so -- unlike the outcome envelopes in AddItemOperationTest et al. -- the
    // serialized order is fully deterministic and safe to pin verbatim.
    assertThat(objectMapper.writeValueAsString(result))
        .isEqualTo(objectMapper.writeValueAsString(expected));
  }

  /**
   * Golden-JSON shape test companion: when the item does not exist, the operation returns {@code
   * null} (via {@code Optional.ofNullable(...).map(Item::attributes).orElse(null)}), which the
   * production mapper serializes as the JSON literal {@code null} -- not an empty array.
   */
  @Test
  void getItem_serializesToDocumentedV1JsonShape_whenItemDoesNotExist() throws Exception {
    GetItem getItem = new GetItem(TestDynamoDBData.ActualValue.TABLE_NAME, Map.of("id", "1"));
    GetItemOperation operation = new GetItemOperation(getItem);
    when(table.getItem(any(PrimaryKey.class))).thenReturn(null);

    Object result = operation.invoke(dynamoDB);

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
