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
import com.amazonaws.services.dynamodbv2.model.DeleteItemResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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

  /**
   * Golden-JSON shape test: pins the exact JSON the v1 deleteItem operation writes to process
   * variables today, so the AWS SDK v2 migration must reproduce it unchanged (migration contract
   * for #7973). Models the response the production overload actually returns: {@link
   * DeleteItemOperation} calls {@code Table.deleteItem(KeyAttribute...)}, which never sets {@code
   * ReturnValues}, so a live call gets {@code NONE} and comes back with no attributes -- but the
   * SDK always attaches request-id and HTTP metadata to every successful call (see
   * AddItemOperationTest for the same shape).
   */
  @Test
  public void deleteItemOutcome_serializesToDocumentedV1JsonShape() throws Exception {
    // Given a realistic DeleteItem response. DeleteItemOperation never requests ReturnValues, so
    // a live call returns no attributes -- but the SDK always attaches request-id and HTTP
    // metadata to every successful call.
    DeleteItemResult deleteItemResult = new DeleteItemResult();
    deleteItemResult.setSdkResponseMetadata(buildSdkResponseMetadata("929bf054-193b-48e6-req"));
    deleteItemResult.setSdkHttpMetadata(buildSdkHttpMetadata(200));
    DeleteItemOutcome realOutcome = new DeleteItemOutcome(deleteItemResult);

    Map<String, Object> primaryKeyComponents = new HashMap<>();
    primaryKeyComponents.put("id", "1234");
    DeleteItem deleteItem =
        new DeleteItem(TestDynamoDBData.ActualValue.TABLE_NAME, primaryKeyComponents);
    DeleteItemOperation operation = new DeleteItemOperation(deleteItem);
    when(table.deleteItem(new KeyAttribute("id", "1234"))).thenReturn(realOutcome);

    // When
    Object result = operation.invoke(dynamoDB);

    // Then: no ReturnValues means the response carries no attributes, so
    // DeleteItemOutcome#getItem() (which wraps DeleteItemResult#getAttributes()) returns null
    // here, not {} (contrast with the exhaustive raw-AttributeValue shape pinned directly against
    // the model classes in AttributeValueSerializationTest, which this operation can never
    // actually surface since it never sets ReturnValues).
    // Built via readTree(writeValueAsString(...)), not valueToTree(): see AddItemOperationTest
    // for why (valueToTree() strips trailing zeroes off BigDecimal values -- not relevant to
    // *this* fixture's values, but kept consistent with the other golden tests in this module).
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
