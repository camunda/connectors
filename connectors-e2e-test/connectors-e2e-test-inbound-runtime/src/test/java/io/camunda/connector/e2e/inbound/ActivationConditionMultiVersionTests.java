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
package io.camunda.connector.e2e.inbound;

import static io.camunda.connector.e2e.BpmnFile.Replace.replace;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.inbound.InboundConnectorTestConfiguration.InboundConnectorTestHelper;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.inbound.executable.ActiveExecutableResponse;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Tests for activation condition validation with multi-version inbound connectors.
 *
 * <p>These tests verify the behavior when multiple process versions share the same inbound
 * connector configuration (deduplicated into a single executable) and have blank/no activation
 * conditions.
 *
 * <p>The issue being tested: When 2+ process versions with the same inbound connector config are
 * deployed and deduplicated, intermediate catch events with blank activation conditions should
 * still correlate correctly because they have distinct correlation keys. The runtime should not
 * fail with "TooManyMatchingElements" error.
 */
@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.polling.enabled=true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@Import(InboundConnectorTestConfiguration.class)
@SlowTest
public class ActivationConditionMultiVersionTests {

  private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30);
  private static final String INBOUND_BPMN = "bpmn/inbound-test-process.bpmn";
  private static final String INBOUND_TEMPLATE = "element-templates/test-inbound-connector.json";

  @TempDir File tempDir;

  @Autowired CamundaClient camundaClient;
  @Autowired InboundExecutableRegistry executableRegistry;
  @Autowired InboundConnectorTestHelper inboundConnectorTestHelper;
  @Autowired InboundCorrelationHandler correlationHandler;

  private volatile String testProcessId;

  @BeforeEach
  void setUp() {
    testProcessId = "activationConditionTest_" + UUID.randomUUID().toString().substring(0, 8);
    TestInboundConnector.resetCounters();
    inboundConnectorTestHelper.setUpTest();
  }

  /**
   * Creates a BPMN model with an Intermediate Catch Event connector. No activation condition is
   * set, which is the default/blank state.
   */
  private BpmnModelInstance createProcessWithBlankActivationCondition(
      String configValue, String deduplicationId) {
    var bpmnFile = getResourceFile(INBOUND_BPMN);
    var modifiedBpmn = BpmnFile.replace(bpmnFile, replace("inboundTestProcess", testProcessId));

    var tempBpmnFile = new File(tempDir, testProcessId + ".bpmn");
    Bpmn.writeModelToFile(tempBpmnFile, modifiedBpmn);

    var templateBuilder =
        ElementTemplate.from(getResourcePath(INBOUND_TEMPLATE))
            .property("configValue", configValue)
            .property("correlationKeyProcess", "=correlationKey")
            .property("correlationKeyPayload", "=body.correlationKey")
            .property("deduplicationModeManualFlag", deduplicationId != null ? "true" : "false");
    // Note: No activationCondition is set - this is intentionally blank

    if (deduplicationId != null) {
      templateBuilder.property("deduplicationId", deduplicationId);
    }

    var templateFile = templateBuilder.writeTo(new File(tempDir, "template.json"));
    return new BpmnFile(tempBpmnFile)
        .apply(templateFile, "catchEvent1", new File(tempDir, testProcessId + "-result.bpmn"));
  }

  private long deploy(BpmnModelInstance model) {
    var deployment =
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(model, testProcessId + ".bpmn")
            .send()
            .join();
    return deployment.getProcesses().getFirst().getProcessDefinitionKey();
  }

  private void waitForProcessDefinitionIndexed(long processDefinitionKey) {
    Awaitility.await("process definition should be indexed")
        .atMost(AWAIT_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> camundaClient.newProcessDefinitionGetRequest(processDefinitionKey).send().join());
  }

  private List<ActiveExecutableResponse> queryExecutables(String processId) {
    return executableRegistry.query(f -> f.bpmnProcessId(processId));
  }

  private void awaitHealthyExecutable(String processId) {
    Awaitility.await("process " + processId + " should have healthy executable")
        .atMost(AWAIT_TIMEOUT)
        .untilAsserted(
            () -> {
              var executables = queryExecutables(processId);
              assertThat(executables).isNotEmpty();
              assertThat(executables.getFirst().health().getStatus()).isEqualTo(Health.Status.UP);
            });
  }

  private File getResourceFile(String resourceName) {
    try {
      var resource = getClass().getClassLoader().getResource(resourceName);
      if (resource == null) {
        throw new IllegalArgumentException("Resource not found: " + resourceName);
      }
      return new File(resource.toURI());
    } catch (Exception e) {
      throw new RuntimeException("Failed to get resource file: " + resourceName, e);
    }
  }

  private String getResourcePath(String resourceName) {
    var resource = getClass().getClassLoader().getResource(resourceName);
    if (resource == null) {
      throw new IllegalArgumentException("Resource not found: " + resourceName);
    }
    return resource.getPath();
  }

  @Test
  @DisplayName(
      "Two versions with same config and blank activation condition should correlate successfully")
  void twoVersions_sameConfig_blankActivationCondition_shouldCorrelateSuccessfully() {
    // Given: Deploy v1 with intermediate catch event connector (blank activation condition)
    var model1 = createProcessWithBlankActivationCondition("config-a", "shared-dedup");
    long keyV1 = deploy(model1);
    waitForProcessDefinitionIndexed(keyV1);
    awaitHealthyExecutable(testProcessId);

    // Start instance on v1 - creates message subscription
    camundaClient
        .newCreateInstanceCommand()
        .processDefinitionKey(keyV1)
        .variable("correlationKey", "correlation-key-v1")
        .send()
        .join();

    // Deploy v2 with same config (will be deduplicated)
    var model2 = createProcessWithBlankActivationCondition("config-a", "shared-dedup");
    long keyV2 = deploy(model2);
    waitForProcessDefinitionIndexed(keyV2);

    // Wait for executable to have both version elements
    Awaitility.await("executable should have elements from both versions")
        .atMost(AWAIT_TIMEOUT)
        .untilAsserted(
            () -> {
              var executables = queryExecutables(testProcessId);
              assertThat(executables).hasSize(1);
              assertThat(executables.getFirst().elements()).hasSize(2);
            });

    // Start instance on v2 - creates another message subscription with different correlation key
    camundaClient
        .newCreateInstanceCommand()
        .processDefinitionKey(keyV2)
        .variable("correlationKey", "correlation-key-v2")
        .send()
        .join();

    // When: Try to correlate a message through the correlation handler
    // This should NOT fail with "TooManyMatchingElements" because:
    // - Both elements have blank activation conditions (both "match")
    // - BUT they have different correlation keys, so Zeebe will route correctly
    var executables = queryExecutables(testProcessId);
    var elements = executables.getFirst().elements();

    // Correlate with payload targeting v1's correlation key
    var correlationResult =
        correlationHandler.correlate(
            elements, Map.of("body", Map.of("correlationKey", "correlation-key-v1")));

    // then
    assertThat(correlationResult)
        .as("Correlation should succeed without TooManyMatchingElements error")
        .isInstanceOf(CorrelationResult.Success.MessagePublished.class);
  }
}
