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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.File;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * End-to-end baseline coverage for all 8 operations of the (v1 SDK) AWS DynamoDB connector,
 * exercised through the committed element template and a deployed BPMN model against a LocalStack
 * DynamoDB instance. Assertions target the actual JSON content of the connector's result variable
 * so that this baseline can be diffed against the future v2 SDK port (#7973).
 */
@SlowTest
public class AwsDynamoDbTest extends BaseAwsTest {

  private static final String ELEMENT_TEMPLATE_PATH =
      "../../../connectors/aws/aws-dynamodb/element-templates/aws-dynamodb-outbound-connector.json";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static DynamoDB dynamoDb;

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
        Table table = dynamoDb.getTable(tableName);
        table.delete();
        table.waitForDelete();
      } catch (ResourceNotFoundException | InterruptedException e) {
        // table already gone (e.g. deleteTable was the operation under test) - nothing to do
      }
    }
  }

  private Table createRawTable(String name) throws InterruptedException {
    Table table =
        dynamoDb.createTable(
            name,
            List.of(new KeySchemaElement("id", "HASH")),
            List.of(new AttributeDefinition("id", "S")),
            new ProvisionedThroughput(5L, 5L));
    table.waitForActive();
    return table;
  }

  private boolean tableExists(String name) {
    try {
      dynamoDb.getTable(name).describe();
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
  public void testDescribeTableOperation() throws InterruptedException {
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
  public void testDeleteTableOperation() throws InterruptedException {
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
  public void testScanTableOperation() throws InterruptedException {
    tableName = "e2e-scan-table";
    Table table = createRawTable(tableName);
    table.putItem(new Item().withPrimaryKey("id", "item-1").withString("color", "blue"));
    table.putItem(new Item().withPrimaryKey("id", "item-2").withString("color", "red"));

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
  public void testAddItemOperation() throws InterruptedException {
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
          // AddItemOperation returns the raw v1 PutItemOutcome bean: {"item": null,
          // "putItemResult": {...}} - "item" is null because PutItem was not asked to return
          // attributes (no ReturnValues requested).
          assertTrue(result.path("item").isNull());
          JsonNode putItemResult = result.path("putItemResult");
          assertEquals(200, putItemResult.path("sdkHttpMetadata").path("httpStatusCode").asInt());
          assertFalse(
              putItemResult.path("sdkResponseMetadata").path("requestId").asText().isBlank());
          assertTrue(putItemResult.path("attributes").isNull());
        });

    Item storedItem = dynamoDb.getTable(tableName).getItem(new PrimaryKey("id", "item-1"));
    assertNotNull(storedItem, "The item should have been written to DynamoDB");
    assertEquals("green", storedItem.getString("color"));
  }

  @Test
  public void testGetItemOperation() throws InterruptedException {
    tableName = "e2e-get-item-table";
    Table table = createRawTable(tableName);
    table.putItem(new Item().withPrimaryKey("id", "item-1").withString("color", "yellow"));

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
          // Today's v1 GetItemOperation returns Item::attributes(), an
          // Iterable<Map.Entry<String,Object>> rather than a plain Map - which serializes as a
          // JSON array of single-key objects, NOT as one flat JSON object. This is the actual
          // production shape and is captured here deliberately (see mission: document reality).
          assertTrue(
              result.isArray(), "getItem result should serialize as a JSON array: " + result);
          assertEquals(2, result.size());
          java.util.Map<String, String> merged = new java.util.HashMap<>();
          result.forEach(
              entry ->
                  entry
                      .fields()
                      .forEachRemaining(e -> merged.put(e.getKey(), e.getValue().asText())));
          assertEquals(java.util.Map.of("id", "item-1", "color", "yellow"), merged);
        });
  }

  @Test
  public void testUpdateItemOperation() throws InterruptedException {
    tableName = "e2e-update-item-table";
    Table table = createRawTable(tableName);
    table.putItem(new Item().withPrimaryKey("id", "item-1").withString("color", "black"));

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
          // UpdateItemOperation returns the raw v1 UpdateItemOutcome bean: {"updateItemResult":
          // {...}, "item": null} - "item" is null because UpdateItem was not asked to return
          // attributes (no ReturnValues requested).
          assertTrue(result.path("item").isNull());
          JsonNode updateItemResult = result.path("updateItemResult");
          assertEquals(
              200, updateItemResult.path("sdkHttpMetadata").path("httpStatusCode").asInt());
          assertFalse(
              updateItemResult.path("sdkResponseMetadata").path("requestId").asText().isBlank());
          assertTrue(updateItemResult.path("attributes").isNull());
        });

    Item updatedItem = dynamoDb.getTable(tableName).getItem(new PrimaryKey("id", "item-1"));
    assertNotNull(updatedItem);
    assertEquals("white", updatedItem.getString("color"));
  }

  @Test
  public void testDeleteItemOperation() throws InterruptedException {
    tableName = "e2e-delete-item-table";
    Table table = createRawTable(tableName);
    table.putItem(new Item().withPrimaryKey("id", "item-1").withString("color", "purple"));

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
          // DeleteItemOperation returns the raw v1 DeleteItemOutcome bean: {"deleteItemResult":
          // {...}, "item": null} - "item" is null because DeleteItem was not asked to return
          // attributes (no ReturnValues requested).
          assertTrue(result.path("item").isNull());
          JsonNode deleteItemResult = result.path("deleteItemResult");
          assertEquals(
              200, deleteItemResult.path("sdkHttpMetadata").path("httpStatusCode").asInt());
          assertFalse(
              deleteItemResult.path("sdkResponseMetadata").path("requestId").asText().isBlank());
          assertTrue(deleteItemResult.path("attributes").isNull());
        });

    Item deletedItem = dynamoDb.getTable(tableName).getItem(new PrimaryKey("id", "item-1"));
    assertNull(deletedItem, "The item should have been removed from DynamoDB");
  }
}
