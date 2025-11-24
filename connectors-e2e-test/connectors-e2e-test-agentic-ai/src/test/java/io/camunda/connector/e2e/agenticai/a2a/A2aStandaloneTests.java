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
package io.camunda.connector.e2e.agenticai.a2a;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static io.camunda.connector.e2e.BpmnFile.Replace.replace;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aAgentCard;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aClientResponse;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTaskStatus;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.BaseAgenticAiTest;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections4.MapUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;

@SlowTest
@TestPropertySource(
    properties = {
      "camunda.connector.polling.enabled=true",
      "camunda.connector.webhook.enabled=true"
    })
@WireMockTest
public class A2aStandaloneTests extends BaseAgenticAiTest {

  @Value("classpath:a2a-connectors-standalone.bpmn")
  protected Resource testProcess;

  @LocalServerPort private int port;

  private WireMockRuntimeInfo wireMock;
  private String webhookUrl;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMock) {
    this.wireMock = wireMock;
    WireMock.reset();
    webhookUrl = "http://localhost:%s/inbound/test-webhook-id".formatted(port);
    setUpWireMockStubs();
  }

  @Test
  void executeStandaloneA2aConnectorsPolling() throws IOException {
    BpmnModelInstance bpmnModel = Bpmn.readModelFromStream(testProcess.getInputStream());
    ZeebeTest zeebeTest =
        createProcessInstance(
                bpmnModel,
                Map.of(
                    "responseRetrievalMode", "polling", "a2aServerUrl", wireMock.getHttpBaseUrl()))
            .waitForProcessCompletion();

    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "travelAgentFetchCardResponse",
            A2aClientResponse.class,
            A2aStandaloneTests::assertAgentCard)
        .hasVariableSatisfies(
            "travelAgentPollingResponse", A2aTask.class, A2aStandaloneTests::assertTask);
  }

  @Test
  void executeStandaloneA2aConnectorsWebhook() throws Exception {
    BpmnModelInstance bpmnModel = Bpmn.readModelFromStream(testProcess.getInputStream());
    ZeebeTest zeebeTest =
        createProcessInstance(
            bpmnModel,
            Map.of(
                "responseRetrievalMode",
                "webhook",
                "a2aServerUrl",
                wireMock.getHttpBaseUrl(),
                "webhookUrl",
                webhookUrl));

    // Post working state - should NOT activate webhook
    callWebhookEndpointWithDelay(
        webhookUrl,
        extractTaskFromJsonRpc(testFileContent("travel-agent-response-working.json").get()),
        5);

    // Post completed state - should activate webhook
    callWebhookEndpointWithDelay(
        webhookUrl,
        extractTaskFromJsonRpc(testFileContent("travel-agent-response-completed.json").get()),
        10);

    zeebeTest.waitForProcessCompletion();

    assertVariablesWithWebhook(zeebeTest);
  }

  @Test
  void executeStandaloneA2aConnectorsWebhookWithToken() throws Exception {
    var token = "test-token-123";
    BpmnModelInstance bpmnModel =
        BpmnFile.replace(
            testProcess.getFile(), replace("id=\"A2A_Standalone\"", "id=\"A2A_Standalone_Token\""));
    ZeebeTest zeebeTest =
        createProcessInstance(
            bpmnModel,
            Map.of(
                "responseRetrievalMode",
                "webhook",
                "a2aServerUrl",
                wireMock.getHttpBaseUrl(),
                "webhookUrl",
                webhookUrl,
                "token",
                token));

    // Post working state - should NOT activate webhook
    callWebhookEndpointWithDelay(
        webhookUrl,
        extractTaskFromJsonRpc(testFileContent("travel-agent-response-working.json").get()),
        Map.of("X-A2A-Notification-Token", token),
        5);

    // Post completed state with invalid token - should NOT activate webhook
    callWebhookEndpointWithDelay(
        webhookUrl,
        extractTaskFromJsonRpc(
            testFileContent("travel-agent-response-completed.json")
                .get()
                .replaceAll("ctx-001", "ctx-002")),
        Map.of("X-A2A-Notification-Token", "invalid-token"),
        7);

    // Post completed state - should activate webhook
    callWebhookEndpointWithDelay(
        webhookUrl,
        extractTaskFromJsonRpc(testFileContent("travel-agent-response-completed.json").get()),
        Map.of("X-A2A-Notification-Token", token),
        10);

    zeebeTest.waitForProcessCompletion();

    assertVariablesWithWebhook(zeebeTest);

    WireMock.verify(
        postRequestedFor(urlPathEqualTo("/travel-agent"))
            .withRequestBody(WireMock.containing("message/send"))
            .withRequestBody(WireMock.containing(token)));
  }

  @Test
  void executeStandaloneA2aConnectorsWebhookWithBasicAuthentication() throws Exception {
    var authHeaders = Map.of("Authorization", "Basic dXNlcjE6cGFzczEyMw==");
    BpmnModelInstance bpmnModel =
        BpmnFile.replace(
            testProcess.getFile(),
            replace("id=\"A2A_Standalone\"", "id=\"A2A_Standalone_BasicAuth\""),
            replace(
                "<zeebe:property name=\"inbound.auth.type\" value=\"NONE\" />",
                """
            <zeebe:property name="inbound.auth.type" value="BASIC" />
            <zeebe:property name="inbound.auth.username" value="user1" />
            <zeebe:property name="inbound.auth.password" value="pass123" />"""));
    ZeebeTest zeebeTest =
        createProcessInstance(
            bpmnModel,
            Map.of(
                "responseRetrievalMode",
                "webhook",
                "a2aServerUrl",
                wireMock.getHttpBaseUrl(),
                "webhookUrl",
                webhookUrl));

    // Post working state - should NOT activate webhook
    callWebhookEndpointWithDelay(
        webhookUrl,
        extractTaskFromJsonRpc(testFileContent("travel-agent-response-working.json").get()),
        authHeaders,
        5);

    // Post completed state with invalid credentials - should NOT activate webhook
    callWebhookEndpointWithDelay(
        webhookUrl,
        extractTaskFromJsonRpc(
            testFileContent("travel-agent-response-completed.json")
                .get()
                .replaceAll("ctx-001", "ctx-002")),
        Map.of("Authorization", "Basic dXNlcjI6cGFzczMyMQo="),
        7);

    // Post completed state - should activate webhook
    callWebhookEndpointWithDelay(
        webhookUrl,
        extractTaskFromJsonRpc(testFileContent("travel-agent-response-completed.json").get()),
        authHeaders,
        10);

    zeebeTest.waitForProcessCompletion();

    assertVariablesWithWebhook(zeebeTest);
  }

  @Test
  void executeStandaloneA2aConnectorsWebhookWithHmacVerification() throws Exception {
    BpmnModelInstance bpmnModel =
        BpmnFile.replace(
            testProcess.getFile(),
            replace("id=\"A2A_Standalone\"", "id=\"A2A_Standalone_HMAC\""),
            replace(
                "<zeebe:property name=\"inbound.shouldValidateHmac\" value=\"disabled\"",
                """
            <zeebe:property name="inbound.shouldValidateHmac" value="enabled" />
            <zeebe:property name="inbound.hmacSecret" value="my-secret-key" />
            <zeebe:property name="inbound.hmacHeader" value="X-HMAC-Signature" />
            <zeebe:property name="inbound.hmacAlgorithm" value="sha_256" />"""));
    ZeebeTest zeebeTest =
        createProcessInstance(
            bpmnModel,
            Map.of(
                "responseRetrievalMode",
                "webhook",
                "a2aServerUrl",
                wireMock.getHttpBaseUrl(),
                "webhookUrl",
                webhookUrl));

    // Post working state - should NOT activate webhook
    callWebhookEndpointWithDelay(
        webhookUrl,
        extractTaskFromJsonRpc(testFileContent("travel-agent-response-working.json").get()),
        Map.of(
            "X-HMAC-Signature", "d2515228699764ba7e6df716539d1ddabbb8cc329e99fc70448c2753cc37bd92"),
        5);

    // Post completed state with invalid HMAC signature - should NOT activate webhook
    callWebhookEndpointWithDelay(
        webhookUrl,
        extractTaskFromJsonRpc(
            testFileContent("travel-agent-response-completed.json")
                .get()
                .replaceAll("ctx-001", "ctx-002")),
        Map.of(
            "X-HMAC-Signature", "d2515228699764ba7e6df716539d1ddabbb8cc329e99fc70448c2753cc37bd92"),
        7);

    // Post completed state - should activate webhook
    callWebhookEndpointWithDelay(
        webhookUrl,
        extractTaskFromJsonRpc(testFileContent("travel-agent-response-completed.json").get()),
        Map.of(
            "X-HMAC-Signature", "1fe75a1c849df2aacb18952f187938b64edff510f341c9ab55df03783aee85a0"),
        10);

    zeebeTest.waitForProcessCompletion();

    assertVariablesWithWebhook(zeebeTest);
  }

  private static void assertVariablesWithWebhook(ZeebeTest zeebeTest) {
    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "travelAgentFetchCardResponse",
            A2aClientResponse.class,
            A2aStandaloneTests::assertAgentCard)
        .hasVariableSatisfies(
            "travelAgentWebhookResponse", A2aTask.class, A2aStandaloneTests::assertTask);
  }

  private static void assertTask(A2aTask task) {
    assertThat(task.status().state()).isEqualTo(A2aTaskStatus.TaskState.COMPLETED);
    assertThat(task.id()).isEqualTo("task-001");
    assertThat(task.contextId()).isEqualTo("ctx-001");
    assertThat(task.artifacts()).isNotEmpty();
    assertThat(task.history()).hasSize(3);
  }

  private static void assertAgentCard(A2aClientResponse response) {
    Assertions.assertThat(response.pollingData()).isNotNull();
    Assertions.assertThat(response.pollingData().id()).isNotBlank();

    Assertions.assertThat(response.result().kind()).isEqualTo("agentCard");
    Assertions.assertThat(response.result()).isInstanceOf(A2aAgentCard.class);
    var a2aAgentCard = (A2aAgentCard) response.result();
    assertThat(a2aAgentCard.skills()).isNotEmpty();
  }

  private void setUpWireMockStubs() {
    stubFor(
        get(urlPathEqualTo("/.well-known/agent-card.json"))
            .willReturn(
                aResponse()
                    .withBody(
                        testFileContent("travel-agent-card.json")
                            .get()
                            .formatted(wireMock.getHttpBaseUrl()))));

    stubFor(
        post(urlPathEqualTo("/travel-agent"))
            .withRequestBody(WireMock.containing("message/send"))
            .willReturn(
                aResponse()
                    .withBody(testFileContent("travel-agent-response-submitted.json").get())));

    stubFor(
        post(urlPathEqualTo("/travel-agent"))
            .inScenario("polling")
            .whenScenarioStateIs(STARTED)
            .withRequestBody(WireMock.containing("tasks/get"))
            .willReturn(
                aResponse().withBody(testFileContent("travel-agent-response-working.json").get()))
            .willSetStateTo("complete-task"));

    stubFor(
        post(urlPathEqualTo("/travel-agent"))
            .inScenario("polling")
            .whenScenarioStateIs("complete-task")
            .withRequestBody(WireMock.containing("tasks/get"))
            .willReturn(
                aResponse()
                    .withBody(testFileContent("travel-agent-response-completed.json").get())));
  }

  private void callWebhookEndpointWithDelay(
      String webhookUrl, String payload, long delayInSeconds) {
    callWebhookEndpointWithDelay(webhookUrl, payload, Map.of(), delayInSeconds);
  }

  private void callWebhookEndpointWithDelay(
      String webhookUrl, String payload, Map<String, String> headers, long delayInSeconds) {
    CompletableFuture.delayedExecutor(delayInSeconds, TimeUnit.SECONDS)
        .execute(
            () -> {
              try (var client = HttpClient.newHttpClient()) {
                HttpRequest.Builder builder =
                    HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload));
                if (MapUtils.isNotEmpty(headers)) {
                  headers.forEach(builder::header);
                }
                var request = builder.build();
                client.send(request, HttpResponse.BodyHandlers.discarding());
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  private String extractTaskFromJsonRpc(String jsonRpcResponse) throws Exception {
    var root = objectMapper.readTree(jsonRpcResponse);
    return objectMapper.writeValueAsString(root.get("result"));
  }
}
