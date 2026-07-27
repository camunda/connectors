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
import io.camunda.connector.aws.dynamodb.model.DeleteItem;
import io.camunda.connector.aws.dynamodb.model.DeleteItemResult;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;

class DeleteItemOperationTest extends BaseDynamoDbOperationTest {

  private DeleteItemOperation deleteItemOperation;

  private DeleteItem newDeleteItem() {
    Map<String, Object> primaryKeyComponents = new HashMap<>();
    primaryKeyComponents.put("id", "1234");
    return new DeleteItem(TestDynamoDBData.ActualValue.TABLE_NAME, primaryKeyComponents);
  }

  @Test
  public void testInvoke() {
    // Given
    deleteItemOperation = new DeleteItemOperation(newDeleteItem());
    DeleteItemResponse response = DeleteItemResponse.builder().build();
    ArgumentCaptor<DeleteItemRequest> requestCaptor =
        ArgumentCaptor.forClass(DeleteItemRequest.class);
    when(dynamoDbClient.deleteItem(requestCaptor.capture())).thenReturn(response);

    // When
    Object result = this.deleteItemOperation.invoke(dynamoDbClient);

    // Then
    assertThat(result).isEqualTo(DeleteItemResult.from(response));
    DeleteItemRequest request = requestCaptor.getValue();
    assertThat(request.tableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
    assertThat(request.key()).isEqualTo(Map.of("id", AttributeValue.fromS("1234")));
  }

  /**
   * Golden-JSON shape test: pins the exact JSON the v1 deleteItem operation writes to process
   * variables today, so the AWS SDK v2 migration must reproduce it unchanged (migration contract
   * for #7973). Models the response the production overload actually returns: {@link
   * DeleteItemOperation} never sets {@code ReturnValues}, so a live call gets {@code NONE} and
   * comes back with no attributes -- but the SDK always attaches request-id and HTTP metadata to
   * every successful call (see AddItemOperationTest for the same shape).
   */
  @Test
  public void deleteItemOutcome_serializesToDocumentedV1JsonShape() throws Exception {
    DeleteItemResponse.Builder responseBuilder = DeleteItemResponse.builder();
    responseBuilder.responseMetadata(buildSdkResponseMetadata("929bf054-193b-48e6-req"));
    responseBuilder.sdkHttpResponse(buildSdkHttpResponse(200));
    DeleteItemResponse response = responseBuilder.build();
    deleteItemOperation = new DeleteItemOperation(newDeleteItem());
    when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(response);

    // When
    Object result = deleteItemOperation.invoke(dynamoDbClient);

    // Then: no ReturnValues means the response carries no attributes, so item/attributes are
    // null here, not {}.
    JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(result));
    String expectedJson =
        """
        {
          "item": null,
          "deleteItemResult": {
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
