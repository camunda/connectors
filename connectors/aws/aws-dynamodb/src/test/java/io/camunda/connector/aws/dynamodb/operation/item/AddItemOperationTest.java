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
import io.camunda.connector.aws.dynamodb.model.AddItem;
import io.camunda.connector.aws.dynamodb.model.AddItemResult;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

class AddItemOperationTest extends BaseDynamoDbOperationTest {

  @Test
  public void testInvoke() {
    // Given
    AddItem addItemModel = new AddItem(TestDynamoDBData.ActualValue.TABLE_NAME, Map.of("id", "1"));
    PutItemResponse response = PutItemResponse.builder().build();
    ArgumentCaptor<PutItemRequest> requestCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
    when(dynamoDbClient.putItem(requestCaptor.capture())).thenReturn(response);
    AddItemOperation addItemOperation = new AddItemOperation(addItemModel);

    // When
    AddItemResult result = addItemOperation.invoke(dynamoDbClient);

    // Then
    assertThat(result).isEqualTo(AddItemResult.from(response));
    PutItemRequest request = requestCaptor.getValue();
    assertThat(request.tableName()).isEqualTo(TestDynamoDBData.ActualValue.TABLE_NAME);
    assertThat(request.item()).isEqualTo(Map.of("id", AttributeValue.fromS("1")));
  }

  /**
   * Golden-JSON shape test: pins the exact JSON the v1 addItem operation writes to process
   * variables today, so the AWS SDK v2 migration must reproduce it unchanged (migration contract
   * for #7973).
   */
  @Test
  public void putItemOutcome_serializesToDocumentedV1JsonShape() throws Exception {
    // Given a realistic PutItem response. AddItemOperation never sets ReturnValues or
    // ReturnConsumedCapacity, so a live call returns no attributes/consumedCapacity -- but the
    // SDK always attaches request-id and HTTP metadata to every successful call.
    PutItemResponse.Builder responseBuilder = PutItemResponse.builder();
    responseBuilder.responseMetadata(buildSdkResponseMetadata("929bf054-193b-48e6-req"));
    responseBuilder.sdkHttpResponse(buildSdkHttpResponse(200));
    PutItemResponse response = responseBuilder.build();
    AddItem realAddItem = new AddItem(TestDynamoDBData.ActualValue.TABLE_NAME, Map.of("id", "1"));
    when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);
    AddItemOperation addItemOperation = new AddItemOperation(realAddItem);

    // When the operation is invoked exactly as the connector does
    Object result = addItemOperation.invoke(dynamoDbClient);

    // Then the JSON matches the documented v1 output shape exactly, including explicit nulls.
    // Note: no ReturnValues means the response carries no attributes, so item/attributes are
    // null here -- not {} (see the populated-attributes handling in AddItemResult).
    JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(result));
    String expectedJson =
        """
        {
          "item": null,
          "putItemResult": {
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
