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
package io.camunda.connector.e2e.csv;

import static io.camunda.process.test.api.CamundaAssert.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.File;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@SlowTest
@ExtendWith(MockitoExtension.class)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class CsvConnectorTests {

  private static final String ELEMENT_TEMPLATE_PATH =
      "../../connectors/csv/element-templates/csv-outbound-connector.json";

  @TempDir File tempDir;
  @Autowired CamundaClient camundaClient;

  @Test
  void readCsvFromString() {
    // Test reading CSV from a string input
    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask("csvTask").endEvent().done();

    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("operation", "readCsv")
            .property("readCsv:data", "name,age,city\nAlice,30,Paris\nBob,25,London")
            .property("readCsv:format.delimiter", ",")
            .property("readCsv:format.skipHeaderRecord", "true")
            .property("readCsv:format.headers", "=[\"name\", \"age\", \"city\"]")
            .property("readCsv:rowType", "Object")
            .property("resultExpression", "={recordCount: count(records)}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "csvTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("recordCount", 2);
  }

  @Test
  void readCsvWithSemicolonDelimiter() {
    // Test reading CSV with semicolon delimiter
    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask("csvTask").endEvent().done();

    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("operation", "readCsv")
            .property("readCsv:data", "name;age;city\nAlice;30;Paris\nBob;25;London")
            .property("readCsv:format.delimiter", ";")
            .property("readCsv:format.skipHeaderRecord", "true")
            .property("readCsv:format.headers", "=[\"name\", \"age\", \"city\"]")
            .property("readCsv:rowType", "Object")
            .property("resultExpression", "={recordCount: count(records)}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "csvTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("recordCount", 2);
  }

  @Test
  void writeCsvFromData() {
    // Test writing CSV from data
    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask("csvTask").endEvent().done();

    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("operation", "writeCsv")
            .property(
                "writeCsv:data",
                "=[{\"name\": \"Alice\", \"age\": 30}, {\"name\": \"Bob\", \"age\": 25}]")
            .property("writeCsv:format.delimiter", ",")
            .property("writeCsv:format.headers", "=[\"name\", \"age\"]")
            .property("writeCsv:createDocument", "false")
            .property("resultVariable", "csvResult")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "csvTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    // The result should contain the CSV output with the data
    assertThat(bpmnTest.getProcessInstanceEvent())
        .hasVariable("csvResult", Map.of("content", "Alice,30\r\nBob,25\r\n"));
  }

  @Test
  void readCsvWithResultExpression() {
    // Test reading CSV and mapping results with result expression
    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask("csvTask").endEvent().done();

    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("operation", "readCsv")
            .property(
                "readCsv:data", "name,age,city\nAlice,30,Paris\nBob,25,London\nCharlie,35,Berlin")
            .property("readCsv:format.delimiter", ",")
            .property("readCsv:format.skipHeaderRecord", "true")
            .property("readCsv:format.headers", "=[\"name\", \"age\", \"city\"]")
            .property("readCsv:rowType", "Object")
            .property(
                "resultExpression", "={recordCount: count(records), firstPerson: records[1].name}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "csvTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent())
        .hasVariable("recordCount", 3)
        .hasVariable("firstPerson", "Alice");
  }

  @Test
  void readCsvWithRecordMapping() {
    // Test reading CSV with record mapping to transform each record
    // Based on docs: extract only specific fields and convert price to number
    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask("csvTask").endEvent().done();

    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("operation", "readCsv")
            .property(
                "readCsv:data",
                "product,price,quantity\nWireless Mouse,29.99,10\nOffice Chair,149.5,5\nUSB Cable,12.99,50")
            .property("readCsv:format.delimiter", ",")
            .property("readCsv:format.skipHeaderRecord", "true")
            .property("readCsv:format.headers", "=[\"product\", \"price\", \"quantity\"]")
            .property("readCsv:rowType", "Object")
            // Record mapping: extract only product and convert price to number
            .property(
                "readCsv:recordMapper", "={product: record.product, price: number(record.price)}")
            .property(
                "resultExpression",
                "={recordCount: count(records), firstProduct: records[1].product, firstPrice: records[1].price}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "csvTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    // Verify all 3 records are present and transformed
    assertThat(bpmnTest.getProcessInstanceEvent())
        .hasVariable("recordCount", 3)
        .hasVariable("firstProduct", "Wireless Mouse")
        .hasVariable("firstPrice", 29.99);
  }

  @Test
  void readCsvWithRecordMappingFilter() {
    // Test reading CSV with record mapping that filters out records by returning null
    // Based on docs: only include products with price >= 30
    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask("csvTask").endEvent().done();

    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("operation", "readCsv")
            .property(
                "readCsv:data",
                "product,price,quantity\nWireless Mouse,29.99,10\nOffice Chair,149.5,5\nUSB Cable,12.99,50\nMonitor Stand,45,8")
            .property("readCsv:format.delimiter", ",")
            .property("readCsv:format.skipHeaderRecord", "true")
            .property("readCsv:format.headers", "=[\"product\", \"price\", \"quantity\"]")
            .property("readCsv:rowType", "Object")
            // Record mapping: filter out records where price < 30 by returning null
            .property(
                "readCsv:recordMapper",
                "=if number(record.price) >= 30 then {product: record.product, price: number(record.price)} else null")
            .property("resultExpression", "={recordCount: count(records), products: records}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "csvTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    // Only Office Chair (149.5) and Monitor Stand (45) should be included
    // Wireless Mouse (29.99) and USB Cable (12.99) are filtered out
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("recordCount", 2);
  }
}
