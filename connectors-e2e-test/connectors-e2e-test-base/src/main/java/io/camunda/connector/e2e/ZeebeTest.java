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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Process;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;

public class ZeebeTest {

  private final CamundaClient camundaClient;
  private DeploymentEvent deploymentEvent;
  private ProcessInstanceEvent processInstanceEvent;

  public ZeebeTest(CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  public static ZeebeTest with(CamundaClient camundaClient) {
    return new ZeebeTest(camundaClient);
  }

  public ZeebeTest deploy(BpmnModelInstance bpmnModelInstance) {
    var process =
        bpmnModelInstance.getModelElementsByType(Process.class).stream().findFirst().get();
    this.deploymentEvent =
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(bpmnModelInstance, process.getId() + ".bpmn")
            .send()
            .join();
    return this;
  }

  public ZeebeTest createInstance() {
    Assertions.assertNotNull(deploymentEvent, "Process needs to be deployed first.");
    var bpmnProcessId = deploymentEvent.getProcesses().get(0).getBpmnProcessId();
    processInstanceEvent =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(bpmnProcessId)
            .latestVersion()
            .send()
            .join();
    return this;
  }

  public ZeebeTest waitForProcessCompletion() {
    Awaitility.with()
        .pollInSameThread()
        .await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> CamundaAssert.assertThat(processInstanceEvent).isCompleted());
    return this;
  }

  public DeploymentEvent getDeploymentEvent() {
    return deploymentEvent;
  }

  public ProcessInstanceEvent getProcessInstanceEvent() {
    return processInstanceEvent;
  }
}
