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
import io.camunda.connector.runtime.inbound.EmptyConfiguration;
import io.camunda.connector.test.SlowTest;
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
}
