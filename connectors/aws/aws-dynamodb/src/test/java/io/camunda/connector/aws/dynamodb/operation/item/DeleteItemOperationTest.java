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
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.DeleteItemResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import io.camunda.connector.aws.dynamodb.model.DeleteItem;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

  /**
   * Golden-JSON shape test: pins the exact JSON the v1 deleteItem operation writes to process
   * variables today, so the AWS SDK v2 migration must reproduce it unchanged (migration contract
   * for #7973). Demonstrates the "attributes ARE populated" quirk: a caller that requests {@code
   * ReturnValues=ALL_OLD} gets back the deleted item's raw v1 {@link AttributeValue}s -- lowercase
   * field names, all-null padding around whichever member is actually set.
   */
  @Test
  public void deleteItemOutcome_serializesToDocumentedV1JsonShape() throws Exception {
    // Given a DeleteItem response with returned attributes (ReturnValues=ALL_OLD) and consumed
    // capacity, as a live call with those options would return.
    Map<String, AttributeValue> attributes = new LinkedHashMap<>();
    attributes.put("id", new AttributeValue().withS("1234"));
    attributes.put("age", new AttributeValue().withN("30"));
    DeleteItemResult deleteItemResult = new DeleteItemResult();
    deleteItemResult.setAttributes(attributes);
    DeleteItemOutcome realOutcome = new DeleteItemOutcome(deleteItemResult);

    Map<String, Object> primaryKeyComponents = new HashMap<>();
    primaryKeyComponents.put("id", "1234");
    DeleteItem deleteItem =
        new DeleteItem(TestDynamoDBData.ActualValue.TABLE_NAME, primaryKeyComponents);
    DeleteItemOperation operation = new DeleteItemOperation(deleteItem);
    when(table.deleteItem(new KeyAttribute("id", "1234"))).thenReturn(realOutcome);

    // When
    Object result = operation.invoke(dynamoDB);

    // Then: attributes came back non-null, so DeleteItemOutcome#getItem() (which wraps
    // DeleteItemResult#getAttributes()) is a non-null (but getter-less) Item -- serializing as
    // {}, not null (contrast with AddItemOperationTest, where no attributes come back at all).
    // Built via readTree(writeValueAsString(...)), not valueToTree(): see AddItemOperationTest
    // for why (valueToTree() strips trailing zeroes off BigDecimal values -- not relevant to
    // *this* fixture's values, but kept consistent with the other golden tests in this module).
    JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(result));
    String expectedJson =
        """
        {
          "item": { },
          "deleteItemResult": {
            "sdkResponseMetadata": null,
            "sdkHttpMetadata": null,
            "attributes": {
              "id": { "s": "1234", "n": null, "b": null, "m": null, "l": null,
                      "ss": null, "ns": null, "bs": null, "null": null, "bool": null },
              "age": { "s": null, "n": "30", "b": null, "m": null, "l": null,
                       "ss": null, "ns": null, "bs": null, "null": null, "bool": null }
            },
            "consumedCapacity": null,
            "itemCollectionMetrics": null
          }
        }
        """;
    JsonNode expected = objectMapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);

    // Deliberately no exact writeValueAsString() pin here: AWS SDK v1's model classes
    // (DeleteItemResult, AttributeValue, ...) are plain, unannotated JavaBeans with no
    // @JsonPropertyOrder, and Jackson's reflection-based property order for them was empirically
    // observed to change between separate JVM invocations of this exact test on this exact SDK
    // version (see AddItemOperationTest for details). Tree equality above -- which compares JSON
    // objects key-by-key regardless of order -- is the reliable way to pin this shape.
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
