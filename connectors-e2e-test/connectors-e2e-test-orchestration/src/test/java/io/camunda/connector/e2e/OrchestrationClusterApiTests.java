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

import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.CamundaAssert.assertThatUserTask;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Runs the connector against the real Orchestration Cluster API of the Camunda container that the
 * test starts, so the API itself checks the URLs and request bodies that the template builds.
 *
 * <p>Only the token endpoint is mocked. The template always fetches an OAuth token first, and the
 * test container does not check it.
 */
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
public class OrchestrationClusterApiTests extends BaseOrchestrationTest {

  private static final String WAIT_TASK = "waitTask";

  @RegisterExtension
  static WireMockExtension tokenServer =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired CamundaProcessTestContext processTestContext;

  private String tokenEndpoint;

  @BeforeEach
  void stubTokenEndpoint() {
    // The runtime caches tokens per token endpoint, so each test uses its own endpoint.
    var tokenPath = "/oauth/token/" + UUID.randomUUID();
    tokenServer.stubFor(
        post(urlPathEqualTo(tokenPath))
            .willReturn(
                ResponseDefinitionBuilder.okForJson(
                    Map.of("access_token", "test-access-token", "expires_in", 3600))));
    tokenEndpoint = "http://localhost:" + tokenServer.getPort() + tokenPath;
  }

  @Test
  void searchProcessInstancesWithFilterSortAndPaging() {
    var processId = uniqueProcessId();
    var keys = startWaitingInstances(processId, 3);

    var template =
        template("/process-instances", SEARCH)
            .propertyByBinding(
                "input.internal_filter", "={processDefinitionId: \"%s\"}".formatted(processId))
            .propertyByBinding(
                "input.internal_sort", "=[{field: \"processInstanceKey\", order: \"DESC\"}]")
            .propertyByBinding("input.internal_results", "2")
            .propertyByBinding(
                "resultExpression",
                "={keys: for item in response.body.items return string(item.processInstanceKey),"
                    + " total: response.body.page.totalItems}");
    var test = run(applyTemplate(template), Map.of()).waitForProcessCompletion();

    assertThat(test.getProcessInstanceEvent())
        .hasVariable("keys", List.of(keys.get(2).toString(), keys.get(1).toString()))
        .hasVariable("total", 3);
  }

  /** The key comes from a variable holding a number, the case from #10298. */
  @Test
  void getProcessInstanceByKey() {
    var processId = uniqueProcessId();
    var key = startWaitingInstances(processId, 1).getFirst();

    var template =
        template("/process-instances", GET_BY_KEY)
            .propertyByBinding("input.internal_key", "=targetKey")
            .propertyByBinding(
                "resultExpression",
                "={foundKey: string(response.body.processInstanceKey),"
                    + " foundProcess: response.body.processDefinitionId,"
                    + " foundState: response.body.state}");
    var test = run(applyTemplate(template), Map.of("targetKey", key)).waitForProcessCompletion();

    assertThat(test.getProcessInstanceEvent())
        .hasVariable("foundKey", key.toString())
        .hasVariable("foundProcess", processId)
        .hasVariable("foundState", "ACTIVE");
  }

  @Test
  void searchUserTasks() {
    var processId = uniqueProcessId();
    var key = startWaitingInstances(processId, 1).getFirst();
    assertThatUserTask(UserTaskSelectors.byProcessInstanceKey(key)).isCreated();

    var template =
        template("/user-tasks", SEARCH)
            .propertyByBinding(
                "input.internal_filter", "={processDefinitionId: \"%s\"}".formatted(processId))
            .propertyByBinding(
                "resultExpression",
                "={tasks: for task in response.body.items return {elementId: task.elementId,"
                    + " processInstanceKey: string(task.processInstanceKey)}}");
    var test = run(applyTemplate(template), Map.of()).waitForProcessCompletion();

    assertThat(test.getProcessInstanceEvent())
        .hasVariable(
            "tasks", List.of(Map.of("elementId", WAIT_TASK, "processInstanceKey", key.toString())));
  }

  private ElementTemplate template(String endpoint, String operation) {
    var apiUrl =
        processTestContext.getCamundaRestAddress().toString().replaceAll("/+$", "") + "/v2";
    return selfManagedTemplate(endpoint, operation, apiUrl, tokenEndpoint);
  }

  private static String uniqueProcessId() {
    return "waiting-process-" + UUID.randomUUID();
  }

  /**
   * Starts instances of a process that waits at a user task, and returns their keys once the API
   * can find them.
   */
  private List<Long> startWaitingInstances(String processId, int count) {
    var waitingProcess =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .userTask(WAIT_TASK)
            .zeebeUserTask()
            .endEvent()
            .done();
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(waitingProcess, processId + ".bpmn")
        .send()
        .join();

    var keys = new ArrayList<Long>();
    for (int i = 0; i < count; i++) {
      var instance =
          camundaClient
              .newCreateInstanceCommand()
              .bpmnProcessId(processId)
              .latestVersion()
              .send()
              .join();
      // Waits until the instance shows up in search, which lags a moment behind the engine.
      assertThat(instance).isActive();
      keys.add(instance.getProcessInstanceKey());
    }
    return keys;
  }
}
