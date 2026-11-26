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

import static io.camunda.process.test.api.CamundaAssert.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.MessageSubscriptionState;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.MessageSubscription;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.connector.test.utils.oidc.MockOidcServer;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Covers tenant isolation of the send message connector against a cluster that enforces
 * multi-tenancy checks. The client of the test (and with it the connector runtime) is the CPT
 * multi-tenancy user, which this test assigns to {@link #TENANT_A} and {@link #TENANT_B}. Although
 * the runtime may act in both, a message must only ever reach the tenant of the sending job.
 */
@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false",
      "camunda.saas.secrets.projectId=42",
      "camunda.client.enabled=true",
      "camunda.connector.auth.audience=connectors.dev.ultrawombat.com",
      "camunda.connector.cloud.organizationId=orgId",
      "camunda.connector.auth.console.audience=cloud.dev.ultrawombat.com",
      "camunda.connector.secretprovider.discovery.enabled=false",
      "management.endpoints.web.exposure.include=*",
      "camunda.process-test.multi-tenancy-enabled=true",
      "camunda.client.worker.defaults.tenant-ids=<default>,tenant-a,tenant-b"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@SlowTest
@ComponentScan(basePackages = {"io.camunda.connector.runtime.app"})
public class MessageTenantIdTests {

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";
  private static final String DEFAULT_TENANT = "<default>";
  private static final String CLIENT_USER = "demo";

  private static final String MESSAGE_NAME = "tenant-message";
  private static final String CURRENT_TEMPLATE =
      "../../connectors/camunda-message/element-templates/send-message-connector-send-task.json";
  // the last template version that still offered the tenant id field
  private static final String TENANT_ID_TEMPLATE =
      "../../connectors/camunda-message/element-templates/versioned/send-message-connector-send-task-1.json";
  private static final String CORRELATION_KEY_EXPRESSION = "valOneCorrelation";
  private static final String CORRELATION_VALUE = "123";
  private static final MockOidcServer OIDC_SERVER = MockOidcServer.start();

  @DynamicPropertySource
  static void registerOidcProperties(DynamicPropertyRegistry registry) {
    registry.add("camunda.connector.auth.issuer", OIDC_SERVER::issuer);
  }

  @AfterAll
  static void stopOidcServer() {
    OIDC_SERVER.close();
  }

  @TempDir File tempDir;

  @Autowired CamundaClient camundaClient;

  @BeforeEach
  void createTenants() {
    createTenant(TENANT_A);
    createTenant(TENANT_B);
    assignClientUser(TENANT_A);
    assignClientUser(TENANT_B);
  }

  @Test
  void publish_reachesOnlyTheJobTenant() {
    var receiver = startReceiver(TENANT_A);
    var receiverInOtherTenant = startReceiver(TENANT_B);
    var receiverInDefaultTenant = startReceiver(DEFAULT_TENANT);

    var sender = runSender(TENANT_A, "publish", CURRENT_TEMPLATE, null);

    awaitCompleted(sender);
    assertThat(sender).hasVariable("tenantIdResult", TENANT_A).hasVariableNames("messageKeyResult");
    awaitCompleted(receiver);
    assertStaysActive(receiverInOtherTenant);
    assertStaysActive(receiverInDefaultTenant);
  }

  @Test
  void correlate_reachesOnlyTheJobTenant() {
    var receiver = startReceiver(TENANT_A);
    var receiverInOtherTenant = startReceiver(TENANT_B);

    var sender = runSender(TENANT_A, "correlate", CURRENT_TEMPLATE, null);

    awaitCompleted(sender);
    assertThat(sender)
        .hasVariable("tenantIdResult", TENANT_A)
        .hasVariable("processInstanceKeyResult", receiver.getProcessInstanceKey());
    awaitCompleted(receiver);
    assertStaysActive(receiverInOtherTenant);
  }

  @Test
  void correlate_withoutInstanceInJobTenant_raisesIncidentInsteadOfReachingOtherTenant() {
    var receiverInOtherTenant = startReceiver(TENANT_B);
    var receiverInDefaultTenant = startReceiver(DEFAULT_TENANT);

    var sender = runSender(TENANT_A, "correlate", CURRENT_TEMPLATE, null);

    assertIncidentFailedWith(sender, "code 404");
    assertStaysActive(receiverInOtherTenant);
    assertStaysActive(receiverInDefaultTenant);
  }

  @ParameterizedTest
  @ValueSource(strings = {"publish", "correlate"})
  void tenantIdOfJobTenant_fromOlderTemplate_isStillAccepted(String mode) {
    var receiver = startReceiver(TENANT_A);

    var sender = runSender(TENANT_A, mode, TENANT_ID_TEMPLATE, TENANT_A);

    awaitCompleted(sender);
    assertThat(sender).hasVariable("tenantIdResult", TENANT_A);
    awaitCompleted(receiver);
  }

  @ParameterizedTest
  @ValueSource(strings = {"publish", "correlate"})
  void tenantIdOfOtherTenant_fromOlderTemplate_raisesIncident(String mode) {
    var receiverInOtherTenant = startReceiver(TENANT_B);

    var sender = runSender(TENANT_A, mode, TENANT_ID_TEMPLATE, TENANT_B);

    assertIncidentFailedWith(sender, "can only send messages to the tenant of the job");
    assertStaysActive(receiverInOtherTenant);
  }

  private ProcessInstanceEvent startReceiver(String tenantId) {
    var model =
        Bpmn.createExecutableProcess("receiveProcess")
            .startEvent()
            .intermediateCatchEvent()
            .message(
                m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_KEY_EXPRESSION))
            .endEvent()
            .done();
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(model, "receiveMessage.bpmn")
        .tenantId(tenantId)
        .send()
        .join();
    var receiver =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("receiveProcess")
            .latestVersion()
            .variable(CORRELATION_KEY_EXPRESSION, CORRELATION_VALUE)
            .tenantId(tenantId)
            .send()
            .join();
    awaitSubscriptionCreated(receiver);
    return receiver;
  }

  /** Creating the instance does not imply its message subscription is already open. */
  private void awaitSubscriptionCreated(ProcessInstanceEvent receiver) {
    Awaitility.with()
        .pollInSameThread()
        .await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() ->
            org.assertj.core.api.Assertions.assertThat(
                    camundaClient
                        .newMessageSubscriptionSearchRequest()
                        .filter(f -> f.processInstanceKey(receiver.getProcessInstanceKey()))
                        .send()
                        .join()
                        .items())
                .extracting(MessageSubscription::getMessageSubscriptionState)
                .containsExactly(MessageSubscriptionState.CREATED));
  }

  /** Deploys and starts the sending process in {@code senderTenant}, the tenant of its job. */
  private ProcessInstanceEvent runSender(
      String senderTenant, String mode, String templatePath, String tenantIdInput) {
    BpmnModelInstance model =
        Bpmn.createExecutableProcess("sendProcess")
            .startEvent()
            .sendTask("sendTask")
            .endEvent()
            .done();
    var template =
        ElementTemplate.from(templatePath)
            .property("correlationType.type", mode)
            .property("messageName", MESSAGE_NAME)
            .property("correlationKey", CORRELATION_VALUE)
            .property("retryCount", "1")
            .property("resultVariable", "messageResponse")
            .property(
                "resultExpression",
                "={messageKeyResult: messageKey, processInstanceKeyResult: processInstanceKey, tenantIdResult: tenantId}");
    if (tenantIdInput != null) {
      template.property("tenantId", tenantIdInput);
    }
    var modelWithTemplate =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "message_send.bpmn"))
            .apply(
                template.writeTo(new File(tempDir, "sendTaskTemplate.json")),
                "sendTask",
                new File(tempDir, "message_send_with_template.bpmn"));
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(modelWithTemplate, "message_send.bpmn")
        .tenantId(senderTenant)
        .send()
        .join();
    return camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId("sendProcess")
        .latestVersion()
        .tenantId(senderTenant)
        .send()
        .join();
  }

  private void awaitCompleted(ProcessInstanceEvent instance) {
    Awaitility.with()
        .pollInSameThread()
        .await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(instance).isCompleted());
  }

  /** The incident alone does not tell why the job failed, so the cause has to be asserted. */
  private void assertIncidentFailedWith(ProcessInstanceEvent sender, String expectedMessagePart) {
    Awaitility.with()
        .pollInSameThread()
        .await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() ->
            org.assertj.core.api.Assertions.assertThat(
                    camundaClient
                        .newIncidentSearchRequest()
                        .filter(f -> f.processInstanceKey(sender.getProcessInstanceKey()))
                        .send()
                        .join()
                        .items())
                .extracting(Incident::getErrorMessage)
                .anySatisfy(message ->
                    org.assertj.core.api.Assertions.assertThat(message)
                        .contains(expectedMessagePart)));
  }

  private void assertStaysActive(ProcessInstanceEvent instance) {
    Awaitility.with()
        .pollInSameThread()
        .await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(instance).isActive());
  }

  private void createTenant(String tenantId) {
    ignoringConflict(() ->
        camundaClient.newCreateTenantCommand().tenantId(tenantId).name(tenantId).send().join());
  }

  private void assignClientUser(String tenantId) {
    ignoringConflict(() ->
        camundaClient
            .newAssignUserToTenantCommand()
            .username(CLIENT_USER)
            .tenantId(tenantId)
            .send()
            .join());
  }

  /** The cluster outlives a single test, so tenants created by an earlier test already exist. */
  private static void ignoringConflict(Runnable command) {
    try {
      command.run();
    } catch (ProblemException e) {
      if (e.code() != 409) {
        throw e;
      }
    }
  }
}
