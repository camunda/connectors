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
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.camunda.client.CamundaClient;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
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
import software.amazon.eventstream.HeaderValue;
import software.amazon.eventstream.MessageBuilder;

@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
class AgentCoreCodeInterpreterTests {
  private static final String TEMPLATE =
      "../../../connectors/aws/aws-bedrock-codeinterpreter/element-templates/aws-bedrock-codeinterpreter-outbound-connector.json";
  private static final String SESSION_PATH = "/code-interpreters/.*/sessions/start";
  private static final String STOP_PATH = "/code-interpreters/.*/sessions/stop.*";
  private static final String INVOKE_PATH = "/code-interpreters/.*/tools/invoke";

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
    aws.stubFor(
        put(urlMatching(SESSION_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"sessionId\":\"session-123\"}")));
    aws.stubFor(put(urlMatching(STOP_PATH)).willReturn(aResponse().withBody("{}")));
    stubInvoke("listFiles", "{\"content\":[]}");
    stubInvoke(
        "executeCode",
        "{\"structuredContent\":{\"stdout\":\"done\\n\",\"stderr\":\"\",\"exitCode\":0,\"executionTime\":42.5}}");
  }

  @Test
  void executesPython() {
    assertExecution("python", "print('done')", "done\n", "", 0, 42.5);
  }

  @Test
  void executesJavaScript() {
    assertExecution("javascript", "console.log('done')", "done\n", "", 0, 42.5);
  }

  @Test
  void executesTypeScript() {
    assertExecution("typescript", "console.log('done')", "done\n", "", 0, 42.5);
  }

  @Test
  void returnsGeneratedFile() {
    aws.stubFor(
        post(urlMatching(INVOKE_PATH))
            .withRequestBody(
                com.github.tomakehurst.wiremock.client.WireMock.containing(
                    "\"name\":\"listFiles\""))
            .inScenario("generated-file")
            .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
            .willReturn(stream("{\"content\":[]}"))
            .willSetStateTo("after-execution"));
    aws.stubFor(
        post(urlMatching(INVOKE_PATH))
            .withRequestBody(
                com.github.tomakehurst.wiremock.client.WireMock.containing(
                    "\"name\":\"listFiles\""))
            .inScenario("generated-file")
            .whenScenarioStateIs("after-execution")
            .willReturn(stream("{\"content\":[{\"name\":\"report.txt\"}]}")));
    stubInvoke(
        "readFiles",
        "{\"content\":[{\"name\":\"report.txt\",\"resource\":{\"mimeType\":\"text/plain\",\"text\":\"generated output\"}}]}");

    var instance = run("python", "open('report.txt','w').write('generated output')", null);
    CamundaAssert.assertThat(instance.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "result",
            Map.class,
            result -> {
              var files = (List<?>) result.get("files");
              assertThat(files).hasSize(1);
              assertThat(files.getFirst()).asString().contains("report.txt");
            });
    aws.verify(
        postRequestedFor(urlMatching(INVOKE_PATH))
            .withRequestBody(
                com.github.tomakehurst.wiremock.client.WireMock.containing(
                    "\"name\":\"readFiles\"")));
  }

  @Test
  void invalidInterpreterIdCreatesIncident() {
    aws.stubFor(
        put(urlMatching("/code-interpreters/invalid-interpreter/sessions/start"))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("x-amzn-errortype", "ResourceNotFoundException")
                    .withBody("{\"message\":\"Code Interpreter not found\"}")));

    var instance =
        ZeebeTest.with(camundaClient)
            .deploy(model("python", "print('done')", "invalid-interpreter"))
            .createInstance()
            .waitForActiveIncidents(Duration.ofSeconds(60));
    CamundaAssert.assertThat(instance.getProcessInstanceEvent()).hasActiveIncidents();
    var incidents =
        camundaClient
            .newIncidentSearchRequest()
            .filter(
                f ->
                    f.processInstanceKey(
                        instance.getProcessInstanceEvent().getProcessInstanceKey()))
            .send()
            .join();
    assertThat(incidents.items())
        .hasSize(1)
        .first()
        .satisfies(i -> assertThat(i.getErrorMessage()).contains("CODE_INTERPRETER_FAILED"));
    aws.verify(
        anyRequestedFor(urlMatching("/code-interpreters/invalid-interpreter/sessions/start")));
  }

  private void assertExecution(
      String language, String code, String stdout, String stderr, int exitCode, double duration) {
    var instance = run(language, code, null);
    CamundaAssert.assertThat(instance.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "result",
            Map.class,
            result -> {
              assertThat(result).containsEntry("stdout", stdout).containsEntry("stderr", stderr);
              assertThat(((Number) result.get("exitCode")).intValue()).isEqualTo(exitCode);
              assertThat(((Number) result.get("executionTimeMs")).doubleValue())
                  .isEqualTo(duration);
              assertThat(result.get("files")).isEqualTo(List.of());
            });
    aws.verify(
        postRequestedFor(urlMatching(INVOKE_PATH))
            .withRequestBody(
                com.github.tomakehurst.wiremock.client.WireMock.containing(
                    "\"language\":\"" + language + "\"")));
    aws.verify(
        postRequestedFor(urlMatching(INVOKE_PATH))
            .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing(code)));
    aws.verify(anyRequestedFor(urlMatching(STOP_PATH)));
  }

  private ZeebeTest run(String language, String code, String identifier) {
    return ZeebeTest.with(camundaClient)
        .deploy(model(language, code, identifier))
        .createInstance()
        .waitForProcessCompletion(Duration.ofSeconds(60));
  }

  private io.camunda.zeebe.model.bpmn.BpmnModelInstance model(
      String language, String code, String identifier) {
    var model =
        Bpmn.createProcess("code-interpreter-" + UUID.randomUUID())
            .executable()
            .startEvent()
            .serviceTask("interpret")
            .endEvent()
            .done();
    var template =
        ElementTemplate.from(TEMPLATE)
            .property("authentication.type", "credentials")
            .property("authentication.accessKey", "test-access-key")
            .property("authentication.secretKey", "test-secret-key")
            .property("configuration.region", "us-east-1")
            .property("configuration.endpoint", aws.baseUrl())
            .property("input.language", language)
            .property("input.code", code)
            .property("resultVariable", "result")
            .property("retryCount", "0");
    if (identifier != null) {
      template.property("input.codeInterpreterIdentifier", identifier);
    }
    var suffix = UUID.randomUUID().toString();
    return new BpmnFile(model)
        .writeToFile(new File(tempDir, suffix + "-input.bpmn"))
        .apply(
            template.writeTo(new File(tempDir, suffix + ".json")),
            "interpret",
            new File(tempDir, suffix + "-output.bpmn"));
  }

  private void stubInvoke(String name, String result) {
    aws.stubFor(
        post(urlMatching(INVOKE_PATH))
            .withRequestBody(
                com.github.tomakehurst.wiremock.client.WireMock.containing(
                    "\"name\":\"" + name + "\""))
            .willReturn(stream(result)));
  }

  private com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder stream(String json) {
    var bytes = new ByteArrayOutputStream();
    MessageBuilder.defaultBuilder()
        .build(
            Map.of(
                ":message-type", HeaderValue.fromString("event"),
                ":event-type", HeaderValue.fromString("result"),
                ":content-type", HeaderValue.fromString("application/json")),
            json.getBytes(StandardCharsets.UTF_8))
        .encode(bytes);
    return aResponse()
        .withHeader("Content-Type", "application/vnd.amazon.eventstream")
        .withBody(bytes.toByteArray());
  }
}
