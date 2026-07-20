/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.dynamodb.BaseDynamoDbOperationTest;
import io.camunda.connector.aws.dynamodb.TestDynamoDBData;
import io.camunda.connector.aws.dynamodb.model.AddItem;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class AddItemOperationTest extends BaseDynamoDbOperationTest {
  @Mock private AddItem addItemModel;
  @Mock private Item item;
  @Mock private PutItemOutcome putItemOutcome;

  @BeforeEach
  public void setUp() {
    when(addItemModel.tableName()).thenReturn(TestDynamoDBData.ActualValue.TABLE_NAME);
    when(addItemModel.item()).thenReturn(item);
    when(dynamoDB.getTable(addItemModel.tableName())).thenReturn(table);
    when(table.putItem(any(Item.class))).thenReturn(putItemOutcome);
  }

  @Test
  public void testInvoke() throws JsonProcessingException {
    AddItemOperation addItemOperation = new AddItemOperation(addItemModel);
    PutItemOutcome result = addItemOperation.invoke(dynamoDB);
    verify(table).putItem(any(Item.class));
    assertThat(result).isEqualTo(putItemOutcome);
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
    PutItemResult putItemResult = new PutItemResult();
    putItemResult.setSdkResponseMetadata(buildSdkResponseMetadata("929bf054-193b-48e6-req"));
    putItemResult.setSdkHttpMetadata(buildSdkHttpMetadata(200));
    PutItemOutcome realOutcome = new PutItemOutcome(putItemResult);

    AddItem realAddItem = new AddItem(TestDynamoDBData.ActualValue.TABLE_NAME, Map.of("id", "1"));
    when(table.putItem(any(Item.class))).thenReturn(realOutcome);
    AddItemOperation addItemOperation = new AddItemOperation(realAddItem);

    // When the operation is invoked exactly as the connector does
    Object result = addItemOperation.invoke(dynamoDB);

    // Then the JSON matches the documented v1 output shape exactly, including explicit nulls.
    // Note: no ReturnValues means the response carries no attributes, so PutItemOutcome#getItem()
    // (which wraps PutItemResult#getAttributes()) returns null here -- not {} (see the
    // populated-attributes case pinned by DeleteItemOperationTest/UpdateItemOperationTest).
    // Built via readTree(writeValueAsString(...)), not valueToTree(): valueToTree() constructs
    // its tree through JsonNodeFactory#numberNode(BigDecimal), which silently strips trailing
    // zeroes off BigDecimal values -- an artifact of tree-building, not of the actual wire format
    // the runtime writes to process variables. Round-tripping through the real serialized string
    // avoids that divergence (see ScanTableOperationTest / GetItemOperationTest for a fixture
    // where it would otherwise turn 30 into 3E+1).
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

    // Deliberately no exact writeValueAsString() pin here (unlike e.g. the EventBridge golden
    // test this pattern is modeled on): AWS SDK v1's model classes (PutItemResult, AttributeValue,
    // ...) are plain, unannotated JavaBeans with no @JsonPropertyOrder, and Jackson's
    // reflection-based property order for them was empirically observed to change between
    // separate JVM invocations of this exact test on this exact SDK version (both the top-level
    // PutItemOutcome's 2 properties, and nested AttributeValue's 10 properties, reorder freely
    // run to run). Tree equality above -- which compares JSON objects key-by-key regardless of
    // order -- is therefore the only reliable way to pin this shape without flaking CI.
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
