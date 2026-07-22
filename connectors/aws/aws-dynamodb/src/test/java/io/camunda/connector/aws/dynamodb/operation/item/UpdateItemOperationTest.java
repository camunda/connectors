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
import io.camunda.connector.aws.dynamodb.model.UpdateItem;
import io.camunda.connector.aws.dynamodb.util.AttributeValueConverter;
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
import software.amazon.awssdk.services.dynamodb.model.AttributeAction;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

class UpdateItemOperationTest extends BaseDynamoDbOperationTest {

  @ParameterizedTest
  @MethodSource("updateItemCases")
  void testUpdateItemOperation(String attributeName, Object newValue) {
    // Given
    Map<String, Object> attributeUpdates = Map.of(attributeName, newValue);
    UpdateItem updateItem =
        new UpdateItem(
            TestDynamoDBData.ActualValue.TABLE_NAME, Map.of("id", "123"), attributeUpdates, "PUT");
    UpdateItemOperation operation = new UpdateItemOperation(updateItem);
    ArgumentCaptor<UpdateItemRequest> requestCaptor =
        ArgumentCaptor.forClass(UpdateItemRequest.class);
    when(dynamoDbClient.updateItem(requestCaptor.capture()))
        .thenReturn(UpdateItemResponse.builder().build());

    // When
    Object result = operation.invoke(dynamoDbClient);

    // Then
    assertThat(result).isNotNull();
    UpdateItemRequest request = requestCaptor.getValue();
    assertThat(request.key()).isEqualTo(Map.of("id", AttributeValue.fromS("123")));

    AttributeValueUpdate expectedAttributeUpdate = request.attributeUpdates().get(attributeName);
    assertThat(expectedAttributeUpdate.action()).isEqualTo(AttributeAction.PUT);
    assertThat(expectedAttributeUpdate.value())
        .isEqualTo(AttributeValueConverter.toAttributeValue(newValue));
  }

  @ParameterizedTest
  @MethodSource("updateItemCases")
  void testDeleteItemOperation(String attributeName, Object newValue) {
    // Given
    Map<String, Object> attributeUpdates = Map.of(attributeName, newValue);
    UpdateItem updateItem =
        new UpdateItem(
            TestDynamoDBData.ActualValue.TABLE_NAME,
            Map.of("id", "123"),
            attributeUpdates,
            "DELETE");
    UpdateItemOperation operation = new UpdateItemOperation(updateItem);
    ArgumentCaptor<UpdateItemRequest> requestCaptor =
        ArgumentCaptor.forClass(UpdateItemRequest.class);
    when(dynamoDbClient.updateItem(requestCaptor.capture()))
        .thenReturn(UpdateItemResponse.builder().build());

    // When
    Object result = operation.invoke(dynamoDbClient);

    // Then
    assertThat(result).isNotNull();
    UpdateItemRequest request = requestCaptor.getValue();
    assertThat(request.key()).isEqualTo(Map.of("id", AttributeValue.fromS("123")));

    AttributeValueUpdate expectedAttributeUpdate = request.attributeUpdates().get(attributeName);
    assertThat(expectedAttributeUpdate.action()).isEqualTo(AttributeAction.DELETE);
    assertThat(expectedAttributeUpdate.value()).isNull();
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
            Map.of("numberSetKey", Set.of(new BigDecimal(3), new BigDecimal(4)))),

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
   * for #7973). {@link UpdateItemOperation} never sets {@code ReturnValues}, so a live call gets
   * {@code NONE} and comes back with no attributes -- but the SDK always attaches request-id and
   * HTTP metadata to every successful call (see AddItemOperationTest for the same shape).
   */
  @Test
  public void updateItemOutcome_serializesToDocumentedV1JsonShape() throws Exception {
    UpdateItemResponse.Builder responseBuilder = UpdateItemResponse.builder();
    responseBuilder.responseMetadata(buildSdkResponseMetadata("929bf054-193b-48e6-req"));
    responseBuilder.sdkHttpResponse(buildSdkHttpResponse(200));
    UpdateItemResponse response = responseBuilder.build();

    UpdateItem updateItem =
        new UpdateItem(
            TestDynamoDBData.ActualValue.TABLE_NAME,
            Map.of("id", "123"),
            Map.of("status", "Active"),
            "PUT");
    UpdateItemOperation operation = new UpdateItemOperation(updateItem);
    when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(response);

    // When
    Object result = operation.invoke(dynamoDbClient);

    // Then: no ReturnValues means the response carries no attributes, so item/attributes are
    // null here, not {}.
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
