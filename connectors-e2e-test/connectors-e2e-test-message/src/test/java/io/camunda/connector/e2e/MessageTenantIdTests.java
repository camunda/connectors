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
 * Covers the {@code tenantId} input of the send message connector against a cluster that enforces
 * multi-tenancy checks. The client of the test (and with it the connector runtime) is the CPT
 * multi-tenancy user, which this test assigns to {@link #TENANT_A} and {@link #TENANT_B} but never
 * to {@link #UNASSIGNED_TENANT}.
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
      "camunda.process-test.multi-tenancy-enabled=true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@SlowTest
@ComponentScan(basePackages = {"io.camunda.connector.runtime.app"})
public class MessageTenantIdTests {

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";
  private static final String UNASSIGNED_TENANT = "tenant-unassigned";
  private static final String DEFAULT_TENANT = "<default>";
  private static final String CLIENT_USER = "demo";

  private static final String MESSAGE_NAME = "tenant-message";
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
    createTenant(UNASSIGNED_TENANT);
    assignClientUser(TENANT_A);
    assignClientUser(TENANT_B);
  }

  @Test
  void publish_toTenantWithSubscription_isReceivedAndResponseCarriesTenant() {
    var receiver = startReceiver(TENANT_A);

    var sender = runSender("publish", TENANT_A).waitForProcessCompletion();

    assertThat(sender.getProcessInstanceEvent())
        .hasVariable("tenantIdResult", TENANT_A)
        .hasVariableNames("messageKeyResult");
    assertThat(receiver).isCompleted();
  }

  @Test
  void publish_toTenantWithoutSubscription_isNotCorrelated() {
    var receiverInOtherTenant = startReceiver(TENANT_B);
    var receiverInDefaultTenant = startReceiver(DEFAULT_TENANT);

    var sender = runSender("publish", TENANT_A).waitForProcessCompletion();

    assertThat(sender.getProcessInstanceEvent()).hasVariable("tenantIdResult", TENANT_A);
    assertSubscriptionsUncorrelated(receiverInOtherTenant, receiverInDefaultTenant);
  }

  @Test
  void correlate_toInstanceInTenant_isCorrelatedAndResponseCarriesTenant() {
    var receiver = startReceiver(TENANT_A);

    var sender = runSender("correlate", TENANT_A).waitForProcessCompletion();

    assertThat(sender.getProcessInstanceEvent())
        .hasVariable("tenantIdResult", TENANT_A)
        .hasVariable("processInstanceKeyResult", receiver.getProcessInstanceKey());
    assertThat(receiver).isCompleted();
  }

  @Test
  void correlate_toInstanceInOtherTenant_raisesIncident() {
    var receiverInOtherTenant = startReceiver(TENANT_B);
    var receiverInDefaultTenant = startReceiver(DEFAULT_TENANT);

    runSender("correlate", TENANT_A).waitForActiveIncidents();

    assertStaysActive(receiverInOtherTenant);
    assertStaysActive(receiverInDefaultTenant);
  }

  @ParameterizedTest
  @ValueSource(strings = {"publish", "correlate"})
  void unassignedTenant_raisesIncidentInsteadOfFallingBackToDefaultTenant(String mode) {
    var receiverInDefaultTenant = startReceiver(DEFAULT_TENANT);

    runSender(mode, UNASSIGNED_TENANT).waitForActiveIncidents();

    assertStaysActive(receiverInDefaultTenant);
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
        .untilAsserted(
            () ->
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

  /** Runs the send task in the default tenant, so only the {@code tenantId} input differs. */
  private ZeebeTest runSender(String mode, String tenantId) {
    BpmnModelInstance model =
        Bpmn.createExecutableProcess().startEvent().sendTask("sendTask").endEvent().done();
    var template =
        ElementTemplate.from(
                "../../connectors/camunda-message/element-templates/send-message-connector-send-task.json")
            .property("correlationType.type", mode)
            .property("messageName", MESSAGE_NAME)
            .property("correlationKey", CORRELATION_VALUE)
            .property("tenantId", tenantId)
            .property("retryCount", "1")
            .property("resultVariable", "messageResponse")
            .property(
                "resultExpression",
                "={messageKeyResult: messageKey, processInstanceKeyResult: processInstanceKey, tenantIdResult: tenantId}")
            .writeTo(new File(tempDir, "sendTaskTemplate.json"));
    var modelWithTemplate =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "message_send.bpmn"))
            .apply(template, "sendTask", new File(tempDir, "message_send_with_template.bpmn"));
    return ZeebeTest.with(camundaClient).deploy(modelWithTemplate).createInstance();
  }

  private void assertStaysActive(ProcessInstanceEvent instance) {
    Awaitility.with()
        .pollInSameThread()
        .await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(instance).isActive());
  }

  /**
   * Checks through the search API that the receivers still wait on their subscription and that the
   * message was correlated to no subscription at all. The {@code during} window bridges the
   * exporter lag, since a correlation that did happen is only visible once it is exported.
   */
  private void assertSubscriptionsUncorrelated(ProcessInstanceEvent... receivers) {
    Awaitility.with()
        .pollInSameThread()
        .await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              for (var receiver : receivers) {
                var subscriptions =
                    camundaClient
                        .newMessageSubscriptionSearchRequest()
                        .filter(f -> f.processInstanceKey(receiver.getProcessInstanceKey()))
                        .send()
                        .join()
                        .items();
                org.assertj.core.api.Assertions.assertThat(subscriptions)
                    .extracting(MessageSubscription::getMessageSubscriptionState)
                    .containsExactly(MessageSubscriptionState.CREATED);
              }
              var correlations =
                  camundaClient
                      .newCorrelatedMessageSubscriptionSearchRequest()
                      .filter(f -> f.messageName(MESSAGE_NAME))
                      .send()
                      .join()
                      .items();
              org.assertj.core.api.Assertions.assertThat(correlations).isEmpty();
            });
  }

  private void createTenant(String tenantId) {
    ignoringConflict(
        () ->
            camundaClient.newCreateTenantCommand().tenantId(tenantId).name(tenantId).send().join());
  }

  private void assignClientUser(String tenantId) {
    ignoringConflict(
        () ->
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
