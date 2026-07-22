/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e;

import static io.camunda.process.test.api.CamundaAssert.setAssertionTimeout;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.File;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * End-to-end baseline coverage for all 8 operations of the AWS SDK v2 AWS DynamoDB connector,
 * exercised through the committed element template and a deployed BPMN model against a LocalStack
 * DynamoDB instance. Assertions target the actual JSON content of the connector's result variable.
 */
@SlowTest
public class AwsDynamoDbTest extends BaseAwsTest {

  private static final String ELEMENT_TEMPLATE_PATH =
      "../../../connectors/aws/aws-dynamodb/element-templates/aws-dynamodb-outbound-connector.json";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static DynamoDbClient dynamoDb;

  private String tableName;

  @BeforeAll
  public static void initClient() {
    dynamoDb = AwsTestHelper.initDynamoDbClient(localstack);
    // Some operations (e.g. table creation/deletion) can take a while to become visible through
    // the process test assertions' search index, so allow more headroom than the library default.
    setAssertionTimeout(Duration.ofSeconds(30));
  }

  @AfterEach
  public void cleanup() {
    if (tableName != null) {
      try {
        dynamoDb.deleteTable(DeleteTableRequest.builder().tableName(tableName).build());
        dynamoDb
            .waiter()
            .waitUntilTableNotExists(DescribeTableRequest.builder().tableName(tableName).build());
      } catch (ResourceNotFoundException e) {
        // table already gone (e.g. deleteTable was the operation under test) - nothing to do
      }
    }
  }

  private void createRawTable(String name) {
    dynamoDb.createTable(
        CreateTableRequest.builder()
            .tableName(name)
            .keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
            .attributeDefinitions(
                AttributeDefinition.builder()
                    .attributeName("id")
                    .attributeType(ScalarAttributeType.S)
                    .build())
            .provisionedThroughput(
                ProvisionedThroughput.builder()
                    .readCapacityUnits(5L)
                    .writeCapacityUnits(5L)
                    .build())
            .build());
    dynamoDb.waiter().waitUntilTableExists(DescribeTableRequest.builder().tableName(name).build());
  }

  private void putRawItem(String name, String id, String attributeName, String attributeValue) {
    dynamoDb.putItem(
        PutItemRequest.builder()
            .tableName(name)
            .item(
                Map.of(
                    "id",
                    AttributeValue.fromS(id),
                    attributeName,
                    AttributeValue.fromS(attributeValue)))
            .build());
  }

  private boolean tableExists(String name) {
    try {
      dynamoDb.describeTable(DescribeTableRequest.builder().tableName(name).build());
      return true;
    } catch (ResourceNotFoundException e) {
      return false;
    }
  }

  private ElementTemplate baseElementTemplate() {
    return ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
        .property("authentication.type", "credentials")
        .property("authentication.accessKey", localstack.getAccessKey())
        .property("authentication.secretKey", localstack.getSecretKey())
        .property("configuration.region", localstack.getRegion())
        .property("configuration.endpoint", localstack.getEndpoint().toString())
        .property("retryBackoff", "PT0S")
        .property("retryCount", "0")
        .property("resultVariable", "result");
  }

  /**
   * Deploys a single-service-task process using the given (already customized) element template,
   * runs it to completion and hands the deserialized "result" variable to the given assertions.
   *
   * <p>The "result" variable is fetched directly through the variable search API (rather than
   * {@code CamundaAssert.hasVariableSatisfies}) and polled with Awaitility: some operations (e.g.
   * {@code getItem}) produce a top-level JSON *array* as their result, and the process test
   * assertion library's variable-by-name lookup does not reliably resolve those - fetching and
   * parsing the variable value ourselves sidesteps that limitation.
   */
  private void runProcessAndAssertResult(
      ElementTemplate elementTemplate, String elementId, Consumer<JsonNode> resultAssertions) {
    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask(elementId).endEvent().done();

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, elementId + ".bpmn"))
            .apply(
                elementTemplate.writeTo(new File(tempDir, elementId + "-template.json")),
                elementId,
                new File(tempDir, elementId + "-result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    JsonNode result =
        fetchResultVariable(bpmnTest.getProcessInstanceEvent().getProcessInstanceKey());
    resultAssertions.accept(result);
  }

  private JsonNode fetchResultVariable(long processInstanceKey) {
    return Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(300))
        .until(
            () -> {
              var variables =
                  camundaClient
                      .newVariableSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey).name("result"))
                      .send()
                      .join()
                      .items();
              if (variables.isEmpty()) {
                return null;
              }
              try {
                return OBJECT_MAPPER.readTree(variables.get(0).getValue());
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            },
            Objects::nonNull);
  }

  @Test
  public void testCreateTableOperation() {
    tableName = "e2e-create-table";

    var elementTemplate =
        baseElementTemplate()
            .property("input.operationGroup", "tableOperation")
            .property("input.tableOperation", "createTable")
            .property("input.createTable.tableName", tableName)
            .property("input.partitionKey", "id")
            .property("input.partitionKeyRole", "HASH")
            .property("input.partitionKeyType", "S")
            .property("input.readCapacityUnits", "5")
            .property("input.writeCapacityUnits", "5")
            .property("input.billingModeStr", "PROVISIONED")
            .property("input.deletionProtection", "false");

    runProcessAndAssertResult(
        elementTemplate,
        "create-table",
        result -> {
          assertEquals(tableName, result.path("tableName").asText());
          assertEquals("ACTIVE", result.path("tableStatus").asText());
          assertEquals(1, result.path("keySchema").size());
          assertEquals("id", result.path("keySchema").get(0).path("attributeName").asText());
          assertEquals("HASH", result.path("keySchema").get(0).path("keyType").asText());
          assertEquals(1, result.path("attributeDefinitions").size());
          assertEquals(
              "id", result.path("attributeDefinitions").get(0).path("attributeName").asText());
          assertEquals(
              "S", result.path("attributeDefinitions").get(0).path("attributeType").asText());
          assertEquals(5, result.path("provisionedThroughput").path("readCapacityUnits").asInt());
          assertEquals(5, result.path("provisionedThroughput").path("writeCapacityUnits").asInt());
        });

    assertTrue(tableExists(tableName), "The table should have been created in DynamoDB");
  }

  @Test
  public void testDescribeTableOperation() {
    tableName = "e2e-describe-table";
    createRawTable(tableName);

    var elementTemplate =
        baseElementTemplate()
            .property("input.operationGroup", "tableOperation")
            .property("input.tableOperation", "describeTable")
            .property("input.describeTable.tableName", tableName);

    runProcessAndAssertResult(
        elementTemplate,
        "describe-table",
        result -> {
          assertEquals(tableName, result.path("tableName").asText());
          assertEquals("ACTIVE", result.path("tableStatus").asText());
          assertEquals(1, result.path("keySchema").size());
          assertEquals("id", result.path("keySchema").get(0).path("attributeName").asText());
        });
  }

  @Test
  public void testDeleteTableOperation() {
    tableName = "e2e-delete-table";
    createRawTable(tableName);

    var elementTemplate =
        baseElementTemplate()
            .property("input.operationGroup", "tableOperation")
            .property("input.tableOperation", "deleteTable")
            .property("input.deleteTable.tableName", tableName);

    runProcessAndAssertResult(
        elementTemplate,
        "delete-table",
        result -> {
          assertEquals("delete Table [" + tableName + "]", result.path("action").asText());
          assertEquals("OK", result.path("status").asText());
        });

    assertFalse(tableExists(tableName), "The table should no longer exist after deleteTable");
  }

  @Test
  public void testScanTableOperation() {
    tableName = "e2e-scan-table";
    createRawTable(tableName);
    putRawItem(tableName, "item-1", "color", "blue");
    putRawItem(tableName, "item-2", "color", "red");

    var elementTemplate =
        baseElementTemplate()
            .property("input.operationGroup", "tableOperation")
            .property("input.tableOperation", "scanTable")
            .property("input.scanTable.tableName", tableName);

    runProcessAndAssertResult(
        elementTemplate,
        "scan-table",
        result -> {
          assertEquals("scanTable", result.path("action").asText());
          assertEquals("OK", result.path("status").asText());
          assertEquals(2, result.path("response").size());
          Set<String> colors = new HashSet<>();
          result.path("response").forEach(item -> colors.add(item.path("color").asText()));
          assertEquals(Set.of("blue", "red"), colors);
        });
  }

  @Test
  public void testAddItemOperation() {
    tableName = "e2e-add-item-table";
    createRawTable(tableName);

    var elementTemplate =
        baseElementTemplate()
            .property("input.operationGroup", "itemOperation")
            .property("input.itemOperation", "addItem")
            .property("input.addItem.tableName", tableName)
            .property("input.item", "={\"id\": \"item-1\", \"color\": \"green\"}");

    runProcessAndAssertResult(
        elementTemplate,
        "add-item",
        result -> {
          // AddItemOperation returns the connector-owned AddItemResult envelope: {"item": null,
          // "putItemResult": {...}} - "item" is null because PutItem was not asked to return
          // attributes (no ReturnValues requested).
          assertTrue(result.path("item").isNull());
          JsonNode putItemResult = result.path("putItemResult");
          assertEquals(200, putItemResult.path("sdkHttpMetadata").path("httpStatusCode").asInt());
          assertFalse(
              putItemResult.path("sdkResponseMetadata").path("requestId").asText().isBlank());
          assertTrue(putItemResult.path("attributes").isNull());
        });

    GetItemResponse storedItem =
        dynamoDb.getItem(
            GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.fromS("item-1")))
                .build());
    assertTrue(storedItem.hasItem(), "The item should have been written to DynamoDB");
    assertEquals("green", storedItem.item().get("color").s());
  }

  @Test
  public void testGetItemOperation() {
    tableName = "e2e-get-item-table";
    createRawTable(tableName);
    putRawItem(tableName, "item-1", "color", "yellow");

    var elementTemplate =
        baseElementTemplate()
            .property("input.operationGroup", "itemOperation")
            .property("input.itemOperation", "getItem")
            .property("input.getItem.tableName", tableName)
            .property("input.getItem.primaryKeyComponents", "={\"id\": \"item-1\"}");

    runProcessAndAssertResult(
        elementTemplate,
        "get-item",
        result -> {
          // getItem returns an array of single-key objects, one per attribute, NOT one flat JSON
          // object - see GetItemOperation/AttributeValueConverter#toSingleKeyEntries.
          assertTrue(
              result.isArray(), "getItem result should serialize as a JSON array: " + result);
          assertEquals(2, result.size());
          java.util.Map<String, String> merged = new java.util.HashMap<>();
          result.forEach(
              entry -> {
                assertTrue(
                    entry.isObject(), "Each getItem entry should be a JSON object: " + entry);
                assertEquals(
                    1, entry.size(), "Each getItem entry should contain exactly one field");
                entry.fields().forEachRemaining(e -> merged.put(e.getKey(), e.getValue().asText()));
              });
          assertEquals(java.util.Map.of("id", "item-1", "color", "yellow"), merged);
        });
  }

  @Test
  public void testUpdateItemOperation() {
    tableName = "e2e-update-item-table";
    createRawTable(tableName);
    putRawItem(tableName, "item-1", "color", "black");

    var elementTemplate =
        baseElementTemplate()
            .property("input.operationGroup", "itemOperation")
            .property("input.itemOperation", "updateItem")
            .property("input.updateTable.tableName", tableName)
            .property("input.updateItem.primaryKeyComponents", "={\"id\": \"item-1\"}")
            .property("input.keyAttributes", "={\"color\": \"white\"}")
            .property("input.attributeAction", "put");

    runProcessAndAssertResult(
        elementTemplate,
        "update-item",
        result -> {
          // UpdateItemOperation returns the connector-owned UpdateItemResult envelope:
          // {"updateItemResult": {...}, "item": null} - "item" is null because UpdateItem was not
          // asked to return attributes (no ReturnValues requested).
          assertTrue(result.path("item").isNull());
          JsonNode updateItemResult = result.path("updateItemResult");
          assertEquals(
              200, updateItemResult.path("sdkHttpMetadata").path("httpStatusCode").asInt());
          assertFalse(
              updateItemResult.path("sdkResponseMetadata").path("requestId").asText().isBlank());
          assertTrue(updateItemResult.path("attributes").isNull());
        });

    GetItemResponse updatedItem =
        dynamoDb.getItem(
            GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.fromS("item-1")))
                .build());
    assertTrue(updatedItem.hasItem());
    assertEquals("white", updatedItem.item().get("color").s());
  }

  @Test
  public void testDeleteItemOperation() {
    tableName = "e2e-delete-item-table";
    createRawTable(tableName);
    putRawItem(tableName, "item-1", "color", "purple");

    var elementTemplate =
        baseElementTemplate()
            .property("input.operationGroup", "itemOperation")
            .property("input.itemOperation", "deleteItem")
            .property("input.deleteItem.tableName", tableName)
            .property("input.deleteItem.primaryKeyComponents", "={\"id\": \"item-1\"}");

    runProcessAndAssertResult(
        elementTemplate,
        "delete-item",
        result -> {
          // DeleteItemOperation returns the connector-owned DeleteItemResult envelope:
          // {"deleteItemResult": {...}, "item": null} - "item" is null because DeleteItem was not
          // asked to return attributes (no ReturnValues requested).
          assertTrue(result.path("item").isNull());
          JsonNode deleteItemResult = result.path("deleteItemResult");
          assertEquals(
              200, deleteItemResult.path("sdkHttpMetadata").path("httpStatusCode").asInt());
          assertFalse(
              deleteItemResult.path("sdkResponseMetadata").path("requestId").asText().isBlank());
          assertTrue(deleteItemResult.path("attributes").isNull());
        });

    GetItemResponse deletedItem =
        dynamoDb.getItem(
            GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.fromS("item-1")))
                .build());
    assertFalse(deletedItem.hasItem(), "The item should have been removed from DynamoDB");
  }
}
