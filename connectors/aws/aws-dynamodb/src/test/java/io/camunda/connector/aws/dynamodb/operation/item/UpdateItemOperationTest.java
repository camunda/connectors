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

import com.amazonaws.services.dynamodbv2.document.AttributeUpdate;
import com.amazonaws.services.dynamodbv2.document.KeyAttribute;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.amazonaws.services.dynamodbv2.document.UpdateItemOutcome;
import com.amazonaws.services.dynamodbv2.model.AttributeAction;
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import io.camunda.connector.aws.dynamodb.model.UpdateItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

class UpdateItemOperationTest extends BaseDynamoDbOperationTest {
  @Mock private UpdateItemOutcome updateItemOutcome;
  @Captor private ArgumentCaptor<PrimaryKey> primaryKeyArgumentCaptor;
  @Captor private ArgumentCaptor<AttributeUpdate[]> attributeUpdateArgumentCaptor;

  @ParameterizedTest
  @MethodSource("updateItemCases")
  void testUpdateItemOperation(String attributeName, Object newValue) {
    // Setup
    AttributeUpdate attributeUpdate = new AttributeUpdate(attributeName).put(newValue);
    Map<String, Object> attributeUpdates = Map.of(attributeUpdate.getAttributeName(), newValue);
    UpdateItem updateItem =
        new UpdateItem(
            TestDynamoDBData.ActualValue.TABLE_NAME, Map.of("id", "123"), attributeUpdates, "PUT");
    UpdateItemOperation operation = new UpdateItemOperation(updateItem);

    // Given
    when(table.updateItem(
            primaryKeyArgumentCaptor.capture(), attributeUpdateArgumentCaptor.capture()))
        .thenReturn(updateItemOutcome);
    // When
    Object result = operation.invoke(dynamoDB);
    // Then
    assertThat(result).isInstanceOf(UpdateItemOutcome.class);

    PrimaryKey value = primaryKeyArgumentCaptor.getValue();

    assertThat(value.getComponents().contains(new KeyAttribute("id", "123"))).isTrue();

    AttributeUpdate expectedAttributeUpdate = attributeUpdateArgumentCaptor.getValue()[0];
    assertThat(expectedAttributeUpdate.getAttributeName()).isEqualTo(attributeName);
    assertThat(expectedAttributeUpdate.getValue()).isEqualTo(newValue);
    assertThat(expectedAttributeUpdate.getAction()).isEqualTo(AttributeAction.PUT);
  }

  @ParameterizedTest
  @MethodSource("updateItemCases")
  void testDeleteItemOperation(String attributeName, Object newValue) {
    // Setup
    AttributeUpdate attributeUpdate = new AttributeUpdate(attributeName).put(newValue);
    Map<String, Object> attributeUpdates = Map.of(attributeUpdate.getAttributeName(), newValue);
    UpdateItem updateItem =
        new UpdateItem(
            TestDynamoDBData.ActualValue.TABLE_NAME,
            Map.of("id", "123"),
            attributeUpdates,
            "DELETE");
    UpdateItemOperation operation = new UpdateItemOperation(updateItem);

    // Given
    when(table.updateItem(
            primaryKeyArgumentCaptor.capture(), attributeUpdateArgumentCaptor.capture()))
        .thenReturn(updateItemOutcome);
    // When
    Object result = operation.invoke(dynamoDB);
    // Then
    assertThat(result).isInstanceOf(UpdateItemOutcome.class);

    PrimaryKey value = primaryKeyArgumentCaptor.getValue();

    assertThat(value.getComponents().contains(new KeyAttribute("id", "123"))).isTrue();

    AttributeUpdate expectedAttributeUpdate = attributeUpdateArgumentCaptor.getValue()[0];
    assertThat(expectedAttributeUpdate.getAttributeName()).isEqualTo(attributeName);
    assertThat(expectedAttributeUpdate.getValue()).isNull();
    assertThat(expectedAttributeUpdate.getAction()).isEqualTo(AttributeAction.DELETE);
  }

  static Stream<Arguments> updateItemCases() {
    return Stream.of(
        // String attribute
        Arguments.of("status", "Active"),

        // Number attribute
        Arguments.of("age", 45),

        // Set attribute - String Set
        Arguments.of("stringSet", Set.of("c", "d")),

        // Set attribute - Number Set
        Arguments.of("numberSet", Set.of(new BigDecimal(3), new BigDecimal(4))),
        // List attribute
        Arguments.of("listAttr", List.of("b", 2)),
        // Map attribute
        Arguments.of("mapAttr", Map.of("key2", "value2")),

        // Complex Map attribute with nested structures
        Arguments.of(
            "complexMap",
            Map.of(
                "nestedKey2",
                Map.of("nestedMapKey2", "nestedValue2"),
                "nestedList",
                List.of("item3", "item4"))),
        // Map attribute with nested Map
        Arguments.of("nestedMap", Map.of("key", Map.of("innerKey2", "innerValue2"))),

        // Map attribute with a Set of Numbers
        Arguments.of(
            "mapWithNumberSet", Map.of("key", Set.of(new BigDecimal(3), new BigDecimal(4)))),

        // Complex Map with nested Map and List
        Arguments.of(
            "complexMapWithNestedMapAndList",
            Map.of(
                "mapKey",
                Map.of(
                    "nestedMapKey",
                    "newNestedMapValue",
                    "nestedListKey",
                    List.of("item3", "item4")),
                "simpleKey",
                "updatedSimpleValue")),

        // Map with nested Set of Numbers
        Arguments.of(
            "mapWithNestedNumberSet",
            Map.of("numberSetKey", Set.of(new BigDecimal(3), new BigDecimal(4))),
            Map.of(
                ":mapWithNestedNumberSet",
                Map.of("numberSetKey", Set.of(new BigDecimal(3), new BigDecimal(4))))),

        // Map with nested Set of Strings
        Arguments.of("mapWithNestedStringSet", Map.of("stringSetKey", Set.of("C", "D"))),

        // Map with nested complex types (Map and Set)
        Arguments.of(
            "mapWithComplexNestedTypes",
            Map.of(
                "nestedMap", Map.of("key2", "value2"),
                "nestedSet", Set.of(new BigDecimal(3), new BigDecimal(4)))));
  }

  /**
   * Golden-JSON shape test: pins the exact JSON the v1 updateItem operation writes to process
   * variables today, so the AWS SDK v2 migration must reproduce it unchanged (migration contract
   * for #7973). Models the response the production overload actually returns: {@link
   * UpdateItemOperation} calls {@code Table.updateItem(PrimaryKey, AttributeUpdate...)}, which
   * never sets {@code ReturnValues}, so a live call gets {@code NONE} and comes back with no
   * attributes -- but the SDK always attaches request-id and HTTP metadata to every successful call
   * (see AddItemOperationTest for the same shape). The raw-{@code AttributeValue} lowercase-
   * key/all-null-padding quirk this operation can never actually surface is instead pinned directly
   * against the model classes in AttributeValueSerializationTest.
   */
  @Test
  public void updateItemOutcome_serializesToDocumentedV1JsonShape() throws Exception {
    // Given a realistic UpdateItem response. UpdateItemOperation never requests ReturnValues, so
    // a live call returns no attributes -- but the SDK always attaches request-id and HTTP
    // metadata to every successful call.
    UpdateItemResult updateItemResult = new UpdateItemResult();
    updateItemResult.setSdkResponseMetadata(buildSdkResponseMetadata("929bf054-193b-48e6-req"));
    updateItemResult.setSdkHttpMetadata(buildSdkHttpMetadata(200));
    UpdateItemOutcome realOutcome = new UpdateItemOutcome(updateItemResult);

    UpdateItem updateItem =
        new UpdateItem(
            TestDynamoDBData.ActualValue.TABLE_NAME,
            Map.of("id", "123"),
            Map.of("status", "Active"),
            "PUT");
    UpdateItemOperation operation = new UpdateItemOperation(updateItem);
    when(table.updateItem(any(PrimaryKey.class), any(AttributeUpdate[].class)))
        .thenReturn(realOutcome);

    // When
    Object result = operation.invoke(dynamoDB);

    // Then: no ReturnValues means the response carries no attributes, so
    // UpdateItemOutcome#getItem() returns null here, not {}.
    // Built via readTree(writeValueAsString(...)), not valueToTree(): see AddItemOperationTest
    // for why (valueToTree() strips trailing zeroes off BigDecimal values -- not relevant to
    // *this* fixture's values, but kept consistent with the other golden tests in this module).
    JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(result));
    String expectedJson =
        """
        {
          "item": null,
          "updateItemResult": {
            "sdkResponseMetadata": { "requestId": "929bf054-193b-48e6-req" },
            "sdkHttpMetadata": {
              "httpHeaders": { "Content-Length": "85" },
              "httpStatusCode": 200,
              "allHttpHeaders": { "Content-Length": ["85"] }
            },
            "attributes": null,
            "consumedCapacity": null,
            "itemCollectionMetrics": null
          }
        }
        """;
    JsonNode expected = objectMapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);

    // Deliberately no exact writeValueAsString() pin here: AWS SDK v1's model classes
    // (UpdateItemResult, AttributeValue, ...) are plain, unannotated JavaBeans with no
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
