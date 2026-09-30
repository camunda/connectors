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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.document.CamundaDocumentReferenceImpl;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.secretprovider.environment.prefix="
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
class RealAgentCoreCptIT {
  private static final String ROOT = "../../../connectors/aws/";
  private static final String RUNTIME_TEMPLATE =
      ROOT
          + "aws-bedrock-agentcore-runtime/element-templates/aws-bedrock-agentcore-runtime-outbound-connector.json";
  private static final String CODE_TEMPLATE =
      ROOT
          + "aws-bedrock-codeinterpreter/element-templates/aws-bedrock-codeinterpreter-outbound-connector.json";
  private static final String MEMORY_TEMPLATE =
      ROOT
          + "aws-bedrock-agentcore-long-term-memory/element-templates/aws-bedrock-agentcore-long-term-memory-outbound-connector.json";

  @Autowired private CamundaClient camundaClient;
  @Autowired private DocumentFactory documentFactory;
  @TempDir File tempDir;

  @Test
  void runtimeReusesRealSessionAcrossTwoTurns() {
    required("AGENTCORE_AGENT_RUNTIME_ARN");
    var model =
        Bpmn.createProcess("real-agentcore-runtime-" + UUID.randomUUID())
            .executable()
            .startEvent()
            .serviceTask("first")
            .serviceTask("second")
            .endEvent()
            .done();
    model =
        apply(
            model,
            "first",
            runtimeTemplate("{{secrets.AGENTCORE_AGENT_RUNTIME_ARN}}", null, "firstResult"));
    model =
        apply(
            model,
            "second",
            runtimeTemplate(
                "{{secrets.AGENTCORE_AGENT_RUNTIME_ARN}}",
                "=firstResult.sessionId",
                "secondResult"));

    var instance =
        ZeebeTest.with(camundaClient)
            .deploy(model)
            .createInstance()
            .waitForProcessCompletion(Duration.ofMinutes(3));
    var sessionId = new AtomicReference<String>();
    CamundaAssert.assertThat(instance.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "firstResult",
            Map.class,
            result -> {
              assertThat(result.get("statusCode")).isEqualTo(200);
              assertThat(result.get("response")).isNotNull();
              assertThat(result.get("response").toString()).containsIgnoringCase("Paris");
              assertThat(result.get("sessionId")).as("first turn session ID").isNotNull();
              assertThat(result.get("sessionId").toString()).isNotBlank();
              sessionId.set(result.get("sessionId").toString());
            });
    CamundaAssert.assertThat(instance.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "secondResult",
            Map.class,
            result -> {
              assertThat(result.get("statusCode")).isEqualTo(200);
              assertThat(result.get("response")).isNotNull();
              assertThat(result.get("response").toString()).containsIgnoringCase("France");
              assertThat(result.get("sessionId")).isEqualTo(sessionId.get());
            });
  }

  @ParameterizedTest(name = "Code Interpreter - {0} execution")
  @ValueSource(strings = {"python", "javascript", "typescript"})
  void codeInterpreterExecutesLanguage(String language) {
    var marker = "agentcore-cpt-" + UUID.randomUUID();
    var code =
        language.equals("python") ? "print('" + marker + "')" : "console.log('" + marker + "')";
    var instance = run("code", codeTemplate(language, code, null));
    CamundaAssert.assertThat(instance.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "result",
            Map.class,
            result -> {
              assertThat(result.get("stdout")).as(language + " stdout").isEqualTo(marker + "\n");
              assertThat(result.get("stderr")).as(language + " stderr").isEqualTo("");
              assertThat(((Number) result.get("exitCode")).intValue()).isZero();
              assertThat(((Number) result.get("executionTimeMs")).doubleValue())
                  .isGreaterThanOrEqualTo(0);
            });
  }

  @Test
  void codeInterpreterReturnsGeneratedDocument() {
    var marker = "agentcore-cpt-" + UUID.randomUUID();
    var instance =
        run(
            "code",
            codeTemplate(
                "python",
                "with open('agentcore-cpt.txt', 'w') as f: f.write('" + marker + "')",
                null));
    CamundaAssert.assertThat(instance.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "result",
            Map.class,
            result -> {
              assertThat(((Number) result.get("exitCode")).intValue()).isZero();
              var files = (List<?>) result.get("files");
              assertThat(files).as("AWS-generated files").isNotEmpty();
              assertThat(files)
                  .anySatisfy(
                      file -> {
                        var document = (Map<?, ?>) file;
                        assertThat(document.get("documentId")).isNotNull();
                        assertThat(((Map<?, ?>) document.get("metadata")).get("fileName"))
                            .isEqualTo("agentcore-cpt.txt");
                        var stored =
                            documentFactory.resolve(
                                new CamundaDocumentReferenceImpl(
                                    document.get("storeId").toString(),
                                    document.get("documentId").toString(),
                                    document.get("contentHash").toString(),
                                    null));
                        assertThat(new String(stored.asByteArray(), StandardCharsets.UTF_8))
                            .isEqualTo(marker);
                      });
            });
  }

  @Test
  void memoryRetrievesExpectedSeededRecord() {
    var expectedId = required("AGENTCORE_LTM_EXPECTED_RECORD_ID");
    var expectedContent = required("AGENTCORE_LTM_EXPECTED_RECORD_CONTENT");
    required("AGENTCORE_LTM_MEMORY_ID");
    var instance =
        run(
            "memory",
            memoryTemplate(
                "retrieve",
                "{{secrets.AGENTCORE_LTM_MEMORY_ID}}",
                "{{secrets.AGENTCORE_LTM_EXPECTED_RECORD_CONTENT}}",
                "result",
                null));
    assertExpectedRecord(instance, expectedId, expectedContent);
  }

  @Test
  void memoryListsTwoSeededRecordsAcrossPages() {
    var firstId = required("AGENTCORE_LTM_EXPECTED_RECORD_ID");
    var firstContent = required("AGENTCORE_LTM_EXPECTED_RECORD_CONTENT");
    var secondId = required("AGENTCORE_LTM_EXPECTED_SECOND_RECORD_ID");
    var secondContent = required("AGENTCORE_LTM_EXPECTED_SECOND_RECORD_CONTENT");
    assertThat(secondId).isNotEqualTo(firstId);
    required("AGENTCORE_LTM_MEMORY_ID");
    var model =
        Bpmn.createProcess("real-agentcore-memory-" + UUID.randomUUID())
            .executable()
            .startEvent()
            .serviceTask("first")
            .serviceTask("second")
            .endEvent()
            .done();
    model =
        apply(
            model,
            "first",
            memoryTemplate(
                "list", "{{secrets.AGENTCORE_LTM_MEMORY_ID}}", null, "firstResult", null));
    model =
        apply(
            model,
            "second",
            memoryTemplate(
                "list",
                "{{secrets.AGENTCORE_LTM_MEMORY_ID}}",
                null,
                "secondResult",
                "=firstResult.nextToken"));
    var instance =
        ZeebeTest.with(camundaClient)
            .deploy(model)
            .createInstance()
            .waitForProcessCompletion(Duration.ofMinutes(3));
    var firstPage = new AtomicReference<Map<?, ?>>();
    var secondPage = new AtomicReference<Map<?, ?>>();
    CamundaAssert.assertThat(instance.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "firstResult",
            Map.class,
            result -> {
              assertThat(((Number) result.get("resultCount")).intValue()).isEqualTo(1);
              assertThat((String) result.get("nextToken"))
                  .as("real AWS pagination token")
                  .isNotBlank();
              firstPage.set(result);
            });
    CamundaAssert.assertThat(instance.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "secondResult",
            Map.class,
            result -> {
              assertThat(((Number) result.get("resultCount")).intValue()).isEqualTo(1);
              secondPage.set(result);
            });
    var records = new java.util.ArrayList<Map<?, ?>>();
    for (var page : List.of(firstPage.get(), secondPage.get())) {
      for (var record : (List<?>) page.get("records")) {
        records.add((Map<?, ?>) record);
      }
    }
    assertThat(records).hasSize(2);
    assertRecord(records, firstId, firstContent);
    assertRecord(records, secondId, secondContent);
  }

  @Test
  void invalidMemoryIdCreatesIncident() {
    required("AGENTCORE_LTM_MEMORY_ID");
    var instance =
        runUntilIncident(
            "memory",
            memoryTemplate(
                "retrieve", "nonexistent-" + UUID.randomUUID(), "preferences", "result", null));
    assertIncident(instance, "AGENTCORE_MEMORY_FAILED");
  }

  @Test
  void invalidRuntimeArnCreatesIncident() {
    var arn = required("AGENTCORE_AGENT_RUNTIME_ARN");
    var instance = runUntilIncident("runtime", runtimeTemplate(arn + "-missing", null, "result"));
    assertIncident(instance, "AGENTCORE_RUNTIME_FAILED");
  }

  @Test
  void invalidCodeInterpreterIdCreatesIncident() {
    var instance =
        runUntilIncident(
            "code",
            codeTemplate("python", "print('unreachable')", "nonexistent-" + UUID.randomUUID()));
    assertIncident(instance, "CODE_INTERPRETER_FAILED");
  }

  private void assertExpectedRecord(ZeebeTest instance, String expectedId, String expectedContent) {
    CamundaAssert.assertThat(instance.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "result",
            Map.class,
            result -> {
              assertThat(((Number) result.get("resultCount")).intValue()).isPositive();
              List<Map<?, ?>> records = new java.util.ArrayList<>();
              for (var record : (List<?>) result.get("records")) {
                records.add((Map<?, ?>) record);
              }
              assertRecord(records, expectedId, expectedContent);
            });
  }

  private void assertRecord(List<Map<?, ?>> records, String expectedId, String expectedContent) {
    assertThat(records)
        .anySatisfy(
            entry -> {
              assertThat(entry.get("memoryRecordId")).isEqualTo(expectedId);
              assertThat(entry.get("content")).isEqualTo(expectedContent);
              assertThat((List<?>) entry.get("namespaces"))
                  .anyMatch(namespace -> required("AGENTCORE_LTM_NAMESPACE").equals(namespace));
            });
  }

  private ElementTemplate runtimeTemplate(String arn, String sessionId, String resultVariable) {
    var template =
        credentials(
                RUNTIME_TEMPLATE,
                "AGENTCORE_AWS_ACCESS_KEY",
                "AGENTCORE_AWS_SECRET_KEY",
                "AGENTCORE_AWS_REGION")
            .property("input.agentRuntimeArn", arn)
            .property(
                "input.payload",
                sessionId == null
                    ? "={inputText: \"What is the capital of France?\"}"
                    : "={inputText: \"What country is that city in?\"}")
            .property("resultVariable", resultVariable)
            .property("retryCount", "0");
    if (sessionId != null) {
      template.property("input.sessionId", sessionId);
    }
    return template;
  }

  private ElementTemplate codeTemplate(String language, String code, String identifier) {
    var template =
        credentials(
                CODE_TEMPLATE,
                "BEDROCK_CI_AWS_ACCESS_KEY",
                "BEDROCK_CI_AWS_SECRET_KEY",
                "BEDROCK_CI_AWS_REGION")
            .property("input.language", language)
            .property("input.code", code)
            .property("resultVariable", "result")
            .property("retryCount", "0");
    if (identifier != null) {
      template.property("input.codeInterpreterIdentifier", identifier);
    }
    return template;
  }

  private ElementTemplate memoryTemplate(
      String operation, String memoryId, String query, String resultVariable, String nextToken) {
    required("AGENTCORE_LTM_NAMESPACE");
    var template =
        credentials(
                MEMORY_TEMPLATE,
                "AGENTCORE_LTM_AWS_ACCESS_KEY",
                "AGENTCORE_LTM_AWS_SECRET_KEY",
                "AGENTCORE_LTM_AWS_REGION")
            .property("memoryId", memoryId)
            .property("namespace", "{{secrets.AGENTCORE_LTM_NAMESPACE}}")
            .property("operation.operationDiscriminator", operation)
            .property("resultVariable", resultVariable)
            .property("retryCount", "0");
    if (operation.equals("retrieve")) {
      template.property("operation.query", query).property("operation.retrieve.maxResults", "20");
    } else {
      template.property("operation.list.maxResults", "1");
      if (nextToken != null) {
        template.property("operation.list.nextToken", nextToken);
      }
    }
    return template;
  }

  private ElementTemplate credentials(
      String template, String accessKey, String secretKey, String region) {
    required(accessKey);
    required(secretKey);
    required(region);
    return ElementTemplate.from(template)
        .property("authentication.type", "credentials")
        .property("authentication.accessKey", "{{secrets." + accessKey + "}}")
        .property("authentication.secretKey", "{{secrets." + secretKey + "}}")
        .property("configuration.region", "{{secrets." + region + "}}");
  }

  private ZeebeTest run(String task, ElementTemplate template) {
    return ZeebeTest.with(camundaClient)
        .deploy(apply(model(task), task, template))
        .createInstance()
        .waitForProcessCompletion(Duration.ofMinutes(3));
  }

  private ZeebeTest runUntilIncident(String task, ElementTemplate template) {
    return ZeebeTest.with(camundaClient)
        .deploy(apply(model(task), task, template))
        .createInstance()
        .waitForActiveIncidents(Duration.ofMinutes(1));
  }

  private BpmnModelInstance model(String task) {
    return Bpmn.createProcess("real-agentcore-" + UUID.randomUUID())
        .executable()
        .startEvent()
        .serviceTask(task)
        .endEvent()
        .done();
  }

  private BpmnModelInstance apply(BpmnModelInstance model, String task, ElementTemplate template) {
    var id = UUID.randomUUID().toString();
    return new BpmnFile(model)
        .writeToFile(new File(tempDir, id + "-input.bpmn"))
        .apply(
            template.writeTo(new File(tempDir, id + ".json")),
            task,
            new File(tempDir, id + "-output.bpmn"));
  }

  private void assertIncident(ZeebeTest instance, String errorCode) {
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
        .satisfies(incident -> assertThat(incident.getErrorMessage()).contains(errorCode));
  }

  private static String required(String name) {
    var value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Real AgentCore CPT requires nonblank " + name);
    }
    return value;
  }
}
