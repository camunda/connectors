/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

/**
 * Request-routing contract for the AWS DynamoDB connector: covers both JSON discriminator styles
 * the {@link io.camunda.connector.aws.dynamodb.model.AwsInputDeserializer} must keep supporting
 * (the legacy, pre-v7 generic "type" field, and the current "tableOperation"/"itemOperation" pair),
 * plus the deprecated {@code io.camunda:aws:1} connector type class, which had zero test coverage
 * before this PR. Each test also asserts the documented v1 golden-JSON result shape, so this is
 * part of the migration contract for #7973.
 */
class AwsDynamoDbServiceConnectorFunctionTest extends BaseDynamoDbOperationTest {

  private static final String ACCESS_KEY = "AKIATESTACCESSKEY";
  private static final String SECRET_KEY = "test-secret-key";
  private static final String REGION = "us-east-1";

  @Mock private DynamoDbClientSupplier dynamoDbClientSupplier;

  private static OutboundConnectorContext contextFor(String inputJson) {
    String requestJson =
        """
        {
          "authentication": { "accessKey": "%s", "secretKey": "%s" },
          "configuration": { "region": "%s" },
          "input": %s
        }
        """
            .formatted(ACCESS_KEY, SECRET_KEY, REGION, inputJson);
    return OutboundConnectorContextBuilder.create().variables(requestJson).build();
  }

  /**
   * Legacy discriminator (pre-v7 payloads, a single generic "type" field) routed through the
   * current, non-deprecated connector type ({@code io.camunda:aws-dynamodb:1}). Also pins the
   * addItem golden JSON shape end-to-end (request parsing -> operation -> production mapper).
   */
  @Test
  void execute_routesLegacyTypeDiscriminator_andProducesDocumentedV1JsonShape() throws Exception {
    PutItemResponse.Builder responseBuilder = PutItemResponse.builder();
    responseBuilder.responseMetadata(buildSdkResponseMetadata("legacy-type-request-id"));
    responseBuilder.sdkHttpResponse(buildSdkHttpResponse(200));
    PutItemResponse response = responseBuilder.build();
    when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);
    when(dynamoDbClientSupplier.dynamoDbClient(any(AwsDynamoDbRequest.class)))
        .thenReturn(dynamoDbClient);

    OutboundConnectorContext context =
        contextFor(
            """
            {
              "type": "addItem",
              "tableName": "%s",
              "item": { "id": "1" }
            }
            """
                .formatted(TestDynamoDBData.ActualValue.TABLE_NAME));

    Object result =
        new AwsDynamoDbServiceConnectorFunction(dynamoDbClientSupplier).execute(context);

    JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(result));
    String expectedJson =
        """
        {
          "item": null,
          "putItemResult": {
            "sdkResponseMetadata": { "requestId": "legacy-type-request-id" },
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
    assertThat(actual).isEqualTo(objectMapper.readTree(expectedJson));
  }

  /**
   * Current discriminator style (the "tableOperation"/"itemOperation" pair introduced with template
   * generation, version 7 onwards) routed through the current connector type. Also pins the
   * describeTable/TableDescription golden JSON shape end-to-end.
   */
  @Test
  void execute_routesCurrentTableOperationDiscriminator_andProducesDocumentedV1JsonShape()
      throws Exception {
    TableDescription realDescription =
        buildRealisticTableDescription(TestDynamoDBData.ActualValue.TABLE_NAME);
    when(dynamoDbClient.describeTable(any(DescribeTableRequest.class)))
        .thenReturn(DescribeTableResponse.builder().table(realDescription).build());
    when(dynamoDbClientSupplier.dynamoDbClient(any(AwsDynamoDbRequest.class)))
        .thenReturn(dynamoDbClient);

    OutboundConnectorContext context =
        contextFor(
            """
            {
              "tableOperation": "describeTable",
              "tableName": "%s"
            }
            """
                .formatted(TestDynamoDBData.ActualValue.TABLE_NAME));

    Object result =
        new AwsDynamoDbServiceConnectorFunction(dynamoDbClientSupplier).execute(context);

    JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(result));
    String expectedJson =
        """
        {
          "attributeDefinitions": [
            { "attributeName": "ID", "attributeType": "N" },
            { "attributeName": "sortKey", "attributeType": "S" }
          ],
          "tableName": "my_table",
          "keySchema": [
            { "attributeName": "ID", "keyType": "HASH" },
            { "attributeName": "sortKey", "keyType": "RANGE" }
          ],
          "tableStatus": "ACTIVE",
          "creationDateTime": "2023-11-14T22:13:20.000+00:00",
          "provisionedThroughput": {
            "lastIncreaseDateTime": null,
            "lastDecreaseDateTime": null,
            "numberOfDecreasesToday": null,
            "readCapacityUnits": 4,
            "writeCapacityUnits": 5
          },
          "tableSizeBytes": 0,
          "itemCount": 0,
          "tableArn": "arn:aws:dynamodb:us-east-1:123456789012:table/my_table",
          "tableId": null,
          "billingModeSummary": {
            "billingMode": "PROVISIONED",
            "lastUpdateToPayPerRequestDateTime": null
          },
          "localSecondaryIndexes": null,
          "globalSecondaryIndexes": null,
          "streamSpecification": null,
          "latestStreamLabel": null,
          "latestStreamArn": null,
          "globalTableVersion": null,
          "replicas": null,
          "restoreSummary": null,
          "archivalSummary": null,
          "tableClassSummary": null,
          "deletionProtectionEnabled": null,
          "onDemandThroughput": null,
          "ssedescription": null,
          "warmThroughput": null
        }
        """;
    assertThat(actual).isEqualTo(objectMapper.readTree(expectedJson));
  }

  /**
   * The deprecated legacy connector type class ({@code io.camunda:aws:1}, superseded by {@code
   * io.camunda:aws-dynamodb:1}) had zero test coverage before this PR. It must keep delegating to
   * {@link AwsDynamoDbServiceConnectorFunction} unchanged, current-discriminator payloads included,
   * and produce the same golden getItem JSON shape (the "array of single-key objects" quirk).
   */
  @Test
  void executeDeprecated_delegatesAndProducesDocumentedV1JsonShape() throws Exception {
    Map<String, AttributeValue> itemAttributes = new LinkedHashMap<>();
    itemAttributes.put("id", AttributeValue.fromS("1"));
    itemAttributes.put("name", AttributeValue.fromS("Alice"));
    when(dynamoDbClient.getItem(any(GetItemRequest.class)))
        .thenReturn(GetItemResponse.builder().item(itemAttributes).build());
    when(dynamoDbClientSupplier.dynamoDbClient(any(AwsDynamoDbRequest.class)))
        .thenReturn(dynamoDbClient);

    OutboundConnectorContext context =
        contextFor(
            """
            {
              "itemOperation": "getItem",
              "tableName": "%s",
              "primaryKeyComponents": { "id": "1" }
            }
            """
                .formatted(TestDynamoDBData.ActualValue.TABLE_NAME));

    var delegate = new AwsDynamoDbServiceConnectorFunction(dynamoDbClientSupplier);
    Object result = new AwsDynamoDbServiceConnectorFunctionDeprecated(delegate).execute(context);

    JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(result));
    String expectedJson =
        """
        [ { "id": "1" }, { "name": "Alice" } ]
        """;
    assertThat(actual).isEqualTo(objectMapper.readTree(expectedJson));
  }
}
