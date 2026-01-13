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
package io.camunda.connector.runtime.inbound.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Variable;
import io.camunda.connector.runtime.inbound.EmptyConfiguration;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.ArrayList;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SlowTest
@SpringBootTest(classes = EmptyConfiguration.class)
@CamundaSpringProcessTest
public class SearchQueryClientTest {

  @Autowired private CamundaClient camundaClient;

  @Test
  public void shouldFetchOnlyTheLatestProcessDefinitions() {
    // Given
    createAndDeployProcess("testProcess", 2);
    waitForIndexing(2);

    // When
    SearchQueryClientImpl processDefinitionSearch = new SearchQueryClientImpl(camundaClient, 200);
    var result = processDefinitionSearch.queryProcessDefinitions(null);

    // Then
    assertThat(result.items()).hasSize(1);
    var processDefinition = result.items().getFirst();
    assertThat(processDefinition.getProcessDefinitionId()).isEqualTo("testProcess");
    assertThat(processDefinition.getVersion()).isEqualTo(2);
  }

  @Test
  public void shouldFetchOnlyTheLatestProcessDefinitions_whenPaginated() {
    // Given
    createAndDeployProcess("testProcess", 2);
    createAndDeployProcess("testProcess2", 1);
    waitForIndexing(3);

    // When
    SearchQueryClientImpl processDefinitionSearch = new SearchQueryClientImpl(camundaClient, 1);
    var resultPage1 = processDefinitionSearch.queryProcessDefinitions(null);
    var pageIndex = resultPage1.page().endCursor();
    var allItems = new ArrayList<>(resultPage1.items());
    var resultPage2 =
        processDefinitionSearch.queryProcessDefinitions(pageIndex); // Fetch next page with 1 item
    pageIndex = resultPage2.page().endCursor();
    allItems.addAll(resultPage2.items());
    // this page should be empty
    var resultPage3 =
        processDefinitionSearch.queryProcessDefinitions(pageIndex); // Fetch next page with 1 item
    pageIndex = resultPage3.page().endCursor();

    // Then
    assertThat(pageIndex).isNull();
    assertThat(resultPage3.items()).isEmpty();
    assertThat(allItems).hasSize(2);
    // result should be sorted by processId asc
    var processDefinition = allItems.getFirst();
    assertThat(processDefinition.getProcessDefinitionId()).isEqualTo("testProcess");
    assertThat(processDefinition.getVersion()).isEqualTo(2);
    var processDefinition2 = allItems.get(1);
    assertThat(processDefinition2.getProcessDefinitionId()).isEqualTo("testProcess2");
    assertThat(processDefinition2.getVersion()).isEqualTo(1);
  }

  @Test
  public void shouldFetchMessageSubscriptions() {
    // Given
    BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("messageProcess")
            .startEvent()
            .intermediateCatchEvent("messageEvent")
            .message(m -> m.name("testMessage").zeebeCorrelationKeyExpression("correlationKey"))
            .endEvent()
            .done();
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(modelInstance, "messageProcess.bpmn")
        .send()
        .join();

    // Start a process instance to create a message subscription
    camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId("messageProcess")
        .latestVersion()
        .variables("{\"correlationKey\": \"test-key\"}")
        .send()
        .join();

    waitForMessageSubscription();

    // When
    SearchQueryClientImpl searchClient = new SearchQueryClientImpl(camundaClient, 200);
    var result = searchClient.queryMessageSubscriptions(null);

    // Then
    assertThat(result.items()).isNotEmpty();
    var subscription =
        result.items().stream().filter(s -> s.getMessageName().equals("testMessage")).findFirst();
    assertThat(subscription).isPresent();
    assertThat(subscription.get().getMessageName()).isEqualTo("testMessage");
  }

  @Test
  public void shouldFetchMessageSubscriptions_whenPaginated() {
    // Given - create multiple message subscriptions
    BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("multiMessageProcess")
            .startEvent()
            .intermediateCatchEvent("message1")
            .message(m -> m.name("message1").zeebeCorrelationKeyExpression("key"))
            .intermediateCatchEvent("message2")
            .message(m -> m.name("message2").zeebeCorrelationKeyExpression("key"))
            .endEvent()
            .done();
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(modelInstance, "multiMessageProcess.bpmn")
        .send()
        .join();

    camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId("multiMessageProcess")
        .latestVersion()
        .variables("{\"key\": \"test\"}")
        .send()
        .join();

    waitForMessageSubscription();

    // When - fetch with pagination
    SearchQueryClientImpl searchClient = new SearchQueryClientImpl(camundaClient, 1);
    var resultPage1 = searchClient.queryMessageSubscriptions(null);
    assertThat(resultPage1.items()).isNotEmpty();

    var allItems = new ArrayList<>(resultPage1.items());

    if (resultPage1.page().endCursor() != null) {
      var resultPage2 = searchClient.queryMessageSubscriptions(resultPage1.page().endCursor());
      allItems.addAll(resultPage2.items());
    }

    // Then - verify we fetched at least one subscription
    assertThat(allItems).isNotEmpty();
  }

  @Test
  public void shouldFetchActiveFlowNodes() {
    // Given
    BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("flowNodeProcess")
            .startEvent()
            .serviceTask("task1")
            .zeebeJobType("testJob")
            .endEvent()
            .done();

    var deployment =
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(modelInstance, "flowNodeProcess.bpmn")
            .send()
            .join();

    long processDefinitionKey = deployment.getProcesses().getFirst().getProcessDefinitionKey();

    // Start instance which will wait at the service task
    camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId("flowNodeProcess")
        .latestVersion()
        .send()
        .join();

    waitForActiveElement(processDefinitionKey, "task1");

    // When
    SearchQueryClientImpl searchClient = new SearchQueryClientImpl(camundaClient, 200);
    var result = searchClient.queryActiveFlowNodes(processDefinitionKey, "task1", null);

    // Then
    assertThat(result.items()).isNotEmpty();
    var activeNode = result.items().getFirst();
    assertThat(activeNode.getElementId()).isEqualTo("task1");
  }

  @Test
  public void shouldFetchActiveFlowNodes_whenPaginated() {
    // Given - create multiple active instances
    BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("paginatedFlowProcess")
            .startEvent()
            .serviceTask("waitTask")
            .zeebeJobType("waitJob")
            .endEvent()
            .done();

    var deployment =
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(modelInstance, "paginatedFlowProcess.bpmn")
            .send()
            .join();

    long processDefinitionKey = deployment.getProcesses().getFirst().getProcessDefinitionKey();

    // Start multiple instances
    IntStream.range(0, 3)
        .forEach(
            i ->
                camundaClient
                    .newCreateInstanceCommand()
                    .bpmnProcessId("paginatedFlowProcess")
                    .latestVersion()
                    .send()
                    .join());

    waitForActiveElement(processDefinitionKey, "waitTask");

    // When - fetch with small page size
    SearchQueryClientImpl searchClient = new SearchQueryClientImpl(camundaClient, 2);
    var resultPage1 = searchClient.queryActiveFlowNodes(processDefinitionKey, "waitTask", null);

    // Then
    assertThat(resultPage1.items()).hasSizeLessThanOrEqualTo(2);
    if (resultPage1.page().endCursor() != null) {
      var resultPage2 =
          searchClient.queryActiveFlowNodes(
              processDefinitionKey, "waitTask", resultPage1.page().endCursor());
      assertThat(resultPage2.items()).isNotEmpty();
    }
  }

  @Test
  public void shouldFetchVariables() {
    // Given
    BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("variableProcess")
            .startEvent()
            .serviceTask("task")
            .zeebeJobType("testJob")
            .endEvent()
            .done();

    var deployment =
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(modelInstance, "variableProcess.bpmn")
            .send()
            .join();

    long processDefinitionKey = deployment.getProcesses().getFirst().getProcessDefinitionKey();

    var instance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("variableProcess")
            .latestVersion()
            .variables("{\"var1\": \"value1\", \"var2\": 123}")
            .send()
            .join();

    long processInstanceKey = instance.getProcessInstanceKey();

    waitForActiveElement(processDefinitionKey, "task");

    // Get the element instance key
    SearchQueryClientImpl searchClient = new SearchQueryClientImpl(camundaClient, 200);
    var activeNodes = searchClient.queryActiveFlowNodes(processDefinitionKey, "task", null);
    long elementInstanceKey = activeNodes.items().getFirst().getElementInstanceKey();

    waitForVariables(processInstanceKey);

    // When
    var result = searchClient.queryVariables(processInstanceKey, elementInstanceKey, null);

    // Then
    assertThat(result.items()).hasSizeGreaterThanOrEqualTo(2);
    var varNames = result.items().stream().map(Variable::getName).toList();
    assertThat(varNames).contains("var1", "var2");
  }

  @Test
  public void shouldFetchVariables_whenPaginated() {
    // Given - create process with multiple variables
    BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("multiVarProcess")
            .startEvent()
            .serviceTask("task")
            .zeebeJobType("testJob")
            .endEvent()
            .done();

    var deployment =
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(modelInstance, "multiVarProcess.bpmn")
            .send()
            .join();

    long processDefinitionKey = deployment.getProcesses().getFirst().getProcessDefinitionKey();

    var instance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("multiVarProcess")
            .latestVersion()
            .variables("{\"a\": 1, \"b\": 2, \"c\": 3, \"d\": 4}")
            .send()
            .join();

    long processInstanceKey = instance.getProcessInstanceKey();

    waitForActiveElement(processDefinitionKey, "task");

    SearchQueryClientImpl searchClient = new SearchQueryClientImpl(camundaClient, 200);
    var activeNodes = searchClient.queryActiveFlowNodes(processDefinitionKey, "task", null);
    long elementInstanceKey = activeNodes.items().getFirst().getElementInstanceKey();

    waitForVariables(processInstanceKey);

    // When - fetch with pagination
    SearchQueryClientImpl searchClientPaginated = new SearchQueryClientImpl(camundaClient, 2);
    var resultPage1 =
        searchClientPaginated.queryVariables(processInstanceKey, elementInstanceKey, null);

    // Then
    assertThat(resultPage1.items()).hasSizeLessThanOrEqualTo(2);
    var allItems = new ArrayList<>(resultPage1.items());

    if (resultPage1.page().endCursor() != null) {
      var resultPage2 =
          searchClientPaginated.queryVariables(
              processInstanceKey, elementInstanceKey, resultPage1.page().endCursor());
      allItems.addAll(resultPage2.items());
    }

    assertThat(allItems.size()).isGreaterThanOrEqualTo(2);
  }

  @Test
  public void shouldGetProcessModel() {
    // Given
    BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("modelTestProcess").startEvent("start").endEvent("end").done();

    var deployment =
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(modelInstance, "modelTestProcess.bpmn")
            .send()
            .join();

    long processDefinitionKey = deployment.getProcesses().getFirst().getProcessDefinitionKey();

    waitForProcessDefinition(processDefinitionKey);

    // When
    SearchQueryClientImpl searchClient = new SearchQueryClientImpl(camundaClient, 200);
    BpmnModelInstance retrievedModel = searchClient.getProcessModel(processDefinitionKey);

    // Then
    assertThat(retrievedModel).isNotNull();
    var processElements =
        retrievedModel.getModelElementsByType(io.camunda.zeebe.model.bpmn.instance.Process.class);
    assertThat(processElements).isNotEmpty();
    var process = processElements.iterator().next();
    assertThat(process.getId()).isEqualTo("modelTestProcess");
  }

  @Test
  public void shouldGetProcessDefinition() {
    // Given
    BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("definitionTestProcess").startEvent().endEvent().done();

    var deployment =
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(modelInstance, "definitionTestProcess.bpmn")
            .send()
            .join();

    long processDefinitionKey = deployment.getProcesses().getFirst().getProcessDefinitionKey();

    waitForProcessDefinition(processDefinitionKey);

    // When
    SearchQueryClientImpl searchClient = new SearchQueryClientImpl(camundaClient, 200);
    var processDefinition = searchClient.getProcessDefinition(processDefinitionKey);

    // Then
    assertThat(processDefinition).isNotNull();
    assertThat(processDefinition.getProcessDefinitionId()).isEqualTo("definitionTestProcess");
    assertThat(processDefinition.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
  }

  private void createAndDeployProcess(String processId, int versions) {
    IntStream.range(0, versions)
        .forEach(
            i -> {
              BpmnModelInstance modelInstance;
              modelInstance =
                  Bpmn.createExecutableProcess(processId)
                      .startEvent("StartEvent" + i)
                      .endEvent()
                      .done();
              camundaClient
                  .newDeployResourceCommand()
                  .addProcessModel(modelInstance, processId + ".bpmn")
                  .send()
                  .join();
            });
  }

  private void waitForIndexing(int expectedCount) {
    // Wait for the process definitions to be indexed
    Awaitility.await("should deploy process definitions and import in ES")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newProcessDefinitionSearchRequest().send().join();
              assertThat(result.items().size()).isEqualTo(expectedCount);
            });
  }

  private void waitForMessageSubscription() {
    Awaitility.await("should create message subscription")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result = camundaClient.newMessageSubscriptionSearchRequest().send().join();
              assertThat(result.items()).isNotEmpty();
            });
  }

  private void waitForActiveElement(long processDefinitionKey, String elementId) {
    Awaitility.await("should have active element")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newElementInstanceSearchRequest()
                      .filter(
                          f -> f.processDefinitionKey(processDefinitionKey).elementId(elementId))
                      .send()
                      .join();
              assertThat(result.items()).isNotEmpty();
            });
  }

  private void waitForVariables(long processInstanceKey) {
    Awaitility.await("should have variables indexed")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newVariableSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .send()
                      .join();
              assertThat(result.items()).isNotEmpty();
            });
  }

  private void waitForProcessDefinition(long processDefinitionKey) {
    Awaitility.await("should index process definition")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions()
        .untilAsserted(
            () -> camundaClient.newProcessDefinitionGetRequest(processDefinitionKey).send().join());
  }
}
