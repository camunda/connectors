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
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.inbound.InboundConnectorTestConfiguration.InboundConnectorTestHelper;
import io.camunda.connector.runtime.inbound.executable.ActiveExecutableQuery;
import io.camunda.connector.runtime.inbound.executable.ActiveExecutableResponse;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * CPT (Camunda Process Test) for inbound connector multi-version lifecycle scenarios.
 *
 * <p>Tests the flow: Process Import → Process State Management → Executable Registry → Connector
 * Activation/Deactivation
 *
 * <p>These tests verify the multi-version support including:
 *
 * <ul>
 *   <li>Basic activation on deployment
 *   <li>Deduplication across versions with same connector config
 *   <li>Restart when connector config changes
 *   <li>Deactivation when connector is removed
 *   <li>Active instances keeping old versions alive
 *   <li>Cross-version conflict detection
 * </ul>
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
public class InboundConnectorMultiVersionTests {

  private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30);
  private static final String INBOUND_BPMN = "bpmn/inbound-test-process.bpmn";
  private static final String INBOUND_TEMPLATE = "element-templates/test-inbound-connector.json";

  @TempDir File tempDir;

  @Autowired CamundaClient camundaClient;

  @Autowired InboundExecutableRegistry executableRegistry;
  @Autowired InboundConnectorTestHelper inboundConnectorTestHelper;

  private volatile String testProcessId;

  @BeforeEach
  void setUp() {
    // Generate unique process ID per test to avoid cross-test interference
    testProcessId = "testProcess_" + UUID.randomUUID().toString().substring(0, 8);
    TestInboundConnector.resetCounters();

    // clear process definition caches & reset executables from previous tests
    inboundConnectorTestHelper.setUpTest();
  }

  // ============= BPMN Building Helpers =============

  /**
   * Creates a BPMN model with an Intermediate Catch Event that has an inbound connector configured.
   * Only applies template to catchEvent1.
   *
   * @param configValue a generic configuration value (used for AUTO deduplication)
   * @return the BPMN model instance
   */
  private BpmnModelInstance createInboundConnectorProcess(String configValue) {
    return createInboundConnectorProcess(configValue, null);
  }

  /**
   * Creates a BPMN model with an Intermediate Catch Event that has an inbound connector configured.
   * Only applies template to catchEvent1.
   *
   * @param configValue a generic configuration value (affects AUTO deduplication hash)
   * @param deduplicationId optional custom deduplication ID (null for AUTO mode)
   * @return the BPMN model instance
   */
  private BpmnModelInstance createInboundConnectorProcess(
      String configValue, String deduplicationId) {
    return createInboundConnectorProcess(configValue, deduplicationId, "catchEvent1");
  }

  /**
   * Creates a BPMN model with an Intermediate Catch Event that has an inbound connector configured.
   *
   * @param configValue a generic configuration value (affects AUTO deduplication hash)
   * @param deduplicationId optional custom deduplication ID (null for AUTO mode)
   * @param elementId the element ID to apply the template to
   * @return the BPMN model instance
   */
  private BpmnModelInstance createInboundConnectorProcess(
      String configValue, String deduplicationId, String elementId) {
    // Load the static BPMN file and replace the process ID with the unique test process ID
    var bpmnFile = getResourceFile(INBOUND_BPMN);
    var modifiedBpmn = BpmnFile.replace(bpmnFile, replace("inboundTestProcess", testProcessId));

    // Write to temp file for element-templates-cli
    var tempBpmnFile = new File(tempDir, testProcessId + ".bpmn");
    Bpmn.writeModelToFile(tempBpmnFile, modifiedBpmn);

    var templateBuilder =
        ElementTemplate.from(getResourcePath(INBOUND_TEMPLATE))
            .property("configValue", configValue)
            .property("correlationKeyProcess", "=correlationKey")
            .property("correlationKeyPayload", "=body.correlationKey")
            .property("deduplicationModeManualFlag", deduplicationId != null ? "true" : "false");

    if (deduplicationId != null) {
      templateBuilder.property("deduplicationId", deduplicationId);
    }

    var templateFile = templateBuilder.writeTo(new File(tempDir, "template.json"));

    return new BpmnFile(tempBpmnFile)
        .apply(templateFile, elementId, new File(tempDir, testProcessId + "-result.bpmn"));
  }

  /**
   * Creates a BPMN model with inbound connectors on BOTH catch events (catchEvent1 and
   * catchEvent2).
   *
   * @param configValue1 configuration value for catchEvent1
   * @param deduplicationId1 deduplication ID for catchEvent1 (null for AUTO)
   * @param configValue2 configuration value for catchEvent2
   * @param deduplicationId2 deduplication ID for catchEvent2 (null for AUTO)
   * @return the BPMN model instance
   */
  private BpmnModelInstance createInboundConnectorProcessWithTwoCatchEvents(
      String configValue1, String deduplicationId1, String configValue2, String deduplicationId2) {
    // Load the static BPMN file and replace the process ID with the unique test process ID
    var bpmnFile = getResourceFile(INBOUND_BPMN);
    var modifiedBpmn = BpmnFile.replace(bpmnFile, replace("inboundTestProcess", testProcessId));

    // Write to temp file for element-templates-cli
    var tempBpmnFile = new File(tempDir, testProcessId + ".bpmn");
    Bpmn.writeModelToFile(tempBpmnFile, modifiedBpmn);

    // Apply template to catchEvent1
    var templateBuilder1 =
        ElementTemplate.from(getResourcePath(INBOUND_TEMPLATE))
            .property("configValue", configValue1)
            .property("correlationKeyProcess", "=correlationKey")
            .property("correlationKeyPayload", "=body.correlationKey")
            .property("deduplicationModeManualFlag", deduplicationId1 != null ? "true" : "false");
    if (deduplicationId1 != null) {
      templateBuilder1.property("deduplicationId", deduplicationId1);
    }
    var templateFile1 = templateBuilder1.writeTo(new File(tempDir, "template1.json"));

    var intermediateFile = new File(tempDir, testProcessId + "-intermediate.bpmn");
    new BpmnFile(tempBpmnFile).apply(templateFile1, "catchEvent1", intermediateFile);

    // Apply template to catchEvent2
    var templateBuilder2 =
        ElementTemplate.from(getResourcePath(INBOUND_TEMPLATE))
            .property("configValue", configValue2)
            .property("correlationKeyProcess", "=correlationKey2")
            .property("correlationKeyPayload", "=body.correlationKey2")
            .property("deduplicationModeManualFlag", deduplicationId2 != null ? "true" : "false");
    if (deduplicationId2 != null) {
      templateBuilder2.property("deduplicationId", deduplicationId2);
    }
    var templateFile2 = templateBuilder2.writeTo(new File(tempDir, "template2.json"));

    var resultFile = new File(tempDir, testProcessId + "-result.bpmn");
    return new BpmnFile(intermediateFile).apply(templateFile2, "catchEvent2", resultFile);
  }

  /** Creates a simple BPMN process without any connectors. */
  private BpmnModelInstance createPlainProcess() {
    return Bpmn.createExecutableProcess(testProcessId).startEvent().endEvent().done();
  }

  /**
   * Creates a plain process with intermediate catch event for testing active instance scenarios
   * when v2 has no connector.
   */
  private BpmnModelInstance createPlainProcessWithIntermediateEvent() {
    return Bpmn.createExecutableProcess(testProcessId)
        .startEvent()
        .intermediateCatchEvent("catchEvent")
        .message(m -> m.name("plain-message").zeebeCorrelationKeyExpression("correlationKey"))
        .endEvent()
        .done();
  }

  private String getResourcePath(String resourceName) {
    var resource = getClass().getClassLoader().getResource(resourceName);
    if (resource == null) {
      throw new IllegalArgumentException("Resource not found: " + resourceName);
    }
    return resource.getPath();
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

  // ============= Deployment Helpers =============

  /** Deploys a process and returns the process definition key. */
  private long deploy(BpmnModelInstance model) {
    var deployment =
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(model, testProcessId + ".bpmn")
            .send()
            .join();
    return deployment.getProcesses().getFirst().getProcessDefinitionKey();
  }

  /** Waits for the process definition to be indexed in Camunda. */
  private void waitForProcessDefinitionIndexed(long processDefinitionKey) {
    Awaitility.await("process definition should be indexed")
        .atMost(AWAIT_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> camundaClient.newProcessDefinitionGetRequest(processDefinitionKey).send().join());
  }

  // ============= Query Helpers =============

  /** Queries executables for the given process. */
  private List<ActiveExecutableResponse> queryExecutables(String processId) {
    return executableRegistry.query(new ActiveExecutableQuery(processId, null, null, null));
  }

  /** Waits for executables to reach the expected count for the given process. */
  private void awaitExecutableCount(String processId, int expectedCount) {
    Awaitility.await("process " + processId + " should have " + expectedCount + " executable(s)")
        .atMost(AWAIT_TIMEOUT)
        .untilAsserted(() -> assertThat(queryExecutables(processId)).hasSize(expectedCount));
  }

  /** Waits for at least one healthy executable for the given process. */
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

  // ============= TEST SCENARIOS =============

  @Nested
  class BasicLifecycle {

    @Test
    void deployProcessWithInboundConnector_shouldActivateConnector() {
      // Given: A process with an inbound connector (Intermediate Catch Event)
      var model = createInboundConnectorProcess("config-a");

      // When: Deploy the process
      long processDefKey = deploy(model);
      waitForProcessDefinitionIndexed(processDefKey);

      // Then: Connector should be activated with healthy status
      awaitHealthyExecutable(testProcessId);

      var executables = queryExecutables(testProcessId);
      assertThat(executables).hasSize(1);
      assertThat(executables.getFirst().health().getStatus()).isEqualTo(Health.Status.UP);
      assertThat(executables.getFirst().elements()).hasSize(1);
      assertThat(executables.getFirst().elements().getFirst().element().processDefinitionKey())
          .isEqualTo(processDefKey);
    }
  }

  @Nested
  class VersionUpgradeNoActiveInstances {

    @Test
    void autoMode_deployV2WithSameProperties_shouldDeduplicateToSingleExecutable() {
      // Given: v1 deployed with connector (AUTO mode, configValue=config-a)
      var model1 = createInboundConnectorProcess("config-a");
      long keyV1 = deploy(model1);
      waitForProcessDefinitionIndexed(keyV1);
      awaitHealthyExecutable(testProcessId);

      // When: Deploy v2 with SAME properties (AUTO mode uses properties for deduplication)
      var model2 = createInboundConnectorProcess("config-a");
      long keyV2 = deploy(model2);
      waitForProcessDefinitionIndexed(keyV2);

      // Then: Still single executable (deduplicated by same properties)
      Awaitility.await("should have single executable with v2 element")
          .atMost(AWAIT_TIMEOUT)
          .untilAsserted(
              () -> {
                var executables = queryExecutables(testProcessId);
                assertThat(executables).hasSize(1);
                assertThat(executables.getFirst().health().getStatus()).isEqualTo(Health.Status.UP);

                // Only v2 should be represented (v1 has no active instances)
                var processDefKeys =
                    executables.getFirst().elements().stream()
                        .map(e -> e.element().processDefinitionKey())
                        .collect(Collectors.toSet());
                assertThat(processDefKeys).contains(keyV2);
              });
    }

    @Test
    void autoMode_deployV2WithDifferentProperties_shouldRestartConnector() {
      // Given: v1 deployed with configValue=config-a (AUTO mode)
      var model1 = createInboundConnectorProcess("config-a");
      long keyV1 = deploy(model1);
      waitForProcessDefinitionIndexed(keyV1);
      awaitHealthyExecutable(testProcessId);

      // When: Deploy v2 with DIFFERENT properties (configValue=config-b)
      var model2 = createInboundConnectorProcess("config-b");
      long keyV2 = deploy(model2);
      waitForProcessDefinitionIndexed(keyV2);

      // Then: Old connector deactivated, new one activated (different dedup ID due to different
      // props)
      Awaitility.await("should have new executable with v2 config")
          .atMost(AWAIT_TIMEOUT)
          .untilAsserted(
              () -> {
                var executables = queryExecutables(testProcessId);
                assertThat(executables).hasSize(1);
                assertThat(executables.getFirst().health().getStatus()).isEqualTo(Health.Status.UP);

                // Only v2 element should be present
                assertThat(executables.getFirst().elements()).hasSize(1);
                assertThat(
                        executables
                            .getFirst()
                            .elements()
                            .getFirst()
                            .element()
                            .processDefinitionKey())
                    .isEqualTo(keyV2);
              });
    }

    @Test
    void manualMode_deployV2WithSameDeduplicationId_shouldDeduplicateToSingleExecutable() {
      // Given: v1 deployed with manual dedup ID "my-dedup-id"
      var model1 = createInboundConnectorProcess("config-a", "my-dedup-id");
      long keyV1 = deploy(model1);
      waitForProcessDefinitionIndexed(keyV1);
      awaitHealthyExecutable(testProcessId);

      // When: Deploy v2 with SAME manual dedup ID (same properties)
      var model2 = createInboundConnectorProcess("config-a", "my-dedup-id");
      long keyV2 = deploy(model2);
      waitForProcessDefinitionIndexed(keyV2);

      // Then: Still single executable (deduplicated by manual ID)
      Awaitility.await("should have single executable with v2 element")
          .atMost(AWAIT_TIMEOUT)
          .untilAsserted(
              () -> {
                var executables = queryExecutables(testProcessId);
                assertThat(executables).hasSize(1);
                assertThat(executables.getFirst().health().getStatus()).isEqualTo(Health.Status.UP);

                // Only v2 should be represented (v1 has no active instances)
                var processDefKeys =
                    executables.getFirst().elements().stream()
                        .map(e -> e.element().processDefinitionKey())
                        .collect(Collectors.toSet());
                assertThat(processDefKeys).contains(keyV2);
              });
    }

    @Test
    void manualMode_deployV2WithDifferentDeduplicationId_shouldRestartConnector() {
      // Given: v1 deployed with dedup ID "dedup-a"
      var model1 = createInboundConnectorProcess("config-a", "dedup-a");
      long keyV1 = deploy(model1);
      waitForProcessDefinitionIndexed(keyV1);
      awaitHealthyExecutable(testProcessId);

      // When: Deploy v2 with DIFFERENT dedup ID "dedup-b"
      var model2 = createInboundConnectorProcess("config-a", "dedup-b");
      long keyV2 = deploy(model2);
      waitForProcessDefinitionIndexed(keyV2);

      // Then: Old connector deactivated, new one activated
      Awaitility.await("should have new executable with v2 config")
          .atMost(AWAIT_TIMEOUT)
          .untilAsserted(
              () -> {
                var executables = queryExecutables(testProcessId);
                assertThat(executables).hasSize(1);
                assertThat(executables.getFirst().health().getStatus()).isEqualTo(Health.Status.UP);

                // Only v2 element should be present
                assertThat(executables.getFirst().elements()).hasSize(1);
                assertThat(
                        executables
                            .getFirst()
                            .elements()
                            .getFirst()
                            .element()
                            .processDefinitionKey())
                    .isEqualTo(keyV2);
              });
    }

    @Test
    void deployV2WithoutConnector_shouldDeactivateConnector() {
      // Given: v1 deployed with connector
      var model1 = createInboundConnectorProcess("config-a");
      long keyV1 = deploy(model1);
      waitForProcessDefinitionIndexed(keyV1);
      awaitHealthyExecutable(testProcessId);

      // When: Deploy v2 WITHOUT connector (plain process)
      var model2 = createPlainProcess();
      long keyV2 = deploy(model2);
      waitForProcessDefinitionIndexed(keyV2);

      // Then: No executables (connector removed, no active instances on v1)
      awaitExecutableCount(testProcessId, 0);
    }

    @Test
    void deployV1_deployV2WithoutConnector_deployV3WithConnector_shouldReactivate() {
      // Given: v1 deployed with connector
      var model1 = createInboundConnectorProcess("config-a");
      long keyV1 = deploy(model1);
      waitForProcessDefinitionIndexed(keyV1);
      awaitHealthyExecutable(testProcessId);

      // When: Deploy v2 WITHOUT connector (deactivates the connector)
      var model2 = createPlainProcess();
      long keyV2 = deploy(model2);
      waitForProcessDefinitionIndexed(keyV2);
      awaitExecutableCount(testProcessId, 0);

      // When: Deploy v3 WITH connector again (same config as v1)
      var model3 = createInboundConnectorProcess("config-a");
      long keyV3 = deploy(model3);
      waitForProcessDefinitionIndexed(keyV3);

      // Then: Connector should be reactivated with v3
      Awaitility.await("connector should be reactivated with v3")
          .atMost(AWAIT_TIMEOUT)
          .untilAsserted(
              () -> {
                var executables = queryExecutables(testProcessId);
                assertThat(executables).hasSize(1);
                assertThat(executables.getFirst().health().getStatus()).isEqualTo(Health.Status.UP);
                assertThat(
                        executables
                            .getFirst()
                            .elements()
                            .getFirst()
                            .element()
                            .processDefinitionKey())
                    .isEqualTo(keyV3);
              });
    }

    @Test
    void deployV1_deployV2WithoutConnector_deployV3WithDifferentConfig_shouldActivateNewConfig() {
      // Given: v1 deployed with connector (configValue=config-a)
      var model1 = createInboundConnectorProcess("config-a");
      long keyV1 = deploy(model1);
      waitForProcessDefinitionIndexed(keyV1);
      awaitHealthyExecutable(testProcessId);

      // When: Deploy v2 WITHOUT connector (deactivates the connector)
      var model2 = createPlainProcess();
      long keyV2 = deploy(model2);
      waitForProcessDefinitionIndexed(keyV2);
      awaitExecutableCount(testProcessId, 0);

      // When: Deploy v3 WITH connector but DIFFERENT config (configValue=config-b)
      var model3 = createInboundConnectorProcess("config-b");
      long keyV3 = deploy(model3);
      waitForProcessDefinitionIndexed(keyV3);

      // Then: Connector should be activated with v3's new config
      Awaitility.await("connector should be activated with v3 config")
          .atMost(AWAIT_TIMEOUT)
          .untilAsserted(
              () -> {
                var executables = queryExecutables(testProcessId);
                assertThat(executables).hasSize(1);
                assertThat(executables.getFirst().health().getStatus()).isEqualTo(Health.Status.UP);
                assertThat(
                        executables
                            .getFirst()
                            .elements()
                            .getFirst()
                            .element()
                            .processDefinitionKey())
                    .isEqualTo(keyV3);
              });
    }
  }

  @Nested
  class ActiveInstancesAffectLifecycle {

    @Test
    void deployV1StartInstance_deployV2SameConfig_shouldDeduplicateWithBothVersions() {
      // Given: v1 deployed with connector using Intermediate Catch Event
      var model1 = createInboundConnectorProcess("config-a", "shared-dedup");
      long keyV1 = deploy(model1);
      waitForProcessDefinitionIndexed(keyV1);
      awaitHealthyExecutable(testProcessId);

      // Start an instance on v1 - it will wait at the intermediate catch event
      // creating a message subscription (makes v1 "active")
      camundaClient
          .newCreateInstanceCommand()
          .processDefinitionKey(keyV1)
          .variable("correlationKey", "test-correlation-key")
          .send()
          .join();

      // When: Deploy v2 with SAME connector config
      var model2 = createInboundConnectorProcess("config-a", "shared-dedup");
      long keyV2 = deploy(model2);
      waitForProcessDefinitionIndexed(keyV2);

      // Then: Single executable (deduplicated) with elements from BOTH versions
      Awaitility.await("should have single executable with both version elements")
          .atMost(AWAIT_TIMEOUT)
          .untilAsserted(
              () -> {
                var executables = queryExecutables(testProcessId);
                assertThat(executables).hasSize(1);
                assertThat(executables.getFirst().health().getStatus()).isEqualTo(Health.Status.UP);

                // Both v1 and v2 should be represented
                var processDefKeys =
                    executables.getFirst().elements().stream()
                        .map(e -> e.element().processDefinitionKey())
                        .collect(Collectors.toSet());
                assertThat(processDefKeys).containsExactlyInAnyOrder(keyV1, keyV2);
              });
    }

    @Test
    void deployV1StartInstance_deployV2WithoutConnector_shouldKeepV1Active() {
      // Given: v1 deployed with connector using Intermediate Catch Event
      var model1 = createInboundConnectorProcess("config-a");
      long keyV1 = deploy(model1);
      waitForProcessDefinitionIndexed(keyV1);
      awaitHealthyExecutable(testProcessId);

      // Start an instance on v1 - it will wait at the intermediate catch event
      // creating a message subscription (makes v1 "active")
      camundaClient
          .newCreateInstanceCommand()
          .processDefinitionKey(keyV1)
          .variable("correlationKey", "test-correlation-key")
          .send()
          .join();

      // When: Deploy v2 WITHOUT connector (plain process with intermediate event)
      var model2 = createPlainProcessWithIntermediateEvent();
      long keyV2 = deploy(model2);
      waitForProcessDefinitionIndexed(keyV2);

      // Then: v1 connector STILL active (because v1 has active instance with message subscription)
      Awaitility.await("v1 connector should still be active")
          .atMost(AWAIT_TIMEOUT)
          .during(Duration.ofSeconds(5)) // Ensure it stays active
          .untilAsserted(
              () -> {
                var executables = queryExecutables(testProcessId);
                assertThat(executables).hasSize(1);
                assertThat(
                        executables
                            .getFirst()
                            .elements()
                            .getFirst()
                            .element()
                            .processDefinitionKey())
                    .isEqualTo(keyV1);
              });
    }
  }

  @Nested
  class CrossVersionConflicts {

    @Test
    void
        autoMode_deployV1StartInstance_deployV2DifferentProperties_shouldHaveSeparateExecutables() {
      // Given: v1 deployed with configValue=config-a using Intermediate Catch Event
      var model1 = createInboundConnectorProcess("config-a");
      long keyV1 = deploy(model1);
      waitForProcessDefinitionIndexed(keyV1);
      awaitHealthyExecutable(testProcessId);

      // Start an instance on v1 - creates message subscription (makes v1 "active")
      camundaClient
          .newCreateInstanceCommand()
          .processDefinitionKey(keyV1)
          .variable("correlationKey", "test-correlation-key")
          .send()
          .join();

      // When: Deploy v2 with DIFFERENT properties (configValue=config-b)
      // AUTO mode computes different dedup IDs for different properties
      var model2 = createInboundConnectorProcess("config-b");
      long keyV2 = deploy(model2);
      waitForProcessDefinitionIndexed(keyV2);

      // Then: Both executables exist (different dedup IDs = separate executables)
      Awaitility.await("should have two separate executables")
          .atMost(AWAIT_TIMEOUT)
          .untilAsserted(
              () -> {
                var executables = queryExecutables(testProcessId);
                assertThat(executables).hasSize(2);
              });
    }

    @Test
    void
        manualMode_deployV1StartInstance_deployV2SameIdDifferentProperties_shouldReplaceWithInvalid() {
      // Given: v1 deployed with manual dedup ID "shared-id" and configValue=config-a
      var model1 = createInboundConnectorProcess("config-a", "shared-id");
      long keyV1 = deploy(model1);
      waitForProcessDefinitionIndexed(keyV1);
      awaitHealthyExecutable(testProcessId);

      // Verify v1 is healthy before starting instance
      var executablesBeforeInstance = queryExecutables(testProcessId);
      assertThat(executablesBeforeInstance).hasSize(1);
      assertThat(executablesBeforeInstance.getFirst().health().getStatus())
          .isEqualTo(Health.Status.UP);

      // Start an instance on v1 - creates message subscription (makes v1 "active")
      camundaClient
          .newCreateInstanceCommand()
          .processDefinitionKey(keyV1)
          .variable("correlationKey", "test-correlation-key")
          .send()
          .join();

      // When: Deploy v2 with SAME manual dedup ID but DIFFERENT properties (configValue=config-b)
      // This creates a cross-version conflict - v2 replaces v1 with an invalid executable
      var model2 = createInboundConnectorProcess("config-b", "shared-id");
      long keyV2 = deploy(model2);
      waitForProcessDefinitionIndexed(keyV2);

      // Then: The executable is replaced with an INVALID one
      // User can fix this by deploying a new version with correct configuration
      Awaitility.await("executable should be replaced with invalid")
          .atMost(AWAIT_TIMEOUT)
          .untilAsserted(
              () -> {
                var executables = queryExecutables(testProcessId);
                assertThat(executables).hasSize(1);

                var executable = executables.getFirst();
                // The executable should be DOWN/INVALID due to configuration conflict
                assertThat(executable.health().getStatus()).isEqualTo(Health.Status.DOWN);
                assertThat(executable.health().getError()).isNotNull();
              });
    }

    @Test
    void manualMode_deployV1_deployV2Invalid_deployV3Valid_shouldFixTheIssue() {
      // Given: v1 deployed with manual dedup ID "shared-id" and configValue=config-a
      var model1 = createInboundConnectorProcess("config-a", "shared-id");
      long keyV1 = deploy(model1);
      waitForProcessDefinitionIndexed(keyV1);
      awaitHealthyExecutable(testProcessId);

      // Start an instance on v1 to keep it active (required for cross-version conflict)
      camundaClient
          .newCreateInstanceCommand()
          .processDefinitionKey(keyV1)
          .variable("correlationKey", "test-correlation-key")
          .send()
          .join();

      // Deploy v2 with conflicting config (creates invalid executable)
      var model2 = createInboundConnectorProcess("config-b", "shared-id");
      long keyV2 = deploy(model2);
      waitForProcessDefinitionIndexed(keyV2);

      // Wait for executable to become invalid
      Awaitility.await("executable should be invalid after v2")
          .atMost(AWAIT_TIMEOUT)
          .untilAsserted(
              () -> {
                var executables = queryExecutables(testProcessId);
                assertThat(executables).hasSize(1);
                assertThat(executables.getFirst().health().getStatus())
                    .isEqualTo(Health.Status.DOWN);
              });

      // When: Deploy v3 with correct config (same as v1, fixes the issue)
      var model3 = createInboundConnectorProcess("config-a", "shared-id");
      long keyV3 = deploy(model3);
      waitForProcessDefinitionIndexed(keyV3);

      // Then: Executable should be healthy again
      Awaitility.await("executable should be healthy after v3 fix")
          .atMost(AWAIT_TIMEOUT)
          .untilAsserted(
              () -> {
                var executables = queryExecutables(testProcessId);
                assertThat(executables).hasSize(1);
                assertThat(executables.getFirst().health().getStatus()).isEqualTo(Health.Status.UP);
              });
    }
  }

  @Nested
  class NoCrossProcessDeduplication {

    @Test
    void sameDeduplicationIdDifferentProcesses_shouldHaveSeparateExecutables() {
      // Given: Two different process definitions with the same manual deduplication ID
      var processId1 = testProcessId;
      var processId2 = testProcessId + "_other";

      // Deploy first process with dedup ID "shared-dedup"
      var model1 = createInboundConnectorProcess("config-a", "shared-dedup");
      long keyV1 = deploy(model1);
      waitForProcessDefinitionIndexed(keyV1);
      awaitHealthyExecutable(processId1);

      // Deploy second process (different BPMN process ID) with SAME dedup ID "shared-dedup"
      var originalProcessId = testProcessId;
      testProcessId = processId2;
      var model2 = createInboundConnectorProcess("config-a", "shared-dedup");
      long keyV2 = deploy(model2);
      waitForProcessDefinitionIndexed(keyV2);
      awaitHealthyExecutable(processId2);
      testProcessId = originalProcessId; // Restore for cleanup

      // Then: Should have TWO separate executables (no deduplication across different processes)
      var executables1 = queryExecutables(processId1);
      var executables2 = queryExecutables(processId2);

      assertThat(executables1).hasSize(1);
      assertThat(executables2).hasSize(1);

      // Each executable should be healthy and belong to its own process
      assertThat(executables1.getFirst().health().getStatus()).isEqualTo(Health.Status.UP);
      assertThat(executables2.getFirst().health().getStatus()).isEqualTo(Health.Status.UP);

      assertThat(executables1.getFirst().elements().getFirst().element().processDefinitionKey())
          .isEqualTo(keyV1);
      assertThat(executables2.getFirst().elements().getFirst().element().processDefinitionKey())
          .isEqualTo(keyV2);
    }
  }

  @Nested
  class MultipleCatchEventsInProcess {

    @Test
    void deployV1WithOneCatchEvent_deployV2WithTwoCatchEvents_shouldAddNewExecutable() {
      // Given: v1 deployed with connector only on catchEvent1
      var model1 = createInboundConnectorProcess("config-a", "dedup-event1");
      long keyV1 = deploy(model1);
      waitForProcessDefinitionIndexed(keyV1);
      awaitHealthyExecutable(testProcessId);

      // Verify v1 has one executable
      var executablesV1 = queryExecutables(testProcessId);
      assertThat(executablesV1).hasSize(1);
      assertThat(executablesV1.getFirst().health().getStatus()).isEqualTo(Health.Status.UP);

      // When: Deploy v2 with connectors on BOTH catchEvent1 and catchEvent2
      // catchEvent1 has same config (should be hot-swapped/deduplicated)
      // catchEvent2 is newly added (should create new executable)
      var model2 =
          createInboundConnectorProcessWithTwoCatchEvents(
              "config-a", "dedup-event1", // Same config for catchEvent1
              "config-b", "dedup-event2" // New config for catchEvent2
              );
      long keyV2 = deploy(model2);
      waitForProcessDefinitionIndexed(keyV2);

      // Then: Should have TWO executables:
      // - One for catchEvent1 (deduplicated, now pointing to v2)
      // - One for catchEvent2 (newly activated)
      Awaitility.await("should have two executables after v2 deployment")
          .atMost(AWAIT_TIMEOUT)
          .untilAsserted(
              () -> {
                var executables = queryExecutables(testProcessId);
                assertThat(executables).hasSize(2);

                // Both should be healthy
                assertThat(executables).allMatch(e -> e.health().getStatus() == Health.Status.UP);

                // Collect all process definition keys from all executables
                var allProcessDefKeys =
                    executables.stream()
                        .flatMap(e -> e.elements().stream())
                        .map(e -> e.element().processDefinitionKey())
                        .collect(Collectors.toSet());

                // v2 should be represented (v1 has no active instances so it's replaced)
                assertThat(allProcessDefKeys).contains(keyV2);
              });
    }

    @Test
    void deployV1WithTwoCatchEvents_deployV2WithOneCatchEvent_shouldDeactivateRemovedExecutable() {
      // Given: v1 deployed with connectors on BOTH catchEvent1 and catchEvent2
      var model1 =
          createInboundConnectorProcessWithTwoCatchEvents(
              "config-a", "dedup-event1", "config-b", "dedup-event2");
      long keyV1 = deploy(model1);
      waitForProcessDefinitionIndexed(keyV1);

      // Wait for both executables to be active
      Awaitility.await("should have two executables for v1")
          .atMost(AWAIT_TIMEOUT)
          .untilAsserted(
              () -> {
                var executables = queryExecutables(testProcessId);
                assertThat(executables).hasSize(2);
                assertThat(executables).allMatch(e -> e.health().getStatus() == Health.Status.UP);
              });

      // When: Deploy v2 with connector ONLY on catchEvent1 (removing catchEvent2)
      var model2 = createInboundConnectorProcess("config-a", "dedup-event1");
      long keyV2 = deploy(model2);
      waitForProcessDefinitionIndexed(keyV2);

      // Then: Should have only ONE executable (catchEvent2's executable deactivated)
      Awaitility.await("should have one executable after v2 removes catchEvent2")
          .atMost(AWAIT_TIMEOUT)
          .untilAsserted(
              () -> {
                var executables = queryExecutables(testProcessId);
                assertThat(executables).hasSize(1);
                assertThat(executables.getFirst().health().getStatus()).isEqualTo(Health.Status.UP);

                // Should only have v2's element
                assertThat(
                        executables
                            .getFirst()
                            .elements()
                            .getFirst()
                            .element()
                            .processDefinitionKey())
                    .isEqualTo(keyV2);
              });
    }
  }
}
