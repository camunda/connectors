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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.PartitionBrokerHealth;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Process;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.springframework.util.CollectionUtils;

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

  public ZeebeTest awaitCompleteTopology() {
    return awaitCompleteTopology(1, 1, 1, Duration.ofSeconds(30));
  }

  /**
   * Waits until the topology is complete <em>and healthy</em>: the expected number of brokers and
   * partitions are present, and every reported partition is {@link PartitionBrokerHealth#HEALTHY}
   * with a healthy leader. The plain count check is not enough — under CPU contention the broker
   * reports the right topology counts while a partition is still installing services (health {@code
   * UNHEALTHY}, e.g. "Services not installed" / "Snapshot not taken yet"), so deploying against it
   * races a partition that cannot yet serve and leads to flaky assertion timeouts.
   */
  public ZeebeTest awaitCompleteTopology(
      final int clusterSize,
      final int partitionCount,
      final int replicationFactor,
      final Duration timeout) {
    Awaitility.with()
        .pollInSameThread()
        .await()
        .atMost(timeout)
        .untilAsserted(
            () -> {
              final var topology = camundaClient.newTopologyRequest().send().join();
              assertEquals(clusterSize, topology.getClusterSize());
              assertEquals(clusterSize, topology.getBrokers().size());
              assertEquals(partitionCount, topology.getPartitionsCount());
              assertEquals(replicationFactor, topology.getReplicationFactor());

              // Every partition reported by any broker must be healthy.
              final List<String> unhealthy =
                  topology.getBrokers().stream()
                      .flatMap(
                          broker ->
                              broker.getPartitions().stream()
                                  .filter(p -> p.getHealth() != PartitionBrokerHealth.HEALTHY)
                                  .map(
                                      p ->
                                          "broker "
                                              + broker.getMemberId()
                                              + " partition "
                                              + p.getPartitionId()
                                              + " = "
                                              + p.getHealth()))
                      .collect(Collectors.toList());
              assertTrue(unhealthy.isEmpty(), () -> "partitions not healthy yet: " + unhealthy);

              // Each partition must have a healthy leader so the broker can serve requests.
              for (int partitionId = 1; partitionId <= partitionCount; partitionId++) {
                final int pid = partitionId;
                final boolean hasHealthyLeader =
                    topology.getBrokers().stream()
                        .flatMap(broker -> broker.getPartitions().stream())
                        .filter(p -> p.getPartitionId() == pid)
                        .filter(PartitionInfo::isLeader)
                        .anyMatch(p -> p.getHealth() == PartitionBrokerHealth.HEALTHY);
                assertTrue(hasHealthyLeader, "no healthy leader for partition " + pid + " yet");
              }
            });
    return this;
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
    return createInstance(Collections.emptyMap());
  }

  public ZeebeTest createInstance(Map<String, Object> variables) {
    Assertions.assertNotNull(deploymentEvent, "Process needs to be deployed first.");
    var bpmnProcessId = deploymentEvent.getProcesses().get(0).getBpmnProcessId();
    var command =
        camundaClient.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();

    if (!CollectionUtils.isEmpty(variables)) {
      command = command.variables(variables);
    }

    processInstanceEvent = command.send().join();

    return this;
  }

  public ZeebeTest waitForProcessCompletion() {
    return waitForProcessCompletion(Duration.ofSeconds(20));
  }

  public ZeebeTest waitForProcessCompletion(Duration timeout) {
    Awaitility.with()
        .pollInSameThread()
        .await()
        .atMost(timeout)
        .untilAsserted(() -> CamundaAssert.assertThat(processInstanceEvent).isCompleted());
    return this;
  }

  public ZeebeTest waitForActiveIncidents() {
    return waitForActiveIncidents(Duration.ofSeconds(20));
  }

  public ZeebeTest waitForActiveIncidents(Duration timeout) {
    Awaitility.with()
        .pollInSameThread()
        .await()
        .atMost(timeout)
        .untilAsserted(() -> CamundaAssert.assertThat(processInstanceEvent).hasActiveIncidents());
    return this;
  }

  public DeploymentEvent getDeploymentEvent() {
    return deploymentEvent;
  }

  public ProcessInstanceEvent getProcessInstanceEvent() {
    return processInstanceEvent;
  }
}
