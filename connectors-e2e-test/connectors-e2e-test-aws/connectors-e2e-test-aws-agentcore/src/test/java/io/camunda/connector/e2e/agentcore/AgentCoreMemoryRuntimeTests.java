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
package io.camunda.connector.e2e.agentcore;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.camunda.client.CamundaClient;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
class AgentCoreMemoryRuntimeTests {
  private static final String MEMORY_TEMPLATE =
      "../../../connectors/aws/aws-bedrock-agentcore-long-term-memory/element-templates/aws-bedrock-agentcore-long-term-memory-outbound-connector.json";
  private static final String RUNTIME_TEMPLATE =
      "../../../connectors/aws/aws-bedrock-agentcore-runtime/element-templates/aws-bedrock-agentcore-runtime-outbound-connector.json";
  private static final String RUNTIME_ARN =
      "arn:aws:bedrock-agentcore:us-east-1:123456789012:runtime/test-agent";

  private static WireMockServer aws;

  @Autowired private CamundaClient camundaClient;
  @TempDir File tempDir;

  @BeforeAll
  static void startAwsDouble() {
    aws = new WireMockServer(0);
    aws.start();
  }

  @AfterAll
  static void stopAwsDouble() {
    aws.stop();
  }

  @BeforeEach
  void resetAwsDouble() {
    aws.resetAll();
  }

  @Test
  void retrievesRecordFromNamespace() {
    aws.stubFor(
        post(urlMatching(".*/retrieve.*"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"memoryRecordSummaries":[{"memoryRecordId":"record-1","content":{"text":"Prefers email"},"memoryStrategyId":"strategy-1","namespaces":["customer/one"],"score":0.9,"metadata":{"source":{"stringValue":"conversation"}}}]}
                        """)));

    var instance =
        run(
            model("retrieve"),
            "retrieve",
            memoryTemplate("retrieve", "customer/one", "memory-1", null, "result"));

    io.camunda.process.test.api.CamundaAssert.assertThat(instance.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "result",
            Map.class,
            result -> {
              assertThat(result).containsEntry("resultCount", 1);
              var records = (List<?>) result.get("records");
              assertThat(records).hasSize(1);
              assertThat(((Map<?, ?>) records.getFirst()).get("memoryRecordId"))
                  .isEqualTo("record-1");
              assertThat(((Map<?, ?>) records.getFirst()).get("content"))
                  .isEqualTo("Prefers email");
            });
    aws.verify(
        postRequestedFor(urlMatching(".*/retrieve.*")).withRequestBody(containing("customer/one")));
  }

  @Test
  void listsIsolatedRecordsAcrossPages() {
    aws.stubFor(
        any(urlMatching(".*"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"memoryRecordSummaries\":[]}")));
    aws.stubFor(
        any(urlMatching(".*"))
            .withRequestBody(containing("customer/two"))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"memoryRecordSummaries":[{"memoryRecordId":"record-2","content":{"text":"Only customer two"},"namespaces":["customer/two"]},{"memoryRecordId":"record-3","content":{"text":"Also customer two"},"namespaces":["customer/two"]}],"nextToken":"page-two"}
                        """)));

    var first =
        run(
            model("list"),
            "list",
            memoryTemplate("list", "customer/two", "memory-1", null, "result"));
    io.camunda.process.test.api.CamundaAssert.assertThat(first.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "result",
            Map.class,
            result -> {
              assertThat(result)
                  .containsEntry("resultCount", 2)
                  .containsEntry("nextToken", "page-two");
              var records = (List<?>) result.get("records");
              assertThat(records).hasSize(2);
              assertThat(((Map<?, ?>) records.get(0)).get("content"))
                  .isEqualTo("Only customer two");
              assertThat(((Map<?, ?>) records.get(1)).get("content"))
                  .isEqualTo("Also customer two");
            });

    aws.stubFor(
        any(urlMatching(".*"))
            .withRequestBody(containing("page-two"))
            .atPriority(0)
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"memoryRecordSummaries":[{"memoryRecordId":"record-4","content":{"text":"Last page"},"namespaces":["customer/two"]}]}
                        """)));
    var second =
        run(
            model("list"),
            "list",
            memoryTemplate("list", "customer/two", "memory-1", "page-two", "result"));
    io.camunda.process.test.api.CamundaAssert.assertThat(second.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "result",
            Map.class,
            result -> {
              assertThat(result).containsEntry("resultCount", 1);
              assertThat(
                      ((Map<?, ?>) ((List<?>) result.get("records")).getFirst())
                          .get("memoryRecordId"))
                  .isEqualTo("record-4");
            });
    aws.verify(anyRequestedFor(urlMatching(".*")).withRequestBody(containing("page-two")));
    var otherNamespace =
        run(
            model("list"),
            "list",
            memoryTemplate("list", "customer/other", "memory-1", null, "result"));
    io.camunda.process.test.api.CamundaAssert.assertThat(otherNamespace.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "result",
            Map.class,
            result -> {
              assertThat(result).containsEntry("resultCount", 0);
              assertThat((List<?>) result.get("records")).isEmpty();
            });
    aws.verify(anyRequestedFor(urlMatching(".*")).withRequestBody(containing("customer/other")));
  }

  @Test
  void invalidMemoryIdCreatesIncident() {
    aws.stubFor(
        any(urlMatching(".*"))
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("x-amzn-errortype", "ResourceNotFoundException")
                    .withBody("{\"message\":\"Memory resource not found\"}")));
    var instance =
        ZeebeTest.with(camundaClient)
            .deploy(
                apply(
                    model("retrieve"),
                    "retrieve",
                    memoryTemplate("retrieve", "customer/one", "invalid-id", null, "result")))
            .createInstance()
            .waitForActiveIncidents(Duration.ofSeconds(60));
    assertIncident(instance, "AGENTCORE_MEMORY_FAILED");
    aws.verify(anyRequestedFor(urlMatching(".*")));
  }

  @Test
  void invokesAgentAndReusesReturnedSession() {
    aws.stubFor(
        post(urlMatching(".*"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withHeader("x-amzn-bedrock-agentcore-runtime-session-id", "session-123")
                    .withBody("{\"reply\":\"Hello\"}")));
    aws.stubFor(
        post(urlMatching(".*"))
            .withHeader("x-amzn-bedrock-agentcore-runtime-session-id", equalTo("session-123"))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withHeader("x-amzn-bedrock-agentcore-runtime-session-id", "session-123")
                    .withBody("{\"reply\":\"Welcome back\"}")));
    var model =
        Bpmn.createProcess("agentcore-" + UUID.randomUUID())
            .executable()
            .startEvent()
            .serviceTask("first")
            .serviceTask("second")
            .endEvent()
            .done();
    var first = runtimeTemplate(RUNTIME_ARN, null, "firstResult");
    model = apply(model, "first", first);
    model =
        apply(
            model,
            "second",
            runtimeTemplate(RUNTIME_ARN, "=firstResult.sessionId", "secondResult"));

    var instance =
        ZeebeTest.with(camundaClient)
            .deploy(model)
            .createInstance()
            .waitForProcessCompletion(Duration.ofSeconds(60));
    io.camunda.process.test.api.CamundaAssert.assertThat(instance.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "firstResult",
            Map.class,
            result -> {
              assertThat(result)
                  .containsEntry("sessionId", "session-123")
                  .containsEntry("statusCode", 200);
              assertThat(((Map<?, ?>) result.get("response")).get("reply")).isEqualTo("Hello");
            });
    io.camunda.process.test.api.CamundaAssert.assertThat(instance.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "secondResult",
            Map.class,
            result -> {
              assertThat(result)
                  .containsEntry("sessionId", "session-123")
                  .containsEntry("statusCode", 200);
              assertThat(((Map<?, ?>) result.get("response")).get("reply"))
                  .isEqualTo("Welcome back");
            });
    aws.verify(2, postRequestedFor(urlMatching(".*")));
    aws.verify(
        postRequestedFor(urlMatching(".*"))
            .withHeader("x-amzn-bedrock-agentcore-runtime-session-id", equalTo("session-123")));
  }

  @Test
  void invalidRuntimeArnCreatesIncident() {
    aws.stubFor(
        any(urlMatching(".*"))
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("x-amzn-errortype", "ResourceNotFoundException")
                    .withBody("{\"message\":\"Agent runtime not found\"}")));
    var instance =
        ZeebeTest.with(camundaClient)
            .deploy(
                apply(
                    model("invoke"),
                    "invoke",
                    runtimeTemplate(RUNTIME_ARN + "-missing", null, "result")))
            .createInstance()
            .waitForActiveIncidents(Duration.ofSeconds(60));
    assertIncident(instance, "AGENTCORE_RUNTIME_FAILED");
    aws.verify(anyRequestedFor(urlMatching(".*")));
  }

  private BpmnModelInstance model(String task) {
    return Bpmn.createProcess("agentcore-" + UUID.randomUUID())
        .executable()
        .startEvent()
        .serviceTask(task)
        .endEvent()
        .done();
  }

  private ElementTemplate memoryTemplate(
      String operation,
      String namespace,
      String memoryId,
      String nextToken,
      String resultVariable) {
    var template =
        commonTemplate(MEMORY_TEMPLATE)
            .property("operation.operationDiscriminator", operation)
            .property("memoryId", memoryId)
            .property("namespace", namespace)
            .property("resultVariable", resultVariable)
            .property("retryCount", "0");
    if ("retrieve".equals(operation)) {
      template.property("operation.query", "preferences");
    } else if (nextToken != null) {
      template.property("operation.list.nextToken", nextToken);
    }
    return template;
  }

  private ElementTemplate runtimeTemplate(String arn, String sessionId, String resultVariable) {
    var template =
        commonTemplate(RUNTIME_TEMPLATE)
            .property("input.agentRuntimeArn", arn)
            .property("input.payload", "={inputText: \"Hello\"}")
            .property("resultVariable", resultVariable)
            .property("retryCount", "0");
    if (sessionId != null) {
      template.property("input.sessionId", sessionId);
    }
    return template;
  }

  private ElementTemplate commonTemplate(String path) {
    return ElementTemplate.from(path)
        .property("authentication.type", "credentials")
        .property("authentication.accessKey", "test-access-key")
        .property("authentication.secretKey", "test-secret-key")
        .property("configuration.region", "us-east-1")
        .property("configuration.endpoint", aws.baseUrl());
  }

  private BpmnModelInstance apply(BpmnModelInstance model, String task, ElementTemplate template) {
    var suffix = UUID.randomUUID().toString();
    return new BpmnFile(model)
        .writeToFile(new File(tempDir, suffix + "-input.bpmn"))
        .apply(
            template.writeTo(new File(tempDir, suffix + ".json")),
            task,
            new File(tempDir, suffix + "-output.bpmn"));
  }

  private ZeebeTest run(BpmnModelInstance model, String task, ElementTemplate template) {
    return ZeebeTest.with(camundaClient)
        .deploy(apply(model, task, template))
        .createInstance()
        .waitForProcessCompletion(Duration.ofSeconds(60));
  }

  private void assertIncident(ZeebeTest instance, String errorCode) {
    io.camunda.process.test.api.CamundaAssert.assertThat(instance.getProcessInstanceEvent())
        .hasActiveIncidents();
    var incidents =
        camundaClient
            .newIncidentSearchRequest()
            .filter(
                filter ->
                    filter.processInstanceKey(
                        instance.getProcessInstanceEvent().getProcessInstanceKey()))
            .send()
            .join();
    assertThat(incidents.items())
        .hasSize(1)
        .first()
        .satisfies(incident -> assertThat(incident.getErrorMessage()).contains(errorCode));
  }
}
