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
package io.camunda.connector.http.rest.templates;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.http.rest.templates.TemplateInputMappings.Mode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The UiPath and Blue Prism templates build the request body (and Blue Prism the item URL) in a
 * hidden expression that reads fields the user may leave empty. If such a field is {@code
 * optional}, the Modeler writes no input mapping for it, and the expression reads a process
 * variable with the same name instead. Without {@code optional}, the Modeler writes a mapping
 * without a source, which Zeebe evaluates to null. Each case runs in both of Zeebe's input mapping
 * modes.
 */
class EmptyOptionalTemplateFieldsTest {

  private static final String BLUE_PRISM =
      "../../blue-prism/element-templates/blue-prism-connector.json";
  private static final String UIPATH = "../../uipath/element-templates/uipath-connector.json";

  @ParameterizedTest
  @EnumSource(Mode.class)
  @SuppressWarnings("unchecked")
  void bluePrismCreateItemSendsNullForEmptyOptionalFields(Mode mode) throws Exception {
    Map<String, String> inputs = bluePrismCreateItemInputs();
    Map<String, Object> processVariables =
        Map.of("status", "APPROVED", "deferDate", "2026-12-31", "priority", 1);

    var locals = TemplateInputMappings.load(BLUE_PRISM, mode).apply(inputs, processVariables);

    var item = ((List<Map<String, Object>>) locals.get("body")).getFirst();
    assertThat(item).containsEntry("status", null).containsEntry("deferredDate", null);
    assertThat(item.get("priority")).isEqualTo("3");
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  @SuppressWarnings("unchecked")
  void bluePrismCreateItemSendsNullForAClearedPriority(Mode mode) throws Exception {
    Map<String, String> inputs = bluePrismCreateItemInputs();
    inputs.put("priority", "");
    Map<String, Object> processVariables = Map.of("priority", 1);

    var locals = TemplateInputMappings.load(BLUE_PRISM, mode).apply(inputs, processVariables);

    var item = ((List<Map<String, Object>>) locals.get("body")).getFirst();
    assertThat(item).containsEntry("priority", null);
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  @SuppressWarnings("unchecked")
  void bluePrismCreateItemSendsNullDataForAnEmptyItemValue(Mode mode) throws Exception {
    Map<String, String> inputs = bluePrismCreateItemInputs();
    inputs.remove("bluePrismDataItemValue");
    Map<String, Object> processVariables = Map.of("bluePrismDataItemValue", "from the process");

    var locals = TemplateInputMappings.load(BLUE_PRISM, mode).apply(inputs, processVariables);

    var item = ((List<Map<String, Object>>) locals.get("body")).getFirst();
    assertThat(item).containsEntry("data", null);
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void bluePrismGetItemDoesNotTakeAnEmptyItemIdFromTheProcess(Mode mode) throws Exception {
    Map<String, String> inputs = bluePrismCreateItemInputs();
    inputs.put("operationType", "getItemFromQueueById");
    inputs.remove("bluePrismDataItemValue");
    Map<String, Object> processVariables =
        Map.of("bluePrismItemId", "00000000-0000-0000-0000-000000000099");

    var locals = TemplateInputMappings.load(BLUE_PRISM, mode).apply(inputs, processVariables);

    assertThat(locals).containsEntry("url", null);
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  @SuppressWarnings("unchecked")
  void uiPathAddQueueItemSendsNullForEmptyOptionalFields(Mode mode) throws Exception {
    Map<String, String> inputs = uiPathAddQueueItemInputs();
    Map<String, Object> processVariables =
        Map.of(
            "dueDate", "2026-12-31",
            "deferDate", "2026-12-01",
            "reference", "INV-2026-0001",
            "specificContent", Map.of("invoiceNumber", "INV-2026-0001"));

    var locals = TemplateInputMappings.load(UIPATH, mode).apply(inputs, processVariables);

    var itemData = (Map<String, Object>) ((Map<String, Object>) locals.get("body")).get("itemData");
    assertThat(itemData)
        .containsEntry("Name", "synthetic-invoices")
        .containsEntry("Priority", "Low")
        .containsEntry("DueDate", null)
        .containsEntry("DeferDate", null)
        .containsEntry("Reference", null)
        .containsEntry("SpecificContent", null);
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  @SuppressWarnings("unchecked")
  void bluePrismCreateItemSendsTheEnteredOptionalFields(Mode mode) throws Exception {
    Map<String, String> inputs = bluePrismCreateItemInputs();
    inputs.put("deferDate", "2026-12-31");
    inputs.put("priority", "=invoicePriority");
    inputs.put("status", "Ready");
    Map<String, Object> processVariables = Map.of("status", "APPROVED", "invoicePriority", 1);

    var locals = TemplateInputMappings.load(BLUE_PRISM, mode).apply(inputs, processVariables);

    var item = ((List<Map<String, Object>>) locals.get("body")).getFirst();
    assertThat(item)
        .containsEntry("status", "Ready")
        .containsEntry("deferredDate", "2026-12-31")
        .containsEntry("priority", 1L);
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  @SuppressWarnings("unchecked")
  void uiPathAddQueueItemSendsTheEnteredOptionalFields(Mode mode) throws Exception {
    Map<String, String> inputs = uiPathAddQueueItemInputs();
    inputs.put("deferDate", "2026-12-01");
    inputs.put("dueDate", "2026-12-31");
    inputs.put("specificContent", "={invoiceNumber: invoice.number}");
    inputs.put("reference", "=invoice.number");
    Map<String, Object> processVariables =
        Map.of("invoice", Map.of("number", "INV-2026-0002"), "reference", "OTHER");

    var locals = TemplateInputMappings.load(UIPATH, mode).apply(inputs, processVariables);

    var itemData = (Map<String, Object>) ((Map<String, Object>) locals.get("body")).get("itemData");
    assertThat(itemData)
        .containsEntry("DeferDate", "2026-12-01")
        .containsEntry("DueDate", "2026-12-31")
        .containsEntry("Reference", "INV-2026-0002")
        .containsEntry("SpecificContent", Map.of("invoiceNumber", "INV-2026-0002"));
  }

  private static Map<String, String> bluePrismCreateItemInputs() {
    Map<String, String> inputs = new HashMap<>();
    inputs.put("operationType", "createItemInQueue");
    inputs.put("authentication.type", "bearer");
    inputs.put("authentication.token", "synthetic-token");
    inputs.put("bluePrismBaseUrl", "https://blueprism.example.test:5678");
    inputs.put("bluePrismQueueId", "00000000-0000-0000-0000-000000000001");
    inputs.put("bluePrismDataItemValue", "synthetic value");
    return inputs;
  }

  private static Map<String, String> uiPathAddQueueItemInputs() {
    Map<String, String> inputs = new HashMap<>();
    inputs.put("operationType", "addQueueItem");
    inputs.put("authentication.type", "bearer");
    inputs.put("authentication.token", "synthetic-token");
    inputs.put("cloudOrg", "fictional-org");
    inputs.put("cloudTenant", "DefaultTenant");
    inputs.put("organizationUnitId", "1001");
    inputs.put("queueName", "synthetic-invoices");
    return inputs;
  }
}
